package com.mercureal.nana.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** On-device text-to-speech using the system offline voice. No network. */
class Speaker(context: Context) {
    private val doneCallbacks = ConcurrentHashMap<String, () -> Unit>()
    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finish(utteranceId) }
                    @Deprecated("deprecated in API 21")
                    override fun onError(utteranceId: String?) { finish(utteranceId) }
                })
            }
        }
    }

    private fun finish(id: String?) {
        if (id != null) doneCallbacks.remove(id)?.invoke()
    }

    /** Speaks text, then calls onDone. Calls onDone immediately if TTS is not ready. */
    fun speak(text: String, onDone: () -> Unit) {
        val engine = tts
        if (!ready || engine == null) {
            onDone()
            return
        }
        val id = UUID.randomUUID().toString()
        doneCallbacks[id] = onDone
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        doneCallbacks.clear()
    }
}
