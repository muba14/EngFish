package com.muba14.engfish

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewAssetLoader

/**
 * EngFish is a hybrid app: the UI lives in assets/www (HTML/CSS/JS) and is served
 * through WebViewAssetLoader from a stable https origin, so localStorage (progress,
 * streak, flames, cards) is kept between launches. Text-to-speech uses the native
 * Android engine through [TtsBridge].
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var tts: TtsBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#06182E")
        window.navigationBarColor = Color.parseColor("#020A14")

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#06182E"))
        setContentView(webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
        }
        webView.isVerticalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)
        }

        tts = TtsBridge(this, webView)
        webView.addJavascriptInterface(tts, "AndroidTTS")

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.efBack && window.efBack()") { handled ->
                    if (handled != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        tts.stop()
        super.onPause()
    }

    override fun onDestroy() {
        tts.shutdown()
        webView.destroy()
        super.onDestroy()
    }
}
