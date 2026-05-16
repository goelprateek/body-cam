package com.kriyanshtech.bodycam

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface BackendApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionResponse

    @POST("api/sessions/{sessionId}/join-token")
    suspend fun joinSession(
        @Path("sessionId") sessionId: String,
        @Body request: JoinSessionTokenRequest
    ): LiveKitTokenResponse

    @POST("api/sessions/{sessionId}/end")
    suspend fun endSession(@Path("sessionId") sessionId: String): SessionResponse

    @Multipart
    @POST("api/recordings/upload")
    suspend fun uploadRecording(
        @Part("sessionId") sessionId: RequestBody,
        @Part("durationSeconds") durationSeconds: RequestBody?,
        @Part("metadata") metadata: RequestBody?,
        @Part file: MultipartBody.Part
    ): RecordingResponse
}

object BackendApiFactory {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun createClient(tokenProvider: () -> String?): OkHttpClient {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequestsPerHost = 4
            maxRequests = 8
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor { chain ->
                val token = tokenProvider()
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun create(baseUrl: String, tokenProvider: () -> String?): BackendApi {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(createClient(tokenProvider))
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(BackendApi::class.java)
    }
}

val textPlainMediaType = "text/plain".toMediaType()
val applicationJsonMediaType = "application/json".toMediaType()
