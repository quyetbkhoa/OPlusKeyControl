package dev.opluskeycontrol.input

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.LocalServerSocket
import android.net.LocalSocket
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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

/** Lives only in the app process. Both ends check SO_PEERCRED; app death closes
 * the stream, which revokes the system-server snapshot. No exported execution API.
 */
class PolicyBridge(private val app: KeyControlApp) {
    companion object { val connected = MutableStateFlow(false) }
    private val authorization = VectorAuthorization(app.scope, app.applicationInfo.sourceDir)
    private val generation = AtomicLong()
    private var server: LocalServerSocket? = null
    private var peer: LocalSocket? = null
    private var output: DataOutputStream? = null
    private var acceptJob: Job? = null
    private val lock = Any()
    private var started = false
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
                if (config.tier != Tier.ROOT || emergency) { withContext(Dispatchers.IO) { authorization.stop(); close() }; return@collectLatest }
                authorization.start()
                if (!allowed) { withContext(Dispatchers.IO) { close() }; return@collectLatest }
                withContext(Dispatchers.IO) {
                    if (server == null) open()
                    publish()
                }
            }
        }
    }
    private fun open() {
        try {
            server = LocalServerSocket(PolicyWire.NAME)
            acceptJob = app.scope.launch(Dispatchers.IO) {
                val listener = server ?: return@launch
                while (isActive) {
                    val socket = try { listener.accept() } catch (_: Exception) { break }
                    if (socket.peerCredentials.uid != Process.SYSTEM_UID) { socket.close(); continue }
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        android.system.Os.setsockoptTimeval(socket.fileDescriptor, android.system.OsConstants.SOL_SOCKET,
                            android.system.OsConstants.SO_SNDTIMEO, android.system.StructTimeval.fromMillis(1500))
                    } else { socket.close(); continue }
                    synchronized(lock) { peer?.close(); peer = socket; output = DataOutputStream(socket.outputStream) }
                    try {
                        val input = DataInputStream(socket.inputStream)
                        require(input.readInt() == PolicyWire.MAGIC)
                        connected.value = true
                        publish()
                        while (isActive) {
                            require(input.readInt() == PolicyWire.ACTION)
                            val revision = input.readLong()
                            val id = input.readUTF()
                            if (revision != generation.get()) continue
                            val c = effectiveConfiguration()
                            if (!authorization.allowed.value || app.store.emergency.value || !c.enabled || c.tier != Tier.ROOT) continue
                            val rule = c.rules.singleOrNull { it.id == id } ?: continue
                            if (RuleResolver.resolve(c.rules, rule.trigger, ZonedDateTime.now()) != Resolution.Match(rule)) continue
                            app.scope.launch {
                                // Recorder commands originate from system_server to satisfy the
                                // platform's system-component FGS exception, not from this socket callback.
                                if (rule.action.kind != ActionKind.RECORD_TOGGLE) app.actions.execute(rule.action, app.visible.value, rootAuthorized = true)
                            }
                        }
                    } catch (_: Exception) { }
                    finally { synchronized(lock) { socket.close(); if (peer === socket) { peer = null; output = null; connected.value = false } } }
                }
            }
            app.sendBroadcast(Intent(PolicyWire.READY).setPackage("android"), PolicyWire.PERMISSION)
        } catch (_: Exception) { close() }
    }
    fun refresh() { if (BuildConfig.POLICY_RUNTIME) app.scope.launch(Dispatchers.IO) { publish() } }
    private fun publish() {
        synchronized(lock) {
            val out = output ?: return
            try {
                val c = effectiveConfiguration()
                val ready = ActionKind.entries.filter { kind ->
                    if (app.actions.busy.value) return@filter false
                    when (kind) {
                        ActionKind.TORCH_TOGGLE, ActionKind.TORCH_ON, ActionKind.TORCH_OFF -> app.actions.torchReady.value && Capabilities.permission(app, android.Manifest.permission.CAMERA)
                        ActionKind.STOCK_CAMERA_CAPTURE -> dev.opluskeycontrol.actions.ActionProofs.verified(app, Action(kind))
                        ActionKind.ACTIVITY -> c.rules.filter { it.enabled && it.action.kind == kind }.all { dev.opluskeycontrol.actions.ActionProofs.verified(app, it.action) }
                        ActionKind.RECORD_TOGGLE -> RecorderService.recording.value || (!RecorderService.error.value && Capabilities.permission(app, android.Manifest.permission.RECORD_AUDIO) &&
                            Capabilities.permission(app, android.Manifest.permission.POST_NOTIFICATIONS))
                        ActionKind.APP -> c.rules.filter { it.enabled && it.action.kind == kind }.all {
                            runCatching { app.packageManager.getLaunchIntentForPackage(requireNotNull(it.action.packageName)) != null }.getOrDefault(false)
                        }
                    }
                }
                out.writeInt(PolicyWire.CONFIG); out.writeLong(generation.incrementAndGet())
                PolicyWire.writeText(out, RuleCodec.encode(c.copy(enabled = c.enabled && !app.store.emergency.value && authorization.allowed.value)))
                out.writeInt(ready.fold(0) { mask, kind -> mask or (1 shl kind.ordinal) }); out.flush()
                out.writeBoolean(RecorderService.recording.value); out.flush()
            } catch (_: Exception) { peer?.close(); output = null; connected.value = false }
        }
    }
    private fun effectiveConfiguration(): dev.opluskeycontrol.storage.Configuration {
        val c = app.store.config.value
        val trigger = RecorderService.origin.value
        return if (RecorderService.recording.value && trigger != null) c.copy(rules = c.rules.filterNot { it.trigger.sameAs(trigger) } +
            Rule("recording-stop", trigger, Action(ActionKind.RECORD_TOGGLE), priority = 100)) else c
    }
    private fun close() {
        connected.value = false
        synchronized(lock) { runCatching { peer?.close() }; peer = null; output = null }
        runCatching { server?.close() }; server = null; acceptJob?.cancel(); acceptJob = null
    }
}
