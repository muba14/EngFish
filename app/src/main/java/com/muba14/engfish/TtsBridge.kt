package com.muba14.engfish

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale

/**
 * Exposes Android's native TextToSpeech to the web UI as `window.AndroidTTS`.
 * JS calls speak(text, rate, pitch, id); when an utterance finishes, the bridge
 * calls `window.__ttsDone(id)` so the podcast player can move to the next line.
 */
class TtsBridge(context: Context, private val webView: WebView) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    private val pending = mutableListOf<Array<Any>>()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val res = tts.setLanguage(Locale.US)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = notifyDone(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = notifyDone(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = notifyDone(utteranceId)
        })
        ready = true
        synchronized(pending) {
            pending.forEach { speakNow(it[0] as String, it[1] as Float, it[2] as Float, it[3] as String) }
            pending.clear()
        }
    }

    @JavascriptInterface
    fun speak(text: String, rate: Double, pitch: Double, id: String) {
        val r = rate.toFloat().coerceIn(0.5f, 2f)
        val p = pitch.toFloat().coerceIn(0.5f, 2f)
        if (!ready) {
            synchronized(pending) { pending.add(arrayOf(text, r, p, id)) }
            return
        }
        speakNow(text, r, p, id)
    }

    private fun speakNow(text: String, rate: Float, pitch: Float, id: String) {
        // Rate/pitch are global engine settings, so set them right before queueing.
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    @JavascriptInterface
    fun stop() {
        synchronized(pending) { pending.clear() }
        if (ready) tts.stop()
    }

    private fun notifyDone(id: String?) {
        if (id == null) return
        val safe = id.replace("'", "")
        webView.post { webView.evaluateJavascript("window.__ttsDone && window.__ttsDone('$safe')", null) }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
