package dev.opluskeycontrol.actions

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import dev.opluskeycontrol.MainActivity
import dev.opluskeycontrol.R
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RecorderService : Service() {
    companion object {
        const val STOP = "dev.opluskeycontrol.STOP_RECORDING"
        const val TOGGLE = "dev.opluskeycontrol.TOGGLE_RECORDING"
        val recording = MutableStateFlow(false)
        val error = MutableStateFlow(false)
        val origin = MutableStateFlow<dev.opluskeycontrol.domain.Trigger?>(null)
    }
    @Volatile private var stopRequested = false
    @Volatile private var destroyed = false
    private var worker: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRequested = true }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || (intent?.action == TOGGLE && worker != null)) {
            stopRequested = true
            if (worker == null) stopSelf()
            return START_NOT_STICKY
        }
        if (worker != null) return START_NOT_STICKY
        val requestedOrigin = intent?.getStringExtra("originGesture")?.let { gesture -> runCatching {
            dev.opluskeycontrol.domain.Trigger(
                dev.opluskeycontrol.domain.Button.valueOf(intent.getStringExtra("originButton") ?: "PLUS"),
                dev.opluskeycontrol.domain.Gesture.valueOf(gesture),
                intent.getStringExtra("originPartner")?.let(dev.opluskeycontrol.domain.Button::valueOf))
        }.getOrNull() }
        if (intent?.hasExtra("originGesture") == true) {
            val app = application as dev.opluskeycontrol.KeyControlApp
            val c = app.store.config.value
            val trigger = requestedOrigin
            val rule = trigger?.let { dev.opluskeycontrol.domain.RuleResolver.resolve(c.rules, it, java.time.ZonedDateTime.now()) }
            if (!c.enabled || c.tier != dev.opluskeycontrol.domain.Tier.ROOT || app.store.emergency.value ||
                rule !is dev.opluskeycontrol.domain.Resolution.Match || rule.rule.action.kind != dev.opluskeycontrol.domain.ActionKind.RECORD_TOGGLE ||
                rule.rule.id != intent.getStringExtra("originRule")) { stopSelf(); return START_NOT_STICKY }
        }
        error.value = false
        stopRequested = false
        try {
            val channel = "recording"
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, getString(R.string.recording), NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.recording)).setContentText(getString(R.string.recording_notice))
                .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause, getString(R.string.stop), stop).build()
            if (Build.VERSION.SDK_INT >= 30) startForeground(20, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(20, notification)
            origin.value = requestedOrigin
            recording.value = true
            handler.postDelayed(autoStop, intent?.getIntExtra("seconds", 300)?.coerceIn(10, 7200)?.times(1000L) ?: 300000L)
            worker = Thread({ captureMp3() }, "OPlusKeyControl-MP3").also { it.start() }
        } catch (_: Exception) {
            error.value = true; recording.value = false; origin.value = null
            handler.removeCallbacks(autoStop); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Capture, encoding and storage stay off the main/UI thread; memory is bounded. */
    @Suppress("MissingPermission")
    private fun captureMp3() {
        var target: Uri? = null
        var legacy: java.io.File? = null
        var input: AudioRecord? = null
        var complete = false
        try {
            val name = "OPlusKeyControl_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".mp3"
            val output = if (Build.VERSION.SDK_INT >= 29) {
                target = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name); put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OPlusKeyControl"); put(MediaStore.Audio.Media.IS_PENDING, 1)
                }) ?: error("storage")
                contentResolver.openOutputStream(target, "w") ?: error("storage")
            } else {
                legacy = java.io.File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), name)
                legacy.outputStream()
            }
            output.buffered().use { stream ->
                Mp3Encoder().use { encoder ->
                    val minimum = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    require(minimum > 0)
                    val audio = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimum * 2, 16384)).build()
                    input = audio
                    check(audio.state == AudioRecord.STATE_INITIALIZED)
                    audio.startRecording()
                    check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    val pcm = ShortArray(4096)
                    val mp3 = ByteArray(16384)
                    var samples = 0L
                    val deadline = SystemClock.elapsedRealtime() + 7200000L
                    while (!stopRequested && SystemClock.elapsedRealtime() < deadline) {
                        val count = audio.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                        check(count > 0) { "Microphone read failed: $count" }
                        stream.write(mp3, 0, encoder.encode(pcm, count, mp3))
                        samples += count
                    }
                    check(samples > 0) { "Empty recording" }
                    stream.write(mp3, 0, encoder.finish(mp3))
                }
            }
            if (Build.VERSION.SDK_INT >= 29) check(contentResolver.update(requireNotNull(target), android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null) == 1)
            complete = true
        } catch (_: Exception) { error.value = true }
        finally {
            runCatching { input?.stop() }; runCatching { input?.release() }
            if (!complete) {
                target?.let { runCatching { contentResolver.delete(it, null, null) } }
                legacy?.let { runCatching { it.delete() } }
            }
            handler.post {
                handler.removeCallbacks(autoStop)
                worker = null; recording.value = false; origin.value = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (!destroyed) stopSelf()
            }
        }
    }
    override fun onDestroy() {
        destroyed = true; stopRequested = true; handler.removeCallbacks(autoStop)
        super.onDestroy()
    }
}
