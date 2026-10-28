package com.mercureal.nana

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mercureal.nana.voice.ActionExecutor
import com.mercureal.nana.voice.Command
import com.mercureal.nana.voice.CommandParser
import com.mercureal.nana.voice.Speaker
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Foreground service: keeps the microphone open while the app is backgrounded.
 * Offline pipeline: Vosk recognizer -> wake word check -> CommandParser -> ActionExecutor -> TTS.
 */
class NanaService : Service(), RecognitionListener {

    companion object {
        private const val CHANNEL_ID = "nana_listening"
        private const val NOTIF_ID = 42
        private const val MODEL_DIR = "model"              // bundled under app/src/main/assets/model
        private const val AWAKE_WINDOW_MS = 8000L          // "nana" alone -> next phrase is the command

        const val ACTION_START = "com.mercureal.nana.START"
        const val ACTION_STOP = "com.mercureal.nana.STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, NanaService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, NanaService::class.java).setAction(ACTION_STOP))
        }
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speaker: Speaker? = null
    private var executor: ActionExecutor? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var awakeUntil = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        speaker = Speaker(this)
        executor = ActionExecutor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdownListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notif = buildNotification("starting")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        loadModelAndListen()
        return START_STICKY
    }

    private fun loadModelAndListen() {
        if (model != null) {
            startListening()
            return
        }
        setStatus("loading speech model")
        StorageService.unpack(this, MODEL_DIR, "model",
            { m: Model ->
                model = m
                main.post { startListening() }
            },
            { e: java.io.IOException ->
                main.post { setStatus("model failed: ${e.message}"); stopSelf() }
            })
    }

    private fun startListening() {
        val m = model ?: return
        if (speechService != null) return
        try {
            val recognizer = Recognizer(m, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f).also { it.startListening(this) }
            NanaState.setListening(true, "listening, say nana")
            updateNotification("listening, say \"nana\"")
        } catch (e: Exception) {
            setStatus("mic failed: ${e.message}")
        }
    }

    private fun shutdownListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        NanaState.setListening(false, "off")
    }

    // --- RecognitionListener -------------------------------------------------

    override fun onPartialResult(hypothesis: String?) {}

    override fun onResult(hypothesis: String?) {
        val text = JSONObject(hypothesis ?: return).optString("text").trim()
        if (text.isNotEmpty()) main.post { handleUtterance(text) }
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = JSONObject(hypothesis ?: return).optString("text").trim()
        if (text.isNotEmpty()) main.post { handleUtterance(text) }
    }

    override fun onError(exception: Exception?) {
        setStatus("error: ${exception?.message}")
    }

    override fun onTimeout() {}

    private fun handleUtterance(text: String) {
        val now = System.currentTimeMillis()
        val afterWake = CommandParser.afterWakeWord(text)
        val command: String = when {
            afterWake == null && now < awakeUntil -> text       // already awake: take the phrase as-is
            afterWake == null -> return                         // no wake word: ignore
            afterWake.isEmpty() -> {                            // only "nana": wait for the next phrase
                awakeUntil = now + AWAKE_WINDOW_MS
                NanaState.addLog("nana")
                speak("yes?")
                return
            }
            else -> afterWake
        }
        awakeUntil = 0L
        runCommand(command)
    }

    private fun runCommand(text: String) {
        val cmd = CommandParser.parse(text)
        NanaState.addLog("you: $text")
        val reply = executor?.execute(cmd) ?: "not ready"
        NanaState.addLog("nana: $reply")
        if (cmd == Command.StopListening) {
            speak(reply) { main.post { shutdownListening(); stopSelf() } }
        } else {
            speak(reply)
        }
    }

    private fun speak(text: String, onDone: () -> Unit = {}) {
        // Pause the recognizer while speaking so NaNa does not hear its own voice.
        speechService?.setPause(true)
        val done = {
            main.post { speechService?.setPause(false) }
            onDone()
        }
        val tts = speaker
        if (tts == null) done() else tts.speak(text, done)
    }

    private fun setStatus(s: String) {
        NanaState.setListening(speechService != null, s)
        NanaState.addLog(s)
        updateNotification(s)
    }

    // --- notification --------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NanaService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NaNa")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shutdownListening()
        speaker?.shutdown()
        model?.close()
        model = null
        NanaState.setListening(false, "off")
        super.onDestroy()
    }
}
