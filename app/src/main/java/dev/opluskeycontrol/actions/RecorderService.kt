package dev.opluskeycontrol.actions

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
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
    private var recorder: MediaRecorder? = null
    private var uri: Uri? = null
    private var descriptor: ParcelFileDescriptor? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { finishRecording() }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finishRecording(); return START_NOT_STICKY }
        if (intent?.action == TOGGLE && recorder != null) { finishRecording(); return START_NOT_STICKY }
        if (intent?.hasExtra("originGesture") == true) {
            val app = application as dev.opluskeycontrol.KeyControlApp
            val c = app.store.config.value
            val trigger = runCatching { dev.opluskeycontrol.domain.Trigger(dev.opluskeycontrol.domain.Button.PLUS,
                dev.opluskeycontrol.domain.Gesture.valueOf(requireNotNull(intent.getStringExtra("originGesture")))) }.getOrNull()
            val rule = trigger?.let { dev.opluskeycontrol.domain.RuleResolver.resolve(c.rules, it, java.time.ZonedDateTime.now()) }
            if (!c.enabled || c.tier != dev.opluskeycontrol.domain.Tier.ROOT || app.store.emergency.value ||
                rule !is dev.opluskeycontrol.domain.Resolution.Match || rule.rule.action.kind != dev.opluskeycontrol.domain.ActionKind.RECORD_TOGGLE ||
                rule.rule.id != intent.getStringExtra("originRule")) { stopSelf(); return START_NOT_STICKY }
        }
        if (recorder != null) return START_NOT_STICKY
        error.value = false
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
            val name = "OPlusKeyControl_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".m4a"
            if (Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name); put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OPlusKeyControl"); put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("storage")
                descriptor = contentResolver.openFileDescriptor(uri!!, "w") ?: error("storage")
            } else {
                val file = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), name)
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
            }
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder = r
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(128000); r.setAudioSamplingRate(44100)
            r.setOutputFile(descriptor!!.fileDescriptor)
            r.setOnErrorListener { _, _, _ -> error.value = true; finishRecording() }
            r.prepare(); r.start(); recording.value = true
            origin.value = intent?.getStringExtra("originGesture")?.let { name -> runCatching {
                dev.opluskeycontrol.domain.Trigger(dev.opluskeycontrol.domain.Button.PLUS, dev.opluskeycontrol.domain.Gesture.valueOf(name))
            }.getOrNull() }
            handler.postDelayed(autoStop, intent?.getIntExtra("seconds", 300)?.coerceIn(10, 7200)?.times(1000L) ?: 300000L)
        } catch (_: Exception) { error.value = true; discard(); stopSelf() }
        return START_NOT_STICKY
    }
    private fun discard() {
        runCatching { recorder?.reset(); recorder?.release() }; recorder = null
        runCatching { descriptor?.close() }; descriptor = null
        uri?.let { runCatching { contentResolver.delete(it, null, null) } }; uri = null
        recording.value = false; origin.value = null; handler.removeCallbacks(autoStop)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    private fun finishRecording() {
        handler.removeCallbacks(autoStop)
        val stopped = runCatching { recorder?.stop() }.isSuccess
        runCatching { recorder?.release() }; recorder = null
        runCatching { descriptor?.close() }; descriptor = null
        uri?.let { target -> runCatching {
            if (stopped && Build.VERSION.SDK_INT >= 29) contentResolver.update(target, android.content.ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            else contentResolver.delete(target, null, null)
        }.onFailure { error.value = true } }
        if (!stopped) error.value = true
        uri = null; recording.value = false; origin.value = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { if (recorder != null) finishRecording(); super.onDestroy() }
}
