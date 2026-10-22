package dev.opluskeycontrol.vector

import android.content.*
import android.os.*
import android.util.Log
import android.view.KeyEvent
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.input.PolicyWire
import dev.opluskeycontrol.input.PolicyBinder
import dev.opluskeycontrol.storage.RuleCodec
import dev.opluskeycontrol.actions.RecorderService
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.time.ZonedDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit experimental build only. Raw Power is observed but never consumed;
 * Volume ownership is limited to an opted-in Plus-first chord. No settings writes,
 * event injection, disk access or IPC on the input interception path.
 */
@androidx.annotation.RequiresApi(36)
class RootPolicyBackend(private val module: XposedModule) {
    private val session = PlusPolicySession(plusVolumeChords = dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS)
    private val power = PowerDoubleSession()
    @Volatile private var features = PolicyBinder.PLUS or if (dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) PolicyBinder.PLUS_VOLUME else 0
    private var powerTimeout = 300L
    private val powerHandles = mutableListOf<XposedInterface.HookHandle>()
    private val lock = Any()
    private val queue = ArrayBlockingQueue<PolicyDecision.Replace>(16)
    private val connecting = AtomicBoolean()
    private val thread by lazy { HandlerThread("OPlusKeyControl-policy").apply { start() } }
    private val worker by lazy { Handler(thread.looper) }
    private var context: Context? = null
    @Volatile private var peer: IBinder? = null
    @Volatile private var callback: IBinder? = null
    @Volatile private var connection: ServiceConnection? = null
    private var peerDeath: IBinder.DeathRecipient? = null
    @Volatile private var revision = 0L
    @Volatile private var recording = false
    @Volatile private var ownerUserActive = false
    private val logBudget = java.util.concurrent.atomic.AtomicInteger(24)
    private var installed = false
    @Volatile private var disposed = false
    private var handle: XposedInterface.HookHandle? = null
    private var writer: Thread? = null
    private val receivers = mutableListOf<BroadcastReceiver>()
    private var initializationAttempts = 0
    private var reconnectAttempts = 0
    private val diagnosticBudget = java.util.concurrent.atomic.AtomicInteger(16)
    private fun diagnostic(stage: String, error: Throwable? = null) {
        if (diagnosticBudget.getAndDecrement() > 0) Log.w("OPlusKeyControl", stage, error)
    }
    private val reconnect = Runnable { connect() }
    private fun requestConnect() {
        // An app-ready event may arrive while a failed boot connection unwinds.
        // Queue a bounded retry window instead of losing that event to connecting.
        worker.post {
            reconnectAttempts = 0
            worker.removeCallbacks(reconnect)
            worker.post(reconnect)
        }
    }
    private val initializer = object : Runnable {
        override fun run() {
            if (disposed) return
            initializeContext()
            if (context != null) connect()
            else if (++initializationAttempts < 60) worker.postDelayed(this, 1500)
        }
    }
    private val bindTimeout = Runnable {
        if (peer == null && connection != null) {
            diagnostic("RUNTIME_BIND_TIMEOUT")
            drop()
            if (++reconnectAttempts < 5) worker.postDelayed(reconnect, 1000)
        }
    }
    private val deadline = Runnable {
        synchronized(lock) {
            dispatch(session.advance(SystemClock.uptimeMillis(), ZonedDateTime.now()))
            schedule()
        }
    }
    @Synchronized fun install(loader: ClassLoader) {
        if (disposed || installed) return
        if (Build.VERSION.SDK_INT != 36 || Build.MODEL != "CPH2745" || Build.DISPLAY != PolicyInputProbe.BUILD) {
            diagnostic("RUNTIME_PROFILE_BLOCKED sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} build=${Build.DISPLAY}")
            return
        }
        try {
            val ims = loader.loadClass("com.android.server.input.InputManagerService")
            val method = ims.getDeclaredMethod("interceptKeyBeforeQueueing", KeyEvent::class.java, Int::class.javaPrimitiveType)
            require(method.returnType == Int::class.javaPrimitiveType)
            handle = module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                var consume = false
                try {
                    val event = chain.getArg(0) as KeyEvent
                    if (event.keyCode == 780 && event.scanCode == 735) {
                        if (peer == null) requestConnect()
                        synchronized(lock) {
                            val driverMatches = event.device?.name == "gpio-keys"
                            if (!driverMatches || !ownerUserActive) session.disconnect()
                            val result = session.accept(PolicyEvent(event.deviceId, event.downTime, event.eventTime,
                                when { event.isCanceled -> Edge.CANCEL; event.action == KeyEvent.ACTION_DOWN -> Edge.DOWN; else -> Edge.UP },
                                event.repeatCount), SystemClock.uptimeMillis(), ZonedDateTime.now())
                            consume = result.consume
                            dispatch(result.actions); schedule()
                            if (logBudget.getAndDecrement() > 0) Log.i("OPlusKeyControl", "RUNTIME_EVENT edge=${event.action} owned=$consume driverMatches=$driverMatches")
                        }
                    } else if (event.keyCode == KeyEvent.KEYCODE_POWER) {
                        synchronized(lock) {
                            session.cancelGesture(); worker.removeCallbacks(deadline)
                            if (dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS && ownerUserActive && event.device?.name == "pmic_pwrkey" && event.scanCode == 116)
                                power.observe(policyEvent(event)) else power.cancel()
                        }
                    } else if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        synchronized(lock) {
                            power.cancel()
                            val partner = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) Button.VOLUME_UP else Button.VOLUME_DOWN
                            val validDriver = if (partner == Button.VOLUME_UP) event.device?.name == "pmic_resin" && event.scanCode == 115
                                else event.device?.name == "gpio-keys" && event.scanCode == 114
                            if (!validDriver || !ownerUserActive) session.cancelGesture()
                            if (dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) {
                                val result = session.acceptPartner(partner, policyEvent(event), SystemClock.uptimeMillis(), ZonedDateTime.now())
                                consume = result.consume; dispatch(result.actions); schedule()
                            } else { session.cancelGesture(); worker.removeCallbacks(deadline) }
                        }
                    }
                } catch (_: Throwable) { synchronized(lock) { session.disconnect(); power.disconnect() } }
                if (consume) 0 else chain.proceed() // original exactly once on pass-through
            }
            installed = true
            if (dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) installPower(loader)
            worker.post(initializer) // finite bootstrap retries; no standby polling
            writer = Thread({ writeActions() }, "OPlusKeyControl-handoff").apply { isDaemon = true; start() }
            diagnostic("RUNTIME_ATTACHED features=$features")
        } catch (e: Throwable) { diagnostic("RUNTIME_BLOCKED", e) }
    }
    private fun policyEvent(event: KeyEvent) = PolicyEvent(event.deviceId, event.downTime, event.eventTime,
        when { event.isCanceled -> Edge.CANCEL; event.action == KeyEvent.ACTION_DOWN -> Edge.DOWN; else -> Edge.UP }, event.repeatCount)

    /** Hooks completed OEM gestures, never raw Power suppression or input replay. */
    private fun installPower(loader: ClassLoader) {
        try {
            val rule = loader.loadClass("com.android.server.policy.PhoneWindowManager\$PowerKeyRule")
            val owner = rule.getDeclaredField("this\$0").apply { isAccessible = true }
            val early = loader.loadClass("com.android.server.policy.PhoneWindowManager")
                .getDeclaredField("mShouldEarlyShortPressOnPower").apply { isAccessible = true }
            val detector = loader.loadClass("com.android.server.policy.SingleKeyGestureDetector")
            powerTimeout = detector.getDeclaredField("MULTI_PRESS_TIMEOUT").apply { isAccessible = true }.getLong(null)
            require(powerTimeout in 100..1000)
            val max = rule.getDeclaredMethod("getMaxMultiPressCount"); require(max.returnType == Int::class.javaPrimitiveType)
            val multi = rule.getDeclaredMethod("onMultiPress", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            require(multi.returnType == Void.TYPE)
            powerHandles += module.hook(max).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                val original = chain.proceed() as Int
                var result = original
                try {
                    val compatible = !early.getBoolean(owner.get(chain.thisObject))
                    setPowerFeature(compatible)
                    if (compatible && peer != null && ownerUserActive && !disposed) synchronized(lock) {
                        result = power.maxPressCount(original, SystemClock.uptimeMillis(), ZonedDateTime.now())
                    }
                } catch (_: Throwable) { setPowerFeature(false); synchronized(lock) { power.disconnect() } }
                result
            }
            powerHandles += module.hook(multi).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                var consumed = false
                try {
                    if (peer != null && ownerUserActive && !disposed && !early.getBoolean(owner.get(chain.thisObject))) synchronized(lock) {
                        consumed = power.accept(chain.getArg(1) as Int, chain.getArg(0) as Long, chain.getArg(2) as Int,
                            SystemClock.uptimeMillis(), ZonedDateTime.now()) { queue.offer(it) }
                    }
                } catch (_: Throwable) { synchronized(lock) { power.disconnect() } }
                if (consumed) { diagnostic("RUNTIME_POWER_DOUBLE_DISPATCH"); null } else chain.proceed()
            }
            setPowerFeature(true)
            diagnostic("RUNTIME_POWER_READY timeout=$powerTimeout")
        } catch (failure: Throwable) {
            for (handle in powerHandles) runCatching { handle.unhook() }
            powerHandles.clear(); setPowerFeature(false)
            diagnostic("RUNTIME_POWER_UNSUPPORTED", failure)
        }
    }
    private fun setPowerFeature(enabled: Boolean) {
        val changed = synchronized(lock) {
            val next = if (enabled) features or PolicyBinder.POWER_DOUBLE else features and PolicyBinder.POWER_DOUBLE.inv()
            if (features == next) false else { features = next; true }
        }
        if (changed) worker.post {
            val target = peer; val endpoint = callback
            if (target != null && endpoint != null) runCatching { PolicyBinder.send(target, PolicyBinder.FEATURES) {
                it.writeStrongBinder(endpoint); it.writeInt(features)
            } }
        }
    }
    @Synchronized private fun initializeContext() {
        if (context != null) return
        try {
            val at = Class.forName("android.app.ActivityThread")
            val instance = at.getDeclaredMethod("currentActivityThread").invoke(null) ?: return
            context = at.getDeclaredMethod("getSystemContext").invoke(instance) as Context
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { requestConnect() }
            }
            context!!.registerReceiver(receiver, IntentFilter(PolicyWire.READY), PolicyWire.PERMISSION, worker, Context.RECEIVER_EXPORTED)
            receivers += receiver
            val invalidation = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.data?.schemeSpecificPart != "dev.opluskeycontrol") return
                    if (intent.action == Intent.ACTION_PACKAGE_REPLACED ||
                        (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))) dispose()
                    else drop()
                }
            }
            context!!.registerReceiver(invalidation, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_RESTARTED); addDataScheme("package")
            }, null, worker, Context.RECEIVER_EXPORTED)
            receivers += invalidation
            val users = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    ownerUserActive = intent.getIntExtra("android.intent.extra.user_handle", -1) == 0
                    drop()
                    if (ownerUserActive) connect()
                }
            }
            context!!.registerReceiver(users, IntentFilter("android.intent.action.USER_SWITCHED"), null, worker, Context.RECEIVER_EXPORTED)
            receivers += users
        } catch (failure: Throwable) {
            diagnostic("RUNTIME_CONTEXT_FAILED", failure)
            for (receiver in receivers) runCatching { context?.unregisterReceiver(receiver) }
            receivers.clear(); context = null
        }
    }
    private fun connect() {
        if (disposed || peer != null || connection != null || !connecting.compareAndSet(false, true)) return
        try {
            initializeContext()
            val ctx = context ?: return
            ownerUserActive = Class.forName("android.app.ActivityManager").getDeclaredMethod("getCurrentUser").invoke(null) == 0
            if (!ownerUserActive) return
            val info = ctx.packageManager.getApplicationInfo("dev.opluskeycontrol", 0)
            // Never restart an explicitly force-stopped app from a physical key.
            if (info.flags and android.content.pm.ApplicationInfo.FLAG_STOPPED != 0) return
            val uid = info.uid
            require(uid != Process.SYSTEM_UID && uid >= 10000)
            val candidate = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (disposed || connection !== this) return
                    try {
                        val endpoint = object : Binder() {
                            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                                if (code == INTERFACE_TRANSACTION) { reply?.writeString(PolicyBinder.DESCRIPTOR); return true }
                                if (getCallingUid() != uid || callback !== this) return false
                                return try {
                                    data.enforceInterface(PolicyBinder.DESCRIPTOR)
                                    when (code) {
                                        PolicyBinder.CONFIG -> {
                                            val version = data.readLong()
                                            val bytes = requireNotNull(data.createByteArray())
                                            require(bytes.size in 1..PolicyWire.MAX_BINDER_BYTES)
                                            val mask = data.readInt(); val isRecording = data.readInt()
                                            require(data.dataAvail() == 0 && isRecording in 0..1 && mask ushr ActionKind.entries.size == 0)
                                            worker.post {
                                                if (callback !== this || peer !== service || disposed) return@post
                                                try {
                                                    require(version > revision)
                                                    val configuration = RuleCodec.decode(PolicyWire.binderText(bytes))
                                                    val allowed = ActionKind.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()
                                                    synchronized(lock) {
                                                        revision = version; recording = isRecording == 1
                                                        val policy = PolicyConfiguration(configuration.rules, configuration.enabled, configuration.tier)
                                                        val lease = BackendLease(version, Long.MAX_VALUE, true, allowed)
                                                        val plusTriggers = setOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG).map { Trigger(Button.PLUS, it) }.toSet() +
                                                            if (dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) setOf(Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_UP), Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_DOWN)) else emptySet()
                                                        session.configure(policy, configuration.timing, PolicyProof(Build.DISPLAY, plusTriggers, true), lease)
                                                        if (dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) power.configure(policy,
                                                            PolicyProof(Build.DISPLAY, setOf(Trigger(Button.POWER, Gesture.DOUBLE)), true), lease, powerTimeout)
                                                        worker.removeCallbacks(deadline)
                                                    }
                                                } catch (failure: Exception) { diagnostic("RUNTIME_CONFIG_FAILED", failure); drop() }
                                            }
                                        }
                                        PolicyBinder.DISCONNECT -> {
                                            val retry = data.readInt()
                                            require(retry in 0..1 && data.dataAvail() == 0)
                                            worker.post { if (callback === this) {
                                                drop()
                                                if (retry == 1 && ++reconnectAttempts < 5) worker.postDelayed(reconnect, 1000)
                                            } }
                                        }
                                        else -> return false
                                    }
                                    true
                                } catch (failure: Exception) {
                                    worker.post { if (callback === this) { diagnostic("RUNTIME_PARCEL_FAILED", failure); drop() } }
                                    false
                                }
                            }
                        }
                        val recipient = IBinder.DeathRecipient {
                            if (peer === service) drop()
                        }
                        service.linkToDeath(recipient, 0)
                        peerDeath = recipient; peer = service; callback = endpoint
                        worker.removeCallbacks(bindTimeout)
                        PolicyBinder.send(service, PolicyBinder.HELLO) { it.writeStrongBinder(endpoint); it.writeInt(features) }
                        diagnostic("RUNTIME_BINDER_CONNECTED")
                    } catch (failure: Exception) { diagnostic("RUNTIME_HELLO_FAILED", failure); drop() }
                }
                override fun onServiceDisconnected(name: ComponentName) { if (connection === this) drop() }
                override fun onBindingDied(name: ComponentName) { if (connection === this) drop() }
                override fun onNullBinding(name: ComponentName) { if (connection === this) drop() }
            }
            connection = candidate
            val accepted = ctx.bindService(Intent().setComponent(ComponentName("dev.opluskeycontrol",
                "dev.opluskeycontrol.input.BackendKeeperService")), Context.BIND_AUTO_CREATE, { worker.post(it) }, candidate)
            if (!accepted) { connection = null; diagnostic("RUNTIME_BIND_REJECTED") }
            else worker.postDelayed(bindTimeout, 5000)
        } catch (failure: Exception) { diagnostic("RUNTIME_BIND_FAILED", failure); drop() }
        finally {
            connecting.set(false)
            if (!disposed && peer == null && connection == null && ++reconnectAttempts < 5) worker.postDelayed(reconnect, 1000)
        }
    }
    private fun schedule() {
        worker.removeCallbacks(deadline)
        session.nextDeadline()?.let { worker.postAtTime(deadline, it) }
    }
    private fun dispatch(actions: List<PolicyDecision.Replace>) {
        for (action in actions) if (!queue.offer(action)) {
            session.disconnect() // bounded queue; never block input policy
        }
    }
    private fun writeActions() {
        while (!disposed) {
            val action = try { queue.take() } catch (_: InterruptedException) { return }
            val version = action.session; val id = action.rule.id
            val target = peer ?: continue
            val endpoint = callback ?: continue
            if (version != revision) continue
            try {
                PolicyBinder.send(target, PolicyBinder.ACTION) {
                    it.writeStrongBinder(endpoint); it.writeLong(version); it.writeString(id)
                }
                if (action.rule.action.kind == ActionKind.RECORD_TOGGLE) {
                    val ctx = context ?: continue
                    val intent = Intent().setComponent(ComponentName("dev.opluskeycontrol", "dev.opluskeycontrol.actions.RecorderService"))
                    if (recording) ctx.startService(intent.setAction(RecorderService.STOP))
                    else ctx.startForegroundService(intent.setAction(RecorderService.TOGGLE).putExtra("seconds", action.rule.action.durationSeconds)
                        .putExtra("originButton", action.rule.trigger.button.name).putExtra("originPartner", action.rule.trigger.partner?.name)
                        .putExtra("originGesture", action.rule.trigger.gesture.name).putExtra("originRule", action.rule.id))
                }
            } catch (_: Exception) { if (peer === target) drop() }
        }
    }
    private fun drop() {
        val old: IBinder?
        val oldCallback: IBinder?
        val oldConnection: ServiceConnection?
        val oldDeath: IBinder.DeathRecipient?
        synchronized(lock) {
            session.disconnect(); power.disconnect(); revision = 0; queue.clear(); worker.removeCallbacks(deadline)
            old = peer; oldCallback = callback; oldConnection = connection; oldDeath = peerDeath
            peer = null; callback = null; connection = null; peerDeath = null
        }
        worker.post {
            worker.removeCallbacks(bindTimeout)
            if (old != null) {
                if (oldDeath != null) runCatching { old.unlinkToDeath(oldDeath, 0) }
                if (oldCallback != null) runCatching {
                    PolicyBinder.send(old, PolicyBinder.DISCONNECT) { it.writeStrongBinder(oldCallback) }
                }
            }
            if (oldConnection != null) runCatching { context?.unbindService(oldConnection) }
        }
    }
    private fun dispose() {
        if (disposed) return
        disposed = true
        worker.removeCallbacks(reconnect)
        worker.removeCallbacks(initializer)
        drop()
        runCatching { handle?.unhook() }; handle = null
        for (powerHandle in powerHandles) runCatching { powerHandle.unhook() }
        powerHandles.clear()
        writer?.interrupt(); writer = null
        for (receiver in receivers.toList()) runCatching { context?.unregisterReceiver(receiver) }
        receivers.clear()
        // This runs on the policy worker; its queued unbind executes before shutdown.
        worker.post { thread.quitSafely() }
    }
}
