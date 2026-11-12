package com.duck.app.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Produces the __ac_signature Douyin requires before it renders a full page.
 *
 * Douyin gates its SSR payload behind a signed request. The signature comes from
 * byted_acrawler, an obfuscated JS virtual machine shipped inside Douyin's own shell
 * page. Reimplementing that VM in Kotlin is impractical, so the official bytecode is
 * bundled at assets/douyin/acrawler.js and executed in a hidden WebView.
 *
 * One WebView is created on first use and reused for the process lifetime. Booting it
 * costs a few hundred milliseconds once, then each signature takes about 3ms. The
 * instance keeps no browsing state, and no cookie is read or persisted beyond the
 * nonce handed in.
 */
class DouyinSigner(private val context: Context) {

    private var webView: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private val ready = CountDownLatch(1)
    private var failed = false

    /** Returns the signature for [nonce], or null when the JSVM could not run. */
    suspend fun sign(nonce: String): String? {
        val loaded = ensureLoaded()
        Log.d(TAG, "JSVM ready=" + loaded)
        if (!loaded) return null

        return suspendCancellableCoroutine { cont ->
            main.post {
                try {
                    val view = webView
                    if (view == null) {
                        if (cont.isActive) cont.resume(null)
                        return@post
                    }
                    val js = "(function(){try{" +
                        "var s=window.byted_acrawler;" +
                        "if(!s||typeof s.sign!=='function')return 'ERR:NO_API';" +
                        "return s.sign('', " + quote(nonce) + ");" +
                        "}catch(e){return 'ERR:'+e.message}})()"

                    view.evaluateJavascript(js) { raw ->
                        val value = raw?.trim('"')?.replace("\\\"", "\"")
                        if (value == null || !value.startsWith("_02B")) {
                            Log.w(TAG, "unexpected signature: " + raw)
                        } else {
                            Log.d(TAG, "signed nonce=" + nonce.take(8) + " sig=" + value.take(20))
                        }
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
    }

    /**
     * Makes sure the JSVM has finished loading.
     *
     * The WebView loads the bytecode asynchronously, so awaiting [ready] here is what
     * keeps the first call from evaluating against an empty document.
     */
    private suspend fun ensureLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (failed) {
            false
        } else if (ready.count == 0L) {
            true
        } else {
            loadAndWait()
        }
    }

    /** Boots the WebView if needed, then blocks until the JSVM reports ready. */
    private suspend fun loadAndWait(): Boolean = withContext(Dispatchers.IO) {
        main.post {
            if (webView == null) {
                val script = try {
                    context.assets.open("douyin/acrawler.js")
                        .bufferedReader()
                        .use { it.readText() }
                } catch (e: Exception) {
                    Log.w(TAG, "acrawler.js missing: " + e.message)
                    failed = true
                    ready.countDown()
                    return@post
                }
                webView = createWebView(script)
            }
        }

        // Booting a WebView and compiling a 70KB JSVM is slow on a cold process.
        val ok = ready.await(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ok) {
            Log.w(TAG, "JSVM load timed out")
            failed = true
        }
        ok && !failed
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(script: String): WebView {
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = USER_AGENT
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                v ?: return
                v.evaluateJavascript(script) {
                    v.evaluateJavascript("typeof window.byted_acrawler") { t ->
                        if (t != null && t.contains("object")) {
                            v.evaluateJavascript(
                                "window.byted_acrawler.init({aid:99999999,dfp:0})",
                            ) {
                                ready.countDown()
                            }
                        } else {
                            Log.w(TAG, "JSVM did not expose byted_acrawler: " + t)
                            failed = true
                            ready.countDown()
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
        }
    }

    private fun quote(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private companion object {
        const val TAG = "DouyinSigner"
        const val LOAD_TIMEOUT_SECONDS = 25L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
