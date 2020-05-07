package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.squareup.moshi.JsonEncodingException
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.TAZ_AUTH_HEADER
import de.taz.app.android.api.dto.DataDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.api.variables.Variables
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import de.taz.app.android.util.okHttpClient
import de.taz.app.android.singletons.*
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * class to get DTOs from the [GRAPHQL_ENDPOINT]
 */
class GraphQlClient @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    private val okHttpClient: OkHttpClient = okHttpClient(),
    private val url: String = GRAPHQL_ENDPOINT,
    private val queryService: QueryService = QueryService.getInstance()
) {
    private constructor(applicationContext: Context) : this(
        okHttpClient = okHttpClient(applicationContext),
        queryService = QueryService.getInstance(applicationContext)
    )

    companion object : SingletonHolder<GraphQlClient, Context>(::GraphQlClient)

    /**
     * function to get DTO from query
     * @param queryType - the type of the query to execute
     * @param variables - the variables to set on query
     * @return the [DataDto] generated by parsing the returned json with moshi
     */
    @Throws(JsonEncodingException::class)
    suspend fun query(queryType: QueryType, variables: Variables? = null): DataDto? {
        return queryService.get(queryType)?.let { query ->
            variables?.let { query.variables = variables }

            log.debug(variables?.toJson().toString())

            val body = query.toJson().toRequestBody("application/json".toMediaType())
            val response = awaitCallback(
                okHttpClient.newCall(
                    Request.Builder().url(url).post(body).build()
                )::enqueue
            )
            val string = withContext(Dispatchers.IO) {
                response.body?.string().toString()
            }
            withContext(Dispatchers.IO) {
                log.debug("graphQL response: ${string.take(100)}")
                val wrapper = JsonHelper.adapter<WrapperDto>().fromJson(string)
                if (wrapper?.data == null) {
                    val errorString = wrapper?.errors.toString()
                    log.error(errorString)
                    Sentry.capture(errorString)
                }
                wrapper?.data
            }
        }
    }

}

/**
 * set ACCEPT headers needed by backend
 */
class AcceptHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(
            chain.request().newBuilder().addHeader("Accept", "application/json, */*").build()
        )
    }
}

/**
 * set authentication header if authenticated
 */
class AuthenticationHeaderInterceptor(
    private val authHelper: AuthHelper = AuthHelper.getInstance()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authHelper.token
        val originalRequest = chain.request()
        val request =
            if (originalRequest.url.toString() == GRAPHQL_ENDPOINT && token.isNotEmpty()) {
                chain.request().newBuilder().addHeader(
                    TAZ_AUTH_HEADER,
                    token
                ).build()
            } else {
                originalRequest
            }

        return chain.proceed(request)
    }
}
