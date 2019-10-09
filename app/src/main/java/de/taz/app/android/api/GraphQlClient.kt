package de.taz.app.android.api

import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.TAZ_AUTH_HEADER
import de.taz.app.android.api.dto.DataDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.api.variables.Variables
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.awaitCallback
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * class to get DTOs from the [GRAPHQL_ENDPOINT]
 */
class GraphQlClient(
    private val httpClient: OkHttpClient = httpClient(),
    private val url: String = GRAPHQL_ENDPOINT,
    private val queryService: QueryService = QueryService.getInstance()
) {

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(WrapperDto::class.java)

    /**
     * function to get DTO from query
     * @param queryType - the type of the query to execute
     * @param variables - the variables to set on query
     * @return the [DataDto] generated by parsing the returned json with moshi
     */
    @Throws(JsonEncodingException::class)
    suspend fun query(queryType: QueryType, variables: Variables? = null): DataDto {
        val query = queryService.get(queryType)
        variables?.let { query.variables = variables }
        val body = query.toJson().toRequestBody("application/json".toMediaType())
        val response = awaitCallback(httpClient.newCall(
            Request.Builder().url(url).post(body).build()
        )::enqueue)

        val tmp = jsonAdapter.fromJson(response.body?.string().toString())!!
        return tmp.data
    }

}

/**
 * helper class to initialize the HttpClient
 */
private fun httpClient(): OkHttpClient {
    return OkHttpClient
        .Builder()
        .addInterceptor(AcceptHeaderInterceptor())
        .addInterceptor(AuthenticationHeaderInterceptor())
        .build()
}

/**
 * set ACCEPT headers needed by backend
 */
class AcceptHeaderInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request().newBuilder().addHeader("Accept", "application/json, */*").build())
    }
}

/**
 * set authentication header if authenticated
 */
class AuthenticationHeaderInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = AuthHelper.getInstance().token
        val request = if (token.isNotEmpty()) {
                chain.request().newBuilder().addHeader(
                    TAZ_AUTH_HEADER,
                    token
                ).build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}
