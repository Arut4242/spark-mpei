package ru.mpei.assistant.ui.bars

import ru.mpei.assistant.security.WebUrlPolicy
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BarsLoginWebView(
    onDismiss: () -> Unit,
    onLoginSuccess: (cookies: String, studentUrl: String?) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentHost by remember { mutableStateOf("bars.mpei.ru") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var detectedLoggedIn by remember { mutableStateOf(false) }

    fun isAllowedUrl(url: String?): Boolean = WebUrlPolicy.isBarsUrl(url)

    fun captureAndComplete() {
        if (!isAllowedUrl(webViewInstance?.url)) return
        val cookieManager = CookieManager.getInstance()
        val c1 = cookieManager.getCookie("https://bars.mpei.ru/bars_web/")
        val c2 = cookieManager.getCookie("https://bars.mpei.ru/")
        val currentUrl = webViewInstance?.url
        val combined = listOfNotNull(c1, c2).distinct().joinToString("; ")
        if (combined.isNotBlank()) {
            onLoginSuccess(combined, currentUrl)
        } else {
            ru.mpei.assistant.util.AppLogger.w("BARS_LOGIN", "captureAndComplete called, but cookies are empty!")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Вход в БАРС МЭИ")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Защищённое подключение",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentHost,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        ru.mpei.assistant.util.AppLogger.w("BARS_LOGIN", "User closed login dialog without saving (✕ clicked)")
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "User tapped 'Я вошёл' button")
                            captureAndComplete()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Я вошёл", fontSize = 13.sp)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (detectedLoggedIn) "Вход выполнен! Сохраняем сессию..." else "Войдите через единую учетную запись МЭИ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Сессия шифруется аппаратно на вашем устройстве",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = {
                            ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "User tapped 'Сохранить вход' button")
                            captureAndComplete()
                        }
                    ) {
                        Text("Сохранить вход")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewInstance = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        setDownloadListener { _, _, _, _, _ -> }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                !isAllowedUrl(request?.url?.toString())

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                !isAllowedUrl(url)

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                                request?.let { BarsLoginResourceLoader.load(it) } ?: BarsLoginResourceLoader.blocked()

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (!isAllowedUrl(url)) {
                                    view?.stopLoading()
                                    return
                                }
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                val parsedHost = url?.let { Uri.parse(it).host }
                                if (!parsedHost.isNullOrBlank()) {
                                    currentHost = parsedHost
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!isAllowedUrl(url)) return
                                super.onPageFinished(view, url)
                                isLoading = false
                                val parsedHost = url?.let { Uri.parse(it).host }
                                if (!parsedHost.isNullOrBlank()) {
                                    currentHost = parsedHost
                                }

                                // Проверяем, появились ли элементы авторизованного личного кабинета
                                view?.evaluateJavascript(
                                    """(function() { 
                                        var text = (document.body ? document.body.innerText : '') || '';
                                        var url = window.location.href || '';
                                        var cookie = document.cookie || '';
                                        return (
                                            text.indexOf('Оценки БАРС') !== -1 || 
                                            text.indexOf('Развернуть всё') !== -1 || 
                                            text.indexOf('Сводка') !== -1 || 
                                            text.indexOf('Выход') !== -1 ||
                                            url.indexOf('studentID') !== -1 ||
                                            url.indexOf('ST_Study') !== -1 ||
                                            url.indexOf('ListStudent') !== -1 ||
                                            cookie.indexOf('.ASPXAUTH') !== -1 ||
                                            cookie.indexOf('ASP.NET_SessionId') !== -1
                                        ); 
                                    })();""".trimIndent()
                                ) { result ->
                                    ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "JS check logged-in elements: result=$result")
                                    if (result == "true") {
                                        detectedLoggedIn = true
                                        captureAndComplete()
                                    }
                                }
                            }
                        }

                        loadUrl("https://bars.mpei.ru/bars_web/")
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
