package com.duck.app.util.douyin

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Computes the __ac_signature cookie Douyin requires.
 *
 * The signature is produced by an obfuscated JS VM (acrawler.js) that expects a handful of
 * browser globals. A headless WebView is the only JavaScript runtime available on Android,
 * so the bundle is loaded there and byted_acrawler.sign is called directly.
 */
object DouyinSigner {

    private const val TAG = "DouyinSigner"
    private const val TIMEOUT_MS = 15_000L
    private const val ASSET_PATH = "douyin/acrawler.js"

    @Volatile private var webView: WebView? = null
    @Volatile private var bundleReady = false

    /** Returns the __ac_signature for [nonce], or null when the runtime cannot produce one. */
    suspend fun sign(context: Context, nonce: String): String? =
        withContext(Dispatchers.Main) {
            val view = ensureWebView(context) ?: return@withContext null
            if (!bundleReady) {
                Log.w(TAG, "acrawler bundle not ready")
                return@withContext null
            }
            val safeNonce = nonce.replace("'", "")

            // Write the nonce into document.cookie, then sign. Both statements run in the
            // same task so the VM observes the cookie we just set.
            val js =
                "(function(){" +
                    "try{" +
                    "document.cookie='__ac_nonce=" + safeNonce + "';" +
                    "if(!window.byted_acrawler)return 'NO_VM';" +
                    "window.byted_acrawler.init({aid:99999999,dfp:0});" +
                    "var s=window.byted_acrawler.sign('','" + safeNonce + "');" +
                    "return s||'EMPTY';" +
                    "}catch(e){return 'ERR:'+e}" +
                    "})()"

            val result =
                withTimeoutOrNull(TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        view.evaluateJavascript(js) { raw ->
                            cont.resume(raw?.trim()?.trim('"') ?: "")
                        }
                    }
                } ?: ""

            Log.d(TAG, "sign raw=" + result)
            when {
                result.startsWith("ERR:") || result == "NO_VM" || result == "EMPTY" -> {
                    Log.w(TAG, "sign failed: " + result)
                    null
                }
                result.isEmpty() -> null
                else -> result
            }
        }

    /** Reads document.cookie from the headless view, for diagnostics. */
    suspend fun readCookieJar(): String =
        withContext(Dispatchers.Main) {
            val view = webView ?: return@withContext ""
            suspendCancellableCoroutine { cont ->
                view.evaluateJavascript("(document.cookie||'')") { raw ->
                    cont.resume(raw?.trim()?.trim('"') ?: "")
                }
            }
        }

    /** Loads the JSVMP bundle once. */
    private suspend fun ensureWebView(context: Context): WebView? {
        webView?.let { return it }
        return try {
            val view = newWebView(context)
            val src = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            view.evaluateJavascript(src) { }

            val deadline = System.currentTimeMillis() + 8_000
            var ready = false
            while (System.currentTimeMillis() < deadline) {
                val has =
                    suspendCancellableCoroutine<String?> { c ->
                        view.evaluateJavascript("(typeof window.byted_acrawler)") { r ->
                            c.resume(r?.trim()?.trim('"'))
                        }
                    }
                if (has == "object") {
                    ready = true
                    break
                }
                delay(120)
            }
            bundleReady = ready
            Log.i(TAG, "acrawler ready: " + ready)
            webView = view
            view
        } catch (e: Exception) {
            Log.w(TAG, "webview init failed", e)
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(context: Context): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = DouyinParser.USER_AGENT
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
        }
}
