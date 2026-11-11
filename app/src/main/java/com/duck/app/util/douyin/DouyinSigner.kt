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
 * The signature comes from an obfuscated JS VM (acrawler.js) that reads
 * document.cookie to pick up the nonce. A headless WebView is the only
 * JavaScript runtime on Android, but a WebView refuses cookie writes for
 * about:blank ("SecurityError: Access is denied"), so cookie_shim.js replaces
 * document.cookie with a plain in-memory jar before the VM loads.
 */
object DouyinSigner {

    private const val TAG = "DouyinSigner"
    private const val TIMEOUT_MS = 15_000L
    private const val SHIM_PATH = "douyin/cookie_shim.js"
    private const val VM_PATH = "douyin/acrawler.js"

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

            // Cookie write and sign happen in one task so the VM cannot observe a stale jar.
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

            Log.d(TAG, "sign result=" + result)
            when {
                result.startsWith("ERR:") || result == "NO_VM" || result == "EMPTY" -> {
                    Log.w(TAG, "sign failed: " + result)
                    null
                }
                result.isEmpty() -> null
                else -> result
            }
        }

    /** Loads the cookie shim and the JSVMP bundle once. */
    private suspend fun ensureWebView(context: Context): WebView? {
        webView?.let { return it }
        return try {
            val view = newWebView(context)
            val shim = readAsset(context, SHIM_PATH)
            val vmSource = readAsset(context, VM_PATH)

            // Shim first: the VM captures document.cookie on load.
            view.evaluateJavascript(shim) { }
            delay(150)
            view.evaluateJavascript(vmSource) { }

            val deadline = System.currentTimeMillis() + 8_000
            var ready = false
            while (System.currentTimeMillis() < deadline) {
                val state =
                    suspendCancellableCoroutine<String?> { c ->
                        view.evaluateJavascript(
                            "(JSON.stringify({vm:typeof window.byted_acrawler," +
                                "shim:!!window.__duck_cookie_ok}))"
                        ) { r -> c.resume(r?.trim()?.trim('"')) }
                    }
                if (state != null && state.contains("\"vm\":\"object\"")) {
                    ready = true
                    Log.i(TAG, "bundle state: " + state)
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

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(context: Context): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = DouyinParser.USER_AGENT
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
        }
}
