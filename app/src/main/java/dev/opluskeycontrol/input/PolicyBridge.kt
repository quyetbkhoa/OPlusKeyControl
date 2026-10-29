package dev.opluskeycontrol.input

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import dev.opluskeycontrol.BuildConfig
import dev.opluskeycontrol.KeyControlApp
import dev.opluskeycontrol.actions.RecorderService
import dev.opluskeycontrol.capabilities.Capabilities
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.root.VectorAuthorization
import dev.opluskeycontrol.storage.RuleCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

/** Private Binder channel: UID checks on every call, death revokes authority. */
class PolicyBridge(private val app: KeyControlApp) {
    companion object {
        val connected = MutableStateFlow(false)
        val features = MutableStateFlow(0)
    }
    private val authorization = VectorAuthorization(app.scope, app.applicationInfo.sourceDir)
    val authorizationState = authorization.state.asStateFlow()
    private val generation = AtomicLong()
    @Volatile private var available = false
    private var peer: IBinder? = null
    private var death: IBinder.DeathRecipient? = null
    private var configTimeout: Job? = null
    private val configAcknowledgement = ConfigAcknowledgement()
    private val lock = Any()
    private data class ActionRequest(val callback: IBinder, val revision: Long, val id: String, val received: Long)
    private val actionQueue = SerialRequestQueue<ActionRequest>(app.scope, onFailure = {
        android.util.Log.e("OPlusKeyControl", "ACTION_QUEUE_FAILED", it); lastRequest = "queue-processing-failed"
    }) { request -> processAction(request) }
    private var started = false
    private var runtimeTrace = "no-runtime-trace"
    private val publication = PolicyPublication()
    private val requestHistory = java.util.ArrayDeque<String>()
    @Volatile private var lastRequest = "none"
        set(value) {
            field = value
            val row = "${android.os.SystemClock.elapsedRealtime()}:$value"
            synchronized(requestHistory) {
                requestHistory.addLast(row)
                while (requestHistory.size > 64) requestHistory.removeFirst()
            }
            android.util.Log.i("OPlusKeyControl", "ACTION_TRACE $row")
        }
    @Volatile private var lastRequestAt = 0L
    private fun validate(request: ActionRequest): Rule? = synchronized(lock) {
        if (requestExpired(request.received, android.os.SystemClock.elapsedRealtime())) {
            lastRequest = "request-expired"; return@synchronized null
        }
        if (peer !== request.callback || request.revision != generation.get()) {
            lastRequest = "stale-connection-or-revision"; return@synchronized null
        }
        val c = effectiveConfiguration()
        if (!authorization.allowed.value || app.store.emergency.value || !c.enabled || c.tier != Tier.ROOT) {
            lastRequest = "not-authorized-or-disabled"; return@synchronized null
        }
        val rule = c.rules.singleOrNull { it.id == request.id } ?: run {
            lastRequest = "missing-rule"; return@synchronized null
        }
        if (RuleResolver.resolve(c.rules, rule.trigger, ZonedDateTime.now()) != Resolution.Match(rule)) {
            lastRequest = "rule-no-longer-selected"; return@synchronized null
        }
        rule
    }
    private suspend fun processAction(request: ActionRequest) {
        val rule = validate(request) ?: return
        if (rule.action.kind == ActionKind.RECORD_TOGGLE) { lastRequest = "recording-request-forwarded"; return }
        var executed = false
        app.actions.execute(rule.action, app.visible.value, rootAuthorized = true, confirmation = false, preflight = {
            val valid = validate(request) == rule
            if (valid) { executed = true; lastRequest = "executing-${rule.action.kind}" }
            valid
        })
        if (executed) lastRequest = if (app.actions.message.value == null) "completed-${rule.action.kind}" else "failed-${rule.action.kind}"
    }
    fun diagnosticSummary(): String = synchronized(lock) {
        "connected=${connected.value} authorized=${authorization.allowed.value} authorization=${authorization.state.value} features=${features.value}" +
            " generation=${generation.get()} enabled=${app.store.config.value.enabled} emergency=${app.store.emergency.value}" +
            " torchReady=${app.actions.torchReady.value} busy=${app.actions.busy.value}" +
            " lastRequest=$lastRequest lastRequestUptime=$lastRequestAt actionMessage=${app.actions.message.value}" +
            "\nrequestHistory=${synchronized(requestHistory) { requestHistory.joinToString("\n") }}" +
            "\nruntimeTrace=$runtimeTrace"
    }

