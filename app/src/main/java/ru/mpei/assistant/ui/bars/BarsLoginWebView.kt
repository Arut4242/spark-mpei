package ru.mpei.assistant.ui.bars

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
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
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var detectedLoggedIn by remember { mutableStateOf(false) }

    fun captureAndComplete() {
        val cookieManager = CookieManager.getInstance()
        val c1 = cookieManager.getCookie("https://bars.mpei.ru/bars_web/")
        val c2 = cookieManager.getCookie("https://bars.mpei.ru/")
        val currentUrl = webViewInstance?.url
        ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "captureAndComplete: url=$currentUrl, c1 len=${c1?.length ?: 0}, c2 len=${c2?.length ?: 0}")
        val combined = listOfNotNull(c1, c2).distinct().joinToString("; ")
        if (combined.isNotBlank()) {
            ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "Cookies captured successfully! Total len=${combined.length}, url=$currentUrl")
            onLoginSuccess(combined, currentUrl)
        } else {
            ru.mpei.assistant.util.AppLogger.w("BARS_LOGIN", "captureAndComplete called, but cookies are empty!")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вход в БАРС МЭИ") },
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
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "WebView pageStarted: $url")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                ru.mpei.assistant.util.AppLogger.i("BARS_LOGIN", "WebView pageFinished: $url")

                                // Check if page contains elements of logged-in portal
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
                                        // Auto-capture after brief delay
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
