package com.project200.data.di

import android.content.Context
import android.content.pm.PackageManager
import com.project200.data.api.ApiService
import com.project200.data.api.ChatApiService
import com.project200.data.utils.ExerciseSyncAccountGuard
import com.project200.data.utils.FcmTokenProvider
import com.project200.data.utils.LocalDateAdapter
import com.project200.data.utils.LocalDateTimeAdapter
import com.project200.data.utils.TokenAuthenticator
import com.project200.data.utils.TokenInterceptor
import com.project200.data.utils.UnitJsonAdapter
import com.project200.undabang.data.BuildConfig
import com.project200.undabang.oauth.AuthStateManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        authStateManager: AuthStateManager,
        fcmTokenProvider: FcmTokenProvider,
        exerciseSyncAccountGuard: ExerciseSyncAccountGuard,
    ): TokenInterceptor {
        return TokenInterceptor(authStateManager, fcmTokenProvider, exerciseSyncAccountGuard)
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(
        @ApplicationContext context: Context,
    ): HttpLoggingInterceptor {
        // 앱의 E2E manifest에서만 지정하며, 일반 debug의 네트워크 로그는 유지합니다
        val disableLogging =
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getBoolean("com.project200.undabang.DISABLE_HTTP_LOGGING", false) == true
        return HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG && !disableLogging) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(Unit::class.java, UnitJsonAdapter())
            .add(LocalDateAdapter())
            .add(LocalDateTimeAdapter())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenAuthenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor,
        tokenInterceptor: TokenInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .authenticator(tokenAuthenticator)
            .addInterceptor { chain ->
                if (chain.request().tag(com.project200.data.utils.ExerciseSyncAccount::class.java) == null) {
                    loggingInterceptor.intercept(chain)
                } else {
                    chain.proceed(chain.request())
                }
            }
            .addInterceptor(tokenInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        val baseUrl =
            if (BuildConfig.DEBUG) {
                "https://api.undabang.site/dev/"
            } else {
                "https://api.undabang.site/"
            }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideChatApiService(retrofit: Retrofit): ChatApiService {
        return retrofit.create(ChatApiService::class.java)
    }
}