    val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(PolicyBinder.DESCRIPTOR); return true }
            if (!BuildConfig.POLICY_RUNTIME || getCallingUid() != Process.SYSTEM_UID) return false
            return try {
                data.enforceInterface(PolicyBinder.DESCRIPTOR)
                when (code) {
                    PolicyBinder.TRACE -> {
                        val callback = data.readStrongBinder()
                        val trace = requireNotNull(data.readString())
                        require(trace.length <= 4096 && data.dataAvail() == 0)
                        synchronized(lock) { if (peer === callback) runtimeTrace = trace }
                    }
                    PolicyBinder.CONFIG_APPLIED -> {
                        val callback = requireNotNull(data.readStrongBinder())
                        val revision = data.readLong()
                        require(data.dataAvail() == 0)
                        synchronized(lock) {
                            if (peer === callback && revision == generation.get() && authorization.allowed.value && !app.store.emergency.value && configAcknowledgement.applied(revision)) {
                                configTimeout?.cancel(); configTimeout = null
                                connected.value = true
                            }
                        }
                    }
                    PolicyBinder.HELLO -> {
                        val callback = requireNotNull(data.readStrongBinder())
                        val supported = data.readInt()
                        require(data.dataAvail() == 0 && supported and PolicyBinder.ALL_FEATURES == supported)
                        app.scope.launch(Dispatchers.IO) {
                            synchronized(lock) {
                                if (!available || !authorization.allowed.value || app.store.emergency.value) {
                                    runCatching { PolicyBinder.send(callback, PolicyBinder.DISCONNECT) {
                                        it.writeInt(if (app.store.config.value.tier == Tier.ROOT && !app.store.emergency.value) 1 else 0)
                                    } }
                                    return@synchronized
                                }
                                closePeer()
                                val recipient = IBinder.DeathRecipient { synchronized(lock) { if (peer === callback) closePeer(false) } }
                                try {
                                    callback.linkToDeath(recipient, 0)
                                    peer = callback; death = recipient; features.value = supported; connected.value = false
                                    publish()
                                } catch (_: Exception) { closePeer(false) }
                            }
                        }
                    }
                    PolicyBinder.FEATURES -> {
                        val callback = data.readStrongBinder(); val supported = data.readInt()
                        require(data.dataAvail() == 0 && supported and PolicyBinder.ALL_FEATURES == supported)
                        app.scope.launch { synchronized(lock) {
                            if (peer === callback && authorization.allowed.value && !app.store.emergency.value) features.value = supported
                        } }
                    }
                    PolicyBinder.ACTION, PolicyBinder.FEEDBACK -> {
                        val callback = requireNotNull(data.readStrongBinder())
                        val revision = data.readLong(); val id = requireNotNull(data.readString())
                        require(id.length <= 128 && data.dataAvail() == 0)
                        val request = ActionRequest(callback, revision, id, android.os.SystemClock.elapsedRealtime())
                        lastRequestAt = android.os.SystemClock.uptimeMillis()
                        if (code == PolicyBinder.FEEDBACK) {
                            app.scope.launch { if (validate(request) != null) { lastRequest = "feedback"; app.feedback.confirm() } }
                        } else if (validate(request) != null) {
                            lastRequest = if (actionQueue.offer(request)) "queued" else "queue-overflow"
                        }
                    }
                    PolicyBinder.DISCONNECT -> {
                        val callback = data.readStrongBinder(); require(data.dataAvail() == 0)
                        app.scope.launch(Dispatchers.IO) { synchronized(lock) { if (peer === callback) closePeer(false) } }
                    }
                    else -> return false
                }
                true
            } catch (_: Exception) { false }
        }
    }

    fun start() {
        if (!BuildConfig.POLICY_RUNTIME || started) return
        started = true
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh() }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        })
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh() }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
        app.scope.launch {
            combine(app.store.config, app.store.emergency, authorization.allowed) { c, emergency, allowed ->
                Triple(c, emergency, allowed)
            }.combine(RecorderService.recording) { state, _ -> state }
                .combine(RecorderService.origin) { state, _ -> state }
                .combine(RecorderService.error) { state, _ -> state }
                .combine(app.actions.torchReady) { state, _ -> state }
                .collectLatest { (config, emergency, allowed) ->
                if (config.tier != Tier.ROOT) { withContext(Dispatchers.IO) { authorization.stop(); close() }; return@collectLatest }
                authorization.start()
                if (emergency) { withContext(Dispatchers.IO) { close() }; return@collectLatest }
                if (!allowed) { withContext(Dispatchers.IO) { close() }; return@collectLatest }
                withContext(Dispatchers.IO) {
                    if (!available) open()
                    publish()
                }
            }
        }
    }
    private fun open() {
        synchronized(lock) {
            available = true
            app.sendBroadcast(Intent(PolicyWire.READY).setPackage("android"), PolicyWire.PERMISSION)
        }
    }
    fun refresh() { if (BuildConfig.POLICY_RUNTIME) app.scope.launch(Dispatchers.IO) {
        if (app.store.config.value.tier == Tier.ROOT && !app.store.emergency.value) authorization.start()
        synchronized(lock) {
            if (available && peer == null) app.sendBroadcast(Intent(PolicyWire.READY).setPackage("android"), PolicyWire.PERMISSION)
            publish()
        }
    } }
    fun retryAuthorization() {
        if (!BuildConfig.POLICY_RUNTIME || app.store.config.value.tier != Tier.ROOT) return
        app.scope.launch(Dispatchers.IO) { authorization.stop(); close(); authorization.start() }
    }
    private fun publish() {
        synchronized(lock) {
            val target = peer ?: return
            try {
                val c = effectiveConfiguration()
                // Target-specific proof must not block unrelated rules of the same action kind.
                val readyRuleIds = c.rules.filter { rule -> rule.enabled && when (rule.action.kind) {
                    ActionKind.ACTIVITY -> dev.opluskeycontrol.actions.ActionProofs.verified(app, rule.action)
                    ActionKind.APP -> runCatching {
                        app.packageManager.getLaunchIntentForPackage(requireNotNull(rule.action.packageName)) != null
                    }.getOrDefault(false)
                    else -> true
                } }.map { it.id }
                val ready = ActionKind.entries.filter { kind ->
                    when (kind) {
                        ActionKind.TORCH_TOGGLE, ActionKind.TORCH_ON, ActionKind.TORCH_OFF -> app.actions.torchReady.value && Capabilities.permission(app, android.Manifest.permission.CAMERA)
                        ActionKind.STOCK_CAMERA_CAPTURE -> true
                        ActionKind.ACTIVITY -> true // Individual target readiness is carried by rule ID below.
                        ActionKind.RECORD_TOGGLE -> RecorderService.recording.value || (!RecorderService.error.value && Capabilities.permission(app, android.Manifest.permission.RECORD_AUDIO) &&
                            Capabilities.permission(app, android.Manifest.permission.POST_NOTIFICATIONS))
                        ActionKind.APP -> true
                    }
                }
                val snapshot = PublishedPolicy(
                    RuleCodec.encode(c.copy(enabled = c.enabled && !app.store.emergency.value && authorization.allowed.value)),
                    ready.fold(0) { mask, kind -> mask or (1 shl kind.ordinal) },
                    RecorderService.recording.value, readyRuleIds)
                if (!publication.changed(snapshot)) return
                val bytes = PolicyWire.binderText(snapshot.text)
                val version = generation.incrementAndGet()
                connected.value = false
                configAcknowledgement.sent(version)
                PolicyBinder.send(target, PolicyBinder.CONFIG) {
                    it.writeLong(version); it.writeByteArray(bytes)
                    it.writeInt(snapshot.readyMask)
                    it.writeInt(if (snapshot.recording) 1 else 0)
                    it.writeInt(readyRuleIds.size)
                    readyRuleIds.forEach(it::writeString)
                }
                publication.sent(snapshot)
                configTimeout?.cancel()
                configTimeout = app.scope.launch(Dispatchers.IO) {
                    delay(5000)
                    synchronized(lock) {
                        if (peer === target && generation.get() == version && configAcknowledgement.awaiting(version)) closePeer(retry = true)
                    }
                }
            } catch (_: Exception) { closePeer() }
        }
    }
    private fun effectiveConfiguration(): dev.opluskeycontrol.storage.Configuration {
        val c = app.store.config.value
        val trigger = RecorderService.origin.value
        return if (RecorderService.recording.value && trigger != null) c.copy(rules = c.rules.filterNot { it.trigger.sameAs(trigger) } +
            Rule("recording-stop", trigger, Action(ActionKind.RECORD_TOGGLE), priority = 100)) else c
    }
    fun disconnect() { synchronized(lock) { closePeer() } }
    private fun closePeer(notify: Boolean = true, retry: Boolean = false) {
        configTimeout?.cancel(); configTimeout = null
        configAcknowledgement.clear()
        publication.reset()
        val old = peer; val recipient = death
        peer = null; death = null; generation.incrementAndGet(); connected.value = false; features.value = 0
        if (old != null) {
            if (recipient != null) runCatching { old.unlinkToDeath(recipient, 0) }
            if (notify) runCatching { PolicyBinder.send(old, PolicyBinder.DISCONNECT) { it.writeInt(if (retry) 1 else 0) } }
        }
    }
    private fun close() { synchronized(lock) { available = false; closePeer() } }
}
