package com.motogp.fantasy.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.motogp.fantasy.BuildConfig
import com.motogp.fantasy.data.remote.MotorsportApi
import com.motogp.fantasy.data.remote.SportsDbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @Named("rapidApiClient")
    fun rapidApiClient(): OkHttpClient = baseClientBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-RapidAPI-Host", "motorsportapi.p.rapidapi.com")
                .apply {
                    if (BuildConfig.RAPIDAPI_KEY.isNotBlank()) {
                        addHeader("X-RapidAPI-Key", BuildConfig.RAPIDAPI_KEY)
                    }
                }
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides @Singleton @Named("sportsDbClient")
    fun sportsDbClient(): OkHttpClient = baseClientBuilder().build()

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)

    @Provides @Singleton
    fun motorsportApi(@Named("rapidApiClient") client: OkHttpClient): MotorsportApi = Retrofit.Builder()
        .baseUrl("https://motorsportapi.p.rapidapi.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MotorsportApi::class.java)

    @Provides @Singleton
    fun sportsDbApi(@Named("sportsDbClient") client: OkHttpClient): SportsDbApi = Retrofit.Builder()
        .baseUrl("https://www.thesportsdb.com/api/v1/json/3/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SportsDbApi::class.java)

    @Provides @Singleton fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    @Provides @Singleton fun messaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
    @Provides @Singleton fun storage(): FirebaseStorage = FirebaseStorage.getInstance()
}
