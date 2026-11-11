package com.duck.app.util.douyin

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * Fallback extractor that renders the real Douyin page in a WebView.
 *
 * Used when the unsigned API refuses an item (image posts get reason=8) and as the
 * safety net if Douyin changes the API shape. Because a real browser engine loads
 * the page, the site's own JS performs whatever signing it needs; we only intercept
 * the detail response it fetches, so no reverse-engineered signature is involved.
 */
object DouyinWebParser {

    private const val TAG = "DouyinWebParser"
    private const val TIMEOUT_MS = 30_000L

    /** Endpoints whose JSON body carries the item the page is displaying. */
    private val CAPTURE_HINTS =
        listOf(
            "/aweme/detail/",
            "/aweme/slidesinfo/",
            "/aweme/iteminfo/",
            "/aweme/post/",
        )

    suspend fun parse(context: Context, input: String): Result<DouyinPost> =
        withContext(Dispatchers.Main) {
            val id = DouyinParser.extractId(DouyinParser.resolveShortLink(input))
                ?: return@withContext Result.failure(
                    IllegalArgumentException("链接里没有作品 ID")
                )

            val captured = captureDetail(context, id)
            if (captured != null) {
                Result.success(DouyinParser.normalize(captured))
            } else {
                Result.failure(IllegalStateException("页面没有返回作品数据"))
            }
        }

    /** Opens the share page and waits for a detail response to pass through. */
    private suspend fun captureDetail(context: Context, id: String): JSONObject? {
        var hit: JSONObject? = null
        val view = newWebView(context) { json -> if (hit == null) hit = json }

        val url = "https://www.iesdouyin.com/share/video/" + id + "/"
        view.loadUrl(url)

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && hit == null) {
            kotlinx.coroutines.delay(250)
        }
        Log.i(TAG, "capture finished, hit=" + (hit != null))

        val result = hit
        view.stopLoading()
        view.destroy()
        return result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(
        context: Context,
        onDetail: (JSONObject) -> Unit,
    ): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = DouyinParser.USER_AGENT
            // Never show it: it exists only to let the page run.
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
            CookieManager.getInstance().setAcceptCookie(true)

            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (CAPTURE_HINTS.none { url.contains(it) }) return null
                        return try {
                            // Re-issue the request ourselves so the body can be inspected;
                            // the page still receives the original bytes.
                            val body = fetchBody(url)
                            if (body != null) {
                                parseDetail(body)?.let { onDetail(it) }
                                body.toResponse()
                            } else null
                        } catch (e: Exception) {
                            Log.w(TAG, "intercept failed: " + e.message)
                            null
                        }
                    }
                }
        }

    private fun fetchBody(url: String): String? =
        runCatching {
            val request =
                okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", DouyinParser.USER_AGENT)
                    .header("Referer", "https://www.iesdouyin.com/")
                    .build()
            val client =
                okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .build()
            client.newCall(request).execute().use { it.body.string() }
        }.getOrNull()

    private fun String.toResponse(): WebResourceResponse =
        WebResourceResponse(
            "application/json",
            "utf-8",
            ByteArrayInputStream(toByteArray()),
        )

    /** The payload shape differs per endpoint; pick whichever holds item data. */
    private fun parseDetail(body: String): JSONObject? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        root.optJSONObject("aweme_detail")?.let { return it }
        root.optJSONArray("aweme_details")?.let { if (it.length() > 0) return it.getJSONObject(0) }
        root.optJSONArray("aweme_list")?.let { if (it.length() > 0) return it.getJSONObject(0) }
        root.optJSONArray("item_list")?.let { if (it.length() > 0) return it.getJSONObject(0) }
        return null
    }
}
