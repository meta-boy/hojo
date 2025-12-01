package wtf.anurag.hojo.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import wtf.anurag.hojo.connectivity.EpaperConnectivityManager
import wtf.anurag.hojo.data.ConnectivityRepository
import wtf.anurag.hojo.data.DefaultConnectivityRepository
import wtf.anurag.hojo.data.FileManagerRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .build()
    }

    @Provides
    @Singleton
    fun provideConnectivityRepository(
        connectivityManager: EpaperConnectivityManager,
        fileManagerRepository: FileManagerRepository
    ): ConnectivityRepository {
        return DefaultConnectivityRepository(connectivityManager, fileManagerRepository)
    }
}
