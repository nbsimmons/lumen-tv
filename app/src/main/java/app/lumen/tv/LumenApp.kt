package app.lumen.tv

import android.app.Application
import app.lumen.tv.di.appModule
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LumenApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LumenApp)
            modules(appModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val lowMemory = DeviceClass.isLowMemory(context)
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .dispatcher(Dispatcher().apply { maxRequestsPerHost = IMAGE_REQUESTS_PER_HOST })
                                .addInterceptor { chain ->
                                    chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
                                }
                                .build()
                        },
                    ),
                )
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, if (lowMemory) 0.12 else 0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(if (lowMemory) 128L * 1024 * 1024 else 256L * 1024 * 1024).build() }
            .crossfade(!lowMemory)
            .build()
    }
}

private const val IMAGE_REQUESTS_PER_HOST = 10
private val USER_AGENT = "Lumen/${BuildConfig.VERSION_NAME} (Android TV)"
