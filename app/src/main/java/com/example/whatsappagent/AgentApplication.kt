package com.example.whatsappagent

import android.app.Application
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.worker.WorkManagerHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AgentApplication : Application() {

    private val BASE_URL = BuildConfig.BACKEND_BASE_URL

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    val apiService: AgentApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.APP_API_TOKEN}")
                    .addHeader("ngrok-skip-browser-warning", "1")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(AgentApiService::class.java)
    }

    val repository: AgentRepository by lazy {
        AgentRepository(apiService, database)
    }

    override fun onCreate() {
        super.onCreate()
        WorkManagerHelper.scheduleEventRecipientPolling(this)
    }
}
