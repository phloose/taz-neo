package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.GraphQlClient
import de.taz.app.android.api.QueryType
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import io.sentry.core.Sentry
import kotlinx.coroutines.*
import okhttp3.Request
import java.util.*

const val DEFAULT_CONNECTION_CHECK_INTERVAL = 1000L
const val MAX_CONNECTION_CHECK_INTERVAL = 30000L

@Mockable
class ServerConnectionHelper private constructor(val applicationContext: Context) {

    companion object : SingletonHolder<ServerConnectionHelper, Context>(::ServerConnectionHelper)

    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val okHttpClient = OkHttp.client

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)

    val isDownloadServerReachableLiveData = MutableLiveData(true)
    val isGraphQlServerReachableLiveData = MutableLiveData(true)
    private var isDownloadServerReachableLastChecked: Long = 0L
    private var isGraphQlServerReachableLastChecked: Long = 0L

    private var backOffTimeMillis = 1000L

    var isDownloadServerReachable: Boolean
        get() = isDownloadServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isDownloadServerReachableLiveData.value != value
                    && isDownloadServerReachableLastChecked < Date().time - DEFAULT_CONNECTION_CHECK_INTERVAL
                ) {
                    isDownloadServerReachableLiveData.postValue(value)
                }
            } else {
                isDownloadServerReachableLiveData.postValue(value)
            }
        }
    var isGraphQlServerReachable: Boolean
        get() = isGraphQlServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isGraphQlServerReachableLiveData.value != value
                    && isGraphQlServerReachableLastChecked < Date().time - DEFAULT_CONNECTION_CHECK_INTERVAL
                ) {
                    isGraphQlServerReachableLiveData.postValue(value)
                }
            } else {
                isGraphQlServerReachableLiveData.postValue(value)
            }
        }

    init {
        Transformations.distinctUntilChanged(isDownloadServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkServerConnection()
                }
            }
        Transformations.distinctUntilChanged(isGraphQlServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showConnectionToServerFailedToast()
                    checkGraphQlConnection()
                }
            }
    }

    private fun checkServerConnection() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isDownloadServerReachable) {
            delay(backOffTimeMillis)
            try {
                log.debug("checking server connection")
                val serverUrl = appInfoRepository.get()?.globalBaseUrl ?: GRAPHQL_ENDPOINT
                val result = awaitCallback(
                    okHttpClient.newCall(
                        Request.Builder().url(serverUrl).build()
                    )::enqueue
                )
                val bool = result.isSuccessful || serverUrl == GRAPHQL_ENDPOINT
                if (bool) {
                    log.debug("downloadserver reached")
                    withContext(Dispatchers.Main) {
                        isDownloadServerReachableLiveData.value = bool
                    }
                    isDownloadServerReachableLastChecked = Date().time
                    backOffTimeMillis = DEFAULT_CONNECTION_CHECK_INTERVAL
                    checkGraphQlConnection()
                    break
                } else if (result.code.toString().firstOrNull() in listOf('4', '5')) {
                    backOffTimeMillis = (backOffTimeMillis * 2).coerceAtMost(
                        MAX_CONNECTION_CHECK_INTERVAL
                    )
                }
            } catch (e: Exception) {
                log.debug("could not reach download server - ${e.javaClass.name}: ${e.localizedMessage}")
                Sentry.captureException(e)
            }
        }
    }

    private fun checkGraphQlConnection() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isGraphQlServerReachable) {
            delay(backOffTimeMillis)
            try {
                log.debug("checking graph ql server connection")
                try {
                    val wrapperDto =
                        GraphQlClient.getInstance(applicationContext).query(QueryType.AppInfo)
                    if (wrapperDto?.errors?.isEmpty() == true) {
                        log.debug("graph ql server reached")
                        withContext(Dispatchers.Main) {
                            isGraphQlServerReachableLiveData.value = true
                        }
                        isGraphQlServerReachableLastChecked = Date().time
                        backOffTimeMillis = DEFAULT_CONNECTION_CHECK_INTERVAL
                        break
                    } else {
                        backOffTimeMillis = (2 * backOffTimeMillis).coerceAtMost(
                            MAX_CONNECTION_CHECK_INTERVAL
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isGraphQlServerReachableLiveData.value = false
                    }
                }
            } catch (e: Exception) {
                log.debug("could not reach download server - ${e.javaClass.name}: ${e.localizedMessage}")
                Sentry.captureException(e)
            }
        }
    }
}