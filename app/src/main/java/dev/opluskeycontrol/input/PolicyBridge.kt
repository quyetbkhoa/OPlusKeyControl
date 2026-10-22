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
    private val lock = Any()
    private var started = false

    val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(PolicyBinder.DESCRIPTOR); return true }
            if (!BuildConfig.POLICY_RUNTIME || getCallingUid() != Process.SYSTEM_UID) return false
            return try {
                data.enforceInterface(PolicyBinder.DESCRIPTOR)
                when (code) {
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
                                    peer = callback; death = recipient; features.value = supported; connected.value = true
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
                    PolicyBinder.ACTION -> {
                        val callback = data.readStrongBinder()
                        val revision = data.readLong(); val id = requireNotNull(data.readString())
                        require(id.length <= 128 && data.dataAvail() == 0)
                        app.scope.launch {
                            synchronized(lock) {
                                if (peer !== callback || revision != generation.get()) return@synchronized
                                val c = effectiveConfiguration()
                                if (!authorization.allowed.value || app.store.emergency.value || !c.enabled || c.tier != Tier.ROOT) return@synchronized
                                val rule = c.rules.singleOrNull { it.id == id } ?: return@synchronized
                                if (RuleResolver.resolve(c.rules, rule.trigger, ZonedDateTime.now()) != Resolution.Match(rule)) return@synchronized
                                if (rule.action.kind != ActionKind.RECORD_TOGGLE) app.scope.launch {
                                    app.actions.execute(rule.action, app.visible.value, rootAuthorized = true)
                                }
                            }
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
        app.scope.launch {
            combine(app.store.config, app.store.emergency, authorization.allowed) { c, emergency, allowed ->
                Triple(c, emergency, allowed)
            }.combine(RecorderService.recording) { state, _ -> state }
                .combine(RecorderService.origin) { state, _ -> state }
                .combine(RecorderService.error) { state, _ -> state }
                .combine(app.actions.torchReady) { state, _ -> state }
                .combine(app.actions.busy) { state, _ -> state }
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
                val ready = ActionKind.entries.filter { kind ->
                    if (app.actions.busy.value) return@filter false
                    when (kind) {
                        ActionKind.TORCH_TOGGLE, ActionKind.TORCH_ON, ActionKind.TORCH_OFF -> app.actions.torchReady.value && Capabilities.permission(app, android.Manifest.permission.CAMERA)
                        ActionKind.STOCK_CAMERA_CAPTURE -> true
                        ActionKind.ACTIVITY -> c.rules.filter { it.enabled && it.action.kind == kind }.all { dev.opluskeycontrol.actions.ActionProofs.verified(app, it.action) }
                        ActionKind.RECORD_TOGGLE -> RecorderService.recording.value || (!RecorderService.error.value && Capabilities.permission(app, android.Manifest.permission.RECORD_AUDIO) &&
                            Capabilities.permission(app, android.Manifest.permission.POST_NOTIFICATIONS))
                        ActionKind.APP -> c.rules.filter { it.enabled && it.action.kind == kind }.all {
                            runCatching { app.packageManager.getLaunchIntentForPackage(requireNotNull(it.action.packageName)) != null }.getOrDefault(false)
                        }
                    }
                }
                val bytes = PolicyWire.binderText(RuleCodec.encode(c.copy(enabled = c.enabled && !app.store.emergency.value && authorization.allowed.value)))
                PolicyBinder.send(target, PolicyBinder.CONFIG) {
                    it.writeLong(generation.incrementAndGet()); it.writeByteArray(bytes)
                    it.writeInt(ready.fold(0) { mask, kind -> mask or (1 shl kind.ordinal) })
                    it.writeInt(if (RecorderService.recording.value) 1 else 0)
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
    private fun closePeer(notify: Boolean = true) {
        val old = peer; val recipient = death
        peer = null; death = null; generation.incrementAndGet(); connected.value = false; features.value = 0
        if (old != null) {
            if (recipient != null) runCatching { old.unlinkToDeath(recipient, 0) }
            if (notify) runCatching { PolicyBinder.send(old, PolicyBinder.DISCONNECT) { it.writeInt(0) } }
        }
    }
    private fun close() { synchronized(lock) { available = false; closePeer() } }
}
