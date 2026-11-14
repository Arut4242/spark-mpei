package ru.mpei.assistant.ui.bars

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.mpei.assistant.security.WebUrlPolicy
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** Follow GET redirects ourselves: WebView does not intercept every resource redirect. */
internal object BarsLoginResourceLoader {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))

    fun load(request: WebResourceRequest): WebResourceResponse? {
        if (!WebUrlPolicy.isBarsUrl(request.url.toString())) return blocked()
        // Form POST bodies belong to WebView; no body copying or credential logging.
        if (request.method != "GET") return null
        return try {
            var url = request.url.toString()
            val cookies = CookieManager.getInstance()
            repeat(6) {
                if (!WebUrlPolicy.isBarsUrl(url)) return blocked()
                val builder = Request.Builder().url(url)
                request.requestHeaders.forEach { (name, value) ->
                    if (name.lowercase() !in setOf("host", "cookie", "connection", "accept-encoding",
                            "if-modified-since", "if-none-match", "content-length")) builder.header(name, value)
                }
                cookies.getCookie(url)?.let { builder.header("Cookie", it) }
                val response = client.newCall(builder.build()).execute()
                response.headers("Set-Cookie").forEach { cookies.setCookie(url, it) }
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    val next = response.header("Location")?.let { response.request.url.resolve(it)?.toString() }
                    response.close()
                    if (!WebUrlPolicy.isBarsUrl(next)) return blocked()
                    // Main-frame redirects must update WebView's URL/base URI and history.
                    // WebView reissues this GET and handles the verified redirect normally.
                    if (request.isForMainFrame) return null
                    url = checkNotNull(next)
                } else {
                    if (response.code !in 200..299 && response.code !in 400..599) {
                        response.close()
                        return blocked()
                    }
                    val body = response.body
                    val type = body?.contentType()
                    val headers = response.headers.toMultimap().filterKeys { !it.equals("Set-Cookie", true) }
                        .mapValues { it.value.joinToString(", ") }.toMutableMap()
                    headers["Content-Security-Policy"] = "default-src https://bars.mpei.ru; " +
                        "script-src https://bars.mpei.ru 'unsafe-inline' 'unsafe-eval'; " +
                        "style-src https://bars.mpei.ru 'unsafe-inline'; img-src https://bars.mpei.ru data:; " +
                        "connect-src https://bars.mpei.ru; form-action https://bars.mpei.ru; " +
                        "frame-src https://bars.mpei.ru; base-uri https://bars.mpei.ru; object-src 'none'"
                    return WebResourceResponse(
                        type?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                        type?.charset()?.name(), response.code, response.message.ifBlank { "Response" },
                        headers, body?.byteStream() ?: ByteArrayInputStream(byteArrayOf())
                    )
                }
            }
            blocked()
        } catch (_: Exception) { blocked() }
    }
}
