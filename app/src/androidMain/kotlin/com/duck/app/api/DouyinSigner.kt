package com.duck.app.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Produces the __ac_signature Douyin requires before it renders a full page.
 *
 * Douyin gates its SSR payload behind a signed request. The signature comes from
 * byted_acrawler, an obfuscated JS virtual machine shipped inside Douyin's own shell
 * page. Reimplementing that VM in Kotlin is impractical, so the official bytecode is
 * bundled at assets/douyin/acrawler.js and executed in a hidden WebView.
 *
 * One WebView is created on first use and reused for the process lifetime: booting it
 * costs a few hundred milliseconds once, then each signature takes ~3ms. The instance
 * keeps no browsing state, and no cookie is read or persisted beyond the nonce passed in.
 */
class DouyinSigner(private val context: Context) {

    private var webView: WebView? = null
    private var ready = false
    private val main = Handler(Looper.getMainLooper())

    /** Returns the signature for [nonce], or null when the JSVM could not run. */
    suspend fun sign(nonce: String): String? = suspendCancellableCoroutine { cont ->
        main.post {
            try {
                val view = getOrCreateWebView()
                val js = "(function(){try{" +
                    "var s=window.byted_acrawler;" +
                    "if(!s||typeof s.sign!=='function')return 'ERR:NO_API';" +
                    "return s.sign('', " + quote(nonce) + ");" +
                    "}catch(e){return 'ERR:'+e.message}})()"

                view.evaluateJavascript(js) { raw ->
                    val value = raw?.trim('"')?.replace("\\\"", "\"")
                    if (cont.isActive) {
                        cont.resume(value?.takeIf { it.startsWith("_02B") })
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "signature failed: " + e.message)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Builds the WebView once and loads the JSVM into it. */
    private fun getOrCreateWebView(): WebView {
        webView?.let { if (ready) return it }

        val script = context.assets.open("douyin/acrawler.js")
            .bufferedReader()
            .use { it.readText() }

        val view = webView ?: createWebView(script)
        webView = view
        return view
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(script: String): WebView {
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = USER_AGENT
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                v?.evaluateJavascript(script) {
                    v.evaluateJavascript("typeof window.byted_acrawler") { t ->
                        ready = t?.contains("object") == true
                        if (ready) {
                            v.evaluateJavascript("window.byted_acrawler.init({aid:99999999,dfp:0})", null)
                        } else {
                            Log.w(TAG, "JSVM did not expose byted_acrawler")
                        }
                    }
                }
            }
        }
        view.loadDataWithBaseURL(
            "https://www.douyin.com/",
            "<!doctype html><html><head><meta charset=utf-8></head><body></body></html>",
            "text/html",
            "utf-8",
            null,
        )
        return view
    }

    /** Releases the WebView when signing is no longer needed. */
    fun dispose() {
        main.post {
            try {
                webView?.destroy()
            } catch (_: Exception) {
            }
            webView = null
            ready = false
        }
    }

    private fun quote(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private companion object {
        const val TAG = "DouyinSigner"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
