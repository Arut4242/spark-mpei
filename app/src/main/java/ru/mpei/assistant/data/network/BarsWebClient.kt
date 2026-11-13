package ru.mpei.assistant.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.mpei.assistant.data.local.SessionStorage
import java.io.IOException
import java.util.concurrent.TimeUnit

class BarsWebClient(private val sessionStorage: SessionStorage) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchHtml(url: String = BARS_BASE_URL): Result<String> = withContext(Dispatchers.IO) {
        val parsedUri = url.toHttpUrlOrNull()
        if (parsedUri == null || !parsedUri.isHttps || !parsedUri.host.equals("bars.mpei.ru", ignoreCase = true)) {
            ru.mpei.assistant.util.AppLogger.e("BARS_WEB", "fetchHtml BLOCKED insecure/external url: $url")
            return@withContext Result.failure(SecurityException("Запрос отклонён: разрешены исключительно защищённые HTTPS-запросы к bars.mpei.ru"))
        }

        val cookies = sessionStorage.barsCookies
        if (cookies.isNullOrBlank()) {
            ru.mpei.assistant.util.AppLogger.w("BARS_WEB", "fetchHtml aborted: cookies are null or blank for url=$url")
            return@withContext Result.failure(IllegalStateException("Требуется авторизация в БАРС"))
        }

        ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchHtml: requesting $url (cookies len=${cookies.length})")

        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", cookies)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchHtml response: code=${response.code} for $url")
                if (!response.isSuccessful) {
                    ru.mpei.assistant.util.AppLogger.e("BARS_WEB", "Server error code=${response.code} for $url")
                    return@withContext Result.failure(IOException("Ошибка сервера БАРС: ${response.code}"))
                }

                val body = response.body?.string().orEmpty()
                ru.mpei.assistant.util.AppLogger.i("BARS_WEB", "fetchHtml body received: len=${body.length}")

                // Check if redirected to login page
                if (body.contains("Войти") && body.contains("password") && !body.contains("Осенний семестр")) {
                    ru.mpei.assistant.util.AppLogger.w("BARS_WEB", "Session expired: body contains login form")
                    return@withContext Result.failure(IllegalStateException("Сессия БАРС истекла. Пожалуйста, выполните вход повторно."))
                }

                Result.success(body)
            }
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("BARS_WEB", "Network error requesting $url", e)
            Result.failure(e)
        }
    }

    fun isAuthorized(): Boolean = !sessionStorage.barsCookies.isNullOrBlank()

    companion object {
        const val BARS_BASE_URL = "https://bars.mpei.ru/bars_web/"

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
