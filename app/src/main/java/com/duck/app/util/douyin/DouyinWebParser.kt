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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Fallback extractor that renders the real Douyin page in a headless WebView.
 *
 * Used when the unsigned API refuses an item (image posts come back with reason=8).
 * A real browser engine loads the page, so the site's own JavaScript performs
 * whatever signing it needs; we only read the JSON it fetches, so nothing is
 * reverse-engineered or hard-coded.
 */
object DouyinWebParser {

    private const val TAG = "DouyinWebParser"
    private const val TIMEOUT_MS = 30_000L

    /** Responses that carry the item currently on screen. */
    private val CAPTURE_HINTS =
        listOf("/aweme/detail/", "/aweme/slidesinfo/", "/aweme/iteminfo/", "/aweme/post/")

    suspend fun parse(context: Context, input: String): Result<DouyinPost> =
        withContext(Dispatchers.Main) {
            val resolved = DouyinParser.resolveShortLink(input)
            val id = DouyinParser.extractId(resolved)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("链接里没有作品 ID")
                )

            val captured = capture(context, id)
            if (captured != null) {
                Result.success(DouyinParser.normalize(captured))
            } else {
                Result.failure(IllegalStateException("页面没有返回作品数据"))
            }
        }

    /**
     * Loads the note page and waits for the app's own detail request.
     *
     * The request is intercepted before it leaves the WebView: we fetch the same URL
     * ourselves (the page's cookies are shared by CookieManager), parse it, then hand
     * the original bytes back so the page keeps rendering normally.
     */
    private suspend fun capture(context: Context, id: String): JSONObject? {
        var hit: JSONObject? = null
        val view = buildWebView(context) { json -> if (hit == null) hit = json }

        // Both spellings resolve to the same post; note is the one that fails the API.
        view.loadUrl("https://www.iesdouyin.com/share/note/" + id + "/")

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var navigated = false
        while (System.currentTimeMillis() < deadline) {
            if (hit != null) break
            // Give the SPA a nudge: it sometimes only fetches detail after a scroll.
            if (!navigated && System.currentTimeMillis() > deadline - TIMEOUT_MS + 6_000) {
                view.evaluateJavascript("window.scrollTo(0, 600);", null)
                navigated = true
            }
            delay(250)
        }

        Log.i(TAG, "capture finished, hit=" + (hit != null))
        val result = hit
        runCatching {
            view.stopLoading()
            view.destroy()
        }
        return result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(
        context: Context,
        onDetail: (JSONObject) -> Unit,
    ): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = DouyinParser.USER_AGENT
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (CAPTURE_HINTS.none { url.contains(it) }) return null
                        Log.d(TAG, "intercepted " + url.take(110))
                        return try {
                            val body = fetchWithPageCookies(url)
                            if (body == null) return null
                            extractItem(body)?.let(onDetail)
                            asResponse(body)
                        } catch (e: Exception) {
                            Log.w(TAG, "intercept failed: " + e.message)
                            null
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Some builds inject the detail later; nudge it once.
                        view?.evaluateJavascript("window.scrollTo(0, 400);", null)
                    }
                }
        }

    /** Re-issues the request with the WebView's cookie jar. */
    private fun fetchWithPageCookies(url: String): String? =
        runCatching {
            val cookies = CookieManager.getInstance().getCookie(url) ?: ""
            val request =
                okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", DouyinParser.USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.iesdouyin.com/")
                    .apply { if (cookies.isNotEmpty()) header("Cookie", cookies) }
                    .build()
            okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .build()
                .newCall(request)
                .execute()
                .use { if (it.isSuccessful) it.body.string() else null }
        }.getOrNull()

    private fun asResponse(body: String): WebResourceResponse =
        WebResourceResponse("application/json", "utf-8", ByteArrayInputStream(body.toByteArray()))

    /** The payload shape differs per endpoint; take whichever holds the item. */
    private fun extractItem(body: String): JSONObject? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        root.optJSONObject("aweme_detail")?.let { return it }
        root.optJSONArray("aweme_details")?.let { if (it.length() > 0) return it.getJSONObject(0) }
        root.optJSONArray("aweme_list")?.let { if (it.length() > 0) return it.getJSONObject(0) }
        root.optJSONArray("item_list")?.let { if (it.length() > 0) return it.getJSONObject(0) }
        return null
    }
}
