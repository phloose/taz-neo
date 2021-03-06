package de.taz.app.android.singletons

import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.download.CONCURRENT_DOWNLOAD_LIMIT
import okhttp3.*
import java.util.concurrent.TimeUnit

@Mockable
object OkHttp : ViewModel() {

    val client: OkHttpClient

    init {
        val builder = OkHttpClient
            .Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().also { it.maxRequestsPerHost = CONCURRENT_DOWNLOAD_LIMIT })

        // disallow cleartext connections if not testing
        try {
            Class.forName("org.junit.Test")
            builder.connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
        } catch (e: ClassNotFoundException) {
            builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        }

        client = builder.build()
    }
}
