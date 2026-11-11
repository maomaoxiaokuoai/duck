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
 *
 * The WebView is created once and kept alive; building it costs a few hundred milliseconds
 * and Douyin asks for a new signature on every session anyway.
 */
object DouyinSigner {

    private const val TAG = "DouyinSigner"
    private const val TIMEOUT_MS = 15_000L

    /** Bundled at app/src/main/assets/douyin/acrawler.js */
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
            val js = """
                (function() {
                  try {
                    if (!window.byted_acrawler) return '';
                    // Reset the cookie jar so every call signs a fresh nonce.
                    document.cookie = '__ac_nonce=' + '" + safeNonce + "';
                    window.byted_acrawler.init({ aid: 99999999, dfp: 0 });
                    var s = window.byted_acrawler.sign('', '" + safeNonce + "');
                    return s || '';
                  } catch (e) { return 'ERR:' + e; }
                })()
            """.trimIndent()

            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    view.evaluateJavascript(js) { raw ->
                        val value = raw?.trim()?.trim('"') ?: ""
                        if (value.startsWith("ERR:")) {
                            Log.w(TAG, "sign failed: " + value)
                            cont.resume(null)
                        } else {
                            cont.resume(value.ifEmpty { null })
                        }
                    }
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

            // Give the VM a moment to register byted_acrawler.
            val deadline = System.currentTimeMillis() + 8_000
            var ready = false
            while (System.currentTimeMillis() < deadline) {
                val has = suspendCancellableCoroutine<String?> { c ->
                    view.evaluateJavascript("(typeof window.byted_acrawler)") { r ->
                        c.resume(r?.trim()?.trim('"'))
                    }
                }
                if (has == "object") { ready = true; break }
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
            settings.allowFileAccess = true
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
            // about:blank gives a writable document.cookie for the local origin.
            loadUrl("about:blank")
        }
}