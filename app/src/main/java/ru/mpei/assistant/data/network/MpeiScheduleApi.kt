package ru.mpei.assistant.data.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import ru.mpei.assistant.data.model.schedule.RawLesson
import ru.mpei.assistant.data.model.schedule.SearchEntity
import java.util.concurrent.TimeUnit

interface MpeiScheduleApi {

    @GET("api/search")
    suspend fun search(
        @Query("term") term: String,
        @Query("type") type: String = "group"
    ): List<SearchEntity>

    @GET("api/schedule/group/{id}")
    suspend fun getSchedule(
        @Path("id") groupId: Long,
        @Query("start") start: String,
        @Query("finish") finish: String,
        @Query("lng") lng: Int = 1
    ): List<RawLesson>

    @GET("api/schedule/person/{id}")
    suspend fun getPersonSchedule(
        @Path("id") personId: Long,
        @Query("start") start: String,
        @Query("finish") finish: String,
        @Query("lng") lng: Int = 1
    ): List<RawLesson>

    @GET("api/schedule/auditorium/{id}")
    suspend fun getAuditoriumSchedule(
        @Path("id") audId: Long,
        @Query("start") start: String,
        @Query("finish") finish: String,
        @Query("lng") lng: Int = 1
    ): List<RawLesson>

    companion object {
        private const val BASE_URL = "https://ts.mpei.ru/"

        fun create(): MpeiScheduleApi {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MpeiScheduleApi::class.java)
        }
    }
}
