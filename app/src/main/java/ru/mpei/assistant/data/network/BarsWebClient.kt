package ru.mpei.assistant.data.network

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.util.AppLogger

class BarsWebClient(
    val sessionStorage: SessionStorage,
    val allowedHost: String = BARS_HOST
) {

    private val barsCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (!url.isHttps || !url.host.equals(allowedHost, ignoreCase = true)) return
            if (cookies.isEmpty()) return
            val currentMap = parseCookies(sessionStorage.barsCookies, allowedHost)
                .associate { it.name to it.value }
                .toMutableMap()
            for (c in cookies) {
                currentMap[c.name] = c.value
            }
            sessionStorage.barsCookies = currentMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Строго запрещаем отправку cookie БАРС для любых сторонних доменов или незащищённых протоколов
            if (!url.isHttps || !url.host.equals(allowedHost, ignoreCase = true)) {
                return emptyList()
            }
            return parseCookies(sessionStorage.barsCookies, allowedHost)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(3, 15, TimeUnit.SECONDS))
        .retryOnConnectionFailure(true)
        .cookieJar(barsCookieJar)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (!url.isHttps || !url.host.equals(allowedHost, ignoreCase = true)) {
                throw SecurityException("Заблокирован сетевой запрос к стороннему/незащищённому хосту: ${url.host}")
            }
            val response = chain.proceed(request)
            if (response.isRedirect) {
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    val redirectUrl = location.toHttpUrlOrNull() ?: request.url.resolve(location)
                    if (redirectUrl == null || !redirectUrl.isHttps || !redirectUrl.host.equals(allowedHost, ignoreCase = true)) {
                        throw SecurityException("Заблокирован междоменный HTTP-редирект с $allowedHost на $location")
                    }
                }
            }
            response
        }
        .addNetworkInterceptor { chain ->
            val req = chain.request()
            // Проверка каждого сетевого hop'а (включая автоматические редиректы)
            if (!req.url.isHttps || !req.url.host.equals(allowedHost, ignoreCase = true)) {
                throw SecurityException("Заблокирован междоменный сетевой hop к: ${req.url.host}")
            }
            chain.proceed(req)
        }
        .build()

    suspend fun fetchHtml(url: String = BARS_BASE_URL): Result<String> = withContext(Dispatchers.IO) {
        val parsedUri = url.toHttpUrlOrNull()
        if (parsedUri == null || !parsedUri.isHttps || !parsedUri.host.equals(allowedHost, ignoreCase = true)) {
            AppLogger.e("BARS_WEB", "fetchHtml BLOCKED insecure/external url: $url")
            return@withContext Result.failure(SecurityException("Запрос отклонён: разрешены исключительно защищённые HTTPS-запросы к $allowedHost"))
        }

        val cookies = sessionStorage.barsCookies
        if (cookies.isNullOrBlank()) {
            AppLogger.w("BARS_WEB", "fetchHtml aborted: cookies are null or blank for url=$url")
            return@withContext Result.failure(IllegalStateException("Требуется авторизация в БАРС"))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                AppLogger.i("BARS_WEB", "fetchHtml (attempt $attempt/3): requesting $url (cookies len=${cookies.length})")
                client.newCall(request).execute().use { response ->
                    AppLogger.i("BARS_WEB", "fetchHtml response: code=${response.code} for $url")
                    if (!response.isSuccessful) {
                        AppLogger.e("BARS_WEB", "Server error code=${response.code} for $url")
                        return@withContext Result.failure(IOException("Ошибка сервера БАРС: ${response.code}"))
                    }

                    val body = response.body?.string().orEmpty()
                    AppLogger.i("BARS_WEB", "fetchHtml body received: len=${body.length}")

                    // Check if redirected to login page
                    if (body.contains("Войти") && body.contains("password") && !body.contains("Осенний семестр")) {
                        AppLogger.w("BARS_WEB", "Session expired: body contains login form")
                        return@withContext Result.failure(IllegalStateException("Сессия БАРС истекла. Пожалуйста, выполните вход повторно."))
                    }

                    return@withContext Result.success(body)
                }
            } catch (e: Exception) {
                lastException = e
                AppLogger.w("BARS_WEB", "Network error requesting $url (attempt $attempt/3): ${e.message}")
                if (attempt < 3 && isRetryableNetworkError(e)) {
                    delay(200L * attempt)
                    continue
                }
                break
            }
        }
        AppLogger.e("BARS_WEB", "fetchHtml failed permanently for $url", lastException)
        Result.failure(lastException ?: IOException("Неизвестная ошибка сети БАРС"))
    }

    fun isAuthorized(): Boolean = !sessionStorage.barsCookies.isNullOrBlank()

    data class PageData(val bytes: ByteArray, val contentType: String) {
        fun text(): String = bytes.toString(Charsets.UTF_8)
    }

    /** All headman requests share the same domain-isolated client and cookie jar. */
    suspend fun requestData(
        url: String,
        fields: List<Pair<String, String>>? = null,
        multipart: Boolean = false
    ): Result<PageData> = withContext(Dispatchers.IO) {
        val target = try {
            trustedUrl(url)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        if (!isAuthorized()) {
            return@withContext Result.failure(IllegalStateException("Требуется авторизация в БАРС"))
        }

        AppLogger.i("BARS_WEB", "requestData started: url=$target, fieldsCount=${fields?.size ?: 0}, multipart=$multipart")

        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val builder = Request.Builder().url(target)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,*/*;q=0.8")

                if (fields != null) {
                    val body = if (multipart) {
                        MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                            fields.forEach { (key, value) -> addFormDataPart(key, value) }
                        }.build()
                    } else {
                        FormBody.Builder().apply {
                            fields.forEach { (key, value) -> add(key, value) }
                        }.build()
                    }
                    builder.post(body).header("Referer", target.toString())
                }

                client.newCall(builder.build()).execute().use { response ->
                    AppLogger.i("BARS_WEB", "requestData response: code=${response.code} for $target (attempt $attempt)")
                    check(response.isSuccessful) { "Ошибка сервера БАРС: ${response.code}" }
                    val body = response.body ?: error("Пустой ответ БАРС")
                    val limit = 20 * 1024 * 1024
                    check(body.contentLength() <= limit) { "Отчёт превышает 20 МБ" }
                    val bytes = body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= limit) { "Отчёт превышает 20 МБ" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val page = PageData(bytes, body.contentType()?.toString().orEmpty())
                    AppLogger.i("BARS_WEB", "requestData received: len=${bytes.size}, type=${page.contentType}")
                    if (page.contentType.contains("html")) {
                        val doc = org.jsoup.Jsoup.parse(page.text())
                        check(doc.select("input[type=password]").isEmpty()) {
                            "Сессия БАРС истекла. Выполните вход повторно"
                        }
                    }
                    return@withContext Result.success(page)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                AppLogger.w("BARS_WEB", "requestData attempt $attempt/3 failed for $target: ${e.message}")
                if (attempt < 3 && isRetryableNetworkError(e)) {
                    delay(200L * attempt)
                    continue
                }
                break
            }
        }
        AppLogger.e("BARS_WEB", "requestData failed permanently for $target", lastException)
        Result.failure(lastException ?: IOException("Неизвестная ошибка сети БАРС"))
    }

    private fun isRetryableNetworkError(e: Throwable): Boolean {
        if (e is SocketException || e is EOFException) return true
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("unexpected end of stream") ||
                msg.contains("connection reset") ||
                msg.contains("broken pipe") ||
                msg.contains("connection closed")
    }

    fun trustedUrl(url: String): HttpUrl {
        val target = BARS_BASE_URL.toHttpUrlOrNull()!!.resolve(url)
        require(target != null && target.isHttps && target.host.equals(allowedHost, ignoreCase = true)) {
            "Недопустимый адрес БАРС"
        }
        return target
    }

    companion object {
        const val BARS_HOST = "bars.mpei.ru"
        const val BARS_BASE_URL = "https://$BARS_HOST/bars_web/"

        /**
         * Парсит строку cookies ("k1=v1; k2=v2") в структурированные Cookie объекты OkHttp с доменной привязкой.
         */
        fun parseCookies(raw: String?, host: String): List<Cookie> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(";").mapNotNull { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val name = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    if (name.isNotEmpty()) {
                        try {
                            Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain(host)
                                .path("/")
                                .secure()
                                .httpOnly()
                                .build()
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                } else null
            }
        }

        /**
         * Извлекает UUID студента (studentID) из переданного HTML или ссылки.
         */
        fun extractStudentIdFromHtml(html: String): String? {
            if (html.isBlank()) return null
            val idRegex = Regex("""studentID=([a-fA-F0-9\-]{36})""", RegexOption.IGNORE_CASE)
            val match = idRegex.find(html)
            if (match != null) return match.groupValues[1]

            val qidRegex = Regex("""data-q-id=["']([a-fA-F0-9\-]{36})["']""", RegexOption.IGNORE_CASE)
            val qidMatch = qidRegex.find(html)
            if (qidMatch != null) return qidMatch.groupValues[1]

            try {
                val doc = org.jsoup.Jsoup.parse(html)
                val link = doc.select("a[href*='studentID']").firstOrNull()
                if (link != null) {
                    val href = link.attr("href")
                    val m = idRegex.find(href)
                    if (m != null) return m.groupValues[1]
                }
            } catch (_: Exception) {}

            return null
        }
    }

    /**
     * Находит идентификатор студента (studentID).
     * Использует системный эндпоинт со списком учебных карточек студента.
     */
    suspend fun resolveStudentId(): String? {
        val cached = sessionStorage.barsStudentId
        if (!cached.isNullOrBlank()) return cached

        // 1. Запрос ListStudent
        ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "resolveStudentId: requesting ListStudent...")
        val listRes = fetchHtml("https://bars.mpei.ru/bars_web/ST/Student/ListStudent")
        if (listRes.isSuccess) {
            val html = listRes.getOrThrow()
            val id = extractStudentIdFromHtml(html)
            if (!id.isNullOrBlank()) {
                ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "resolveStudentId: found studentId=$id from ListStudent")
                val oldId = sessionStorage.barsStudentId
                if (!oldId.isNullOrBlank() && oldId != id) {
                    ru.mpei.assistant.util.AppLogger.w("BARS_WEB", "Detected studentId change: $oldId -> $id")
                }
                sessionStorage.barsStudentId = id
                if (sessionStorage.barsStudentUrl.isNullOrBlank()) {
                    sessionStorage.barsStudentUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=$id"
                }
                return id
            }
        }

        // 2. Резервный поиск на базовой странице
        ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "resolveStudentId: requesting BARS_BASE_URL...")
        val baseRes = fetchHtml(BARS_BASE_URL)
        if (baseRes.isSuccess) {
            val html = baseRes.getOrThrow()
            val id = extractStudentIdFromHtml(html)
            if (!id.isNullOrBlank()) {
                ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "resolveStudentId: found studentId=$id from base URL")
                val oldId = sessionStorage.barsStudentId
                if (!oldId.isNullOrBlank() && oldId != id) {
                    ru.mpei.assistant.util.AppLogger.w("BARS_WEB", "Detected studentId change: $oldId -> $id")
                }
                sessionStorage.barsStudentId = id
                if (sessionStorage.barsStudentUrl.isNullOrBlank()) {
                    sessionStorage.barsStudentUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=$id"
                }
                return id
            }
        }

        return null
    }

    suspend fun fetchGradesHtml(url: String? = null, semesterId: String? = null): Result<String> {
        if (!url.isNullOrBlank()) {
            ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml targeting explicit url: $url")
            return fetchHtml(url)
        }

        val studentId = resolveStudentId()

        // 1. Приоритетно запрашиваем прямой эндпоинт семестровой ведомости (_PartialListStudent_SemesterSheet__Mark).
        // Это частичное представление, возвращающее готовую разметку с предметами и КМ.
        if (!studentId.isNullOrBlank()) {
            val targetSem = semesterId ?: ru.mpei.assistant.data.parser.BarsAttendanceParser.estimateCurrentSemesterId()
            val partialUrl = ru.mpei.assistant.data.parser.BarsAttendanceParser.buildGradesUrl(studentId, targetSem)
            ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml: fetching direct grades partial (semId=$targetSem): $partialUrl")
            val partialRes = fetchHtml(partialUrl)
            if (partialRes.isSuccess) {
                val partialHtml = partialRes.getOrThrow()
                val parsedSubjects = ru.mpei.assistant.data.parser.BarsHtmlParser.parseSubjects(partialHtml)
                if (parsedSubjects.isNotEmpty()) {
                    ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml: SUCCESS via direct grades partial! Found ${parsedSubjects.size} subjects (semId=$targetSem)")
                    return Result.success(partialHtml)
                } else {
                    ru.mpei.assistant.util.AppLogger.w("BARS_WEB", "fetchGradesHtml: direct partial returned 0 subjects for semId=$targetSem")
                }
            }
        }

        // 2. Если прямой запрос с расчетным семестром вернул 0 предметов (или семестр в БАРС отличается),
        // запрашиваем контейнер ST_Study/Main/Main для извлечения актуального семестра из БАРС.
        val targetUrl = when {
            !sessionStorage.barsStudentUrl.isNullOrBlank() -> sessionStorage.barsStudentUrl!!
            !studentId.isNullOrBlank() -> "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=$studentId"
            else -> BARS_BASE_URL
        }

        ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml: querying study container $targetUrl (studentId=$studentId)")
        val res = fetchHtml(targetUrl)
        if (res.isSuccess) {
            val html = res.getOrThrow()

            // Проверяем, есть ли на странице уже готовые предметы (в legacy версиях БАРС)
            val directSubjects = ru.mpei.assistant.data.parser.BarsHtmlParser.parseSubjects(html)
            if (directSubjects.isNotEmpty()) {
                ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml: found ${directSubjects.size} subjects directly in container")
                if (sessionStorage.barsStudentUrl.isNullOrBlank() && targetUrl != BARS_BASE_URL) {
                    sessionStorage.barsStudentUrl = targetUrl
                }
                return Result.success(html)
            }

            // Извлекаем актуальный семестр из селектора БАРС
            if (!studentId.isNullOrBlank()) {
                val detectedSem = ru.mpei.assistant.data.parser.BarsAttendanceParser.extractSelectedSemesterId(html)
                val currentEstimate = semesterId ?: ru.mpei.assistant.data.parser.BarsAttendanceParser.estimateCurrentSemesterId()
                if (!detectedSem.isNullOrBlank() && detectedSem != currentEstimate) {
                    val detectedUrl = ru.mpei.assistant.data.parser.BarsAttendanceParser.buildGradesUrl(studentId, detectedSem)
                    ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml: trying detected semester ($detectedSem): $detectedUrl")
                    val detectedRes = fetchHtml(detectedUrl)
                    if (detectedRes.isSuccess) {
                        val detectedHtml = detectedRes.getOrThrow()
                        val detectedSubjects = ru.mpei.assistant.data.parser.BarsHtmlParser.parseSubjects(detectedHtml)
                        if (detectedSubjects.isNotEmpty()) {
                            ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchGradesHtml: SUCCESS via detected semester! Found ${detectedSubjects.size} subjects (semId=$detectedSem)")
                            return Result.success(detectedHtml)
                        }
                    }
                }
            }

            // Резервный поиск ссылок на учебную карточку внутри HTML
            val doc = org.jsoup.Jsoup.parse(html)
            val link = doc.select("a[href*='ST_Study/Main/Main']").firstOrNull()
                ?: doc.select("a[href*='studentID']").firstOrNull()

            if (link != null) {
                var href = link.attr("href").trim()
                if (href.startsWith("http://")) {
                    href = "https://" + href.removePrefix("http://")
                } else if (!href.startsWith("https://")) {
                    href = if (href.startsWith("/")) "https://bars.mpei.ru$href" else "https://bars.mpei.ru/bars_web/$href"
                }
                val checkUri = href.toHttpUrlOrNull()
                if (checkUri != null && checkUri.isHttps && checkUri.host.equals("bars.mpei.ru", ignoreCase = true)) {
                    ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "Found student study link on portal: $href. Following it...")
                    sessionStorage.barsStudentUrl = href
                    val id = extractStudentIdFromHtml(href)
                    if (!id.isNullOrBlank() && sessionStorage.barsStudentId.isNullOrBlank()) {
                        sessionStorage.barsStudentId = id
                        return fetchGradesHtml(semesterId = semesterId)
                    }
                } else {
                    ru.mpei.assistant.util.AppLogger.w("BARS_WEB", "Ignored untrusted study link: $href")
                }
            }
        }

        return res
    }
}
