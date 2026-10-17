package io.github.impca201.launcherforsimkl.data.api

import io.github.impca201.launcherforsimkl.BuildConfig
import io.github.impca201.launcherforsimkl.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiException(
    val code: Int,
    message: String,
) : IOException(message) {
    val unauthorized get() = code == 401 || code == 403
}

/**
 * Thin client for https://api.simkl.com, following SIMKL's API documentation (github.com/SIMKL/api,
 * now api.simkl.org). Every API call sends the `simkl-api-key` header plus `client_id`, and once
 * signed in the user's bearer token. data.simkl.in files (trending) need no client ID and get no
 * query parameters.
 */
class SimklApi(
    private val http: OkHttpClient,
    private val token: suspend () -> String?,
) {
    /** The app's own SIMKL client ID, baked in at build time (not a secret, per SIMKL's API rules). */
    private fun clientId(): String? = BuildConfig.SIMKL_CLIENT_ID.ifBlank { null }

    val hasClientId: Boolean get() = clientId() != null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun url(
        path: String,
        params: Map<String, String?>,
    ): HttpUrl {
        val base = if (path.startsWith("http")) path.toHttpUrl() else (BASE + path).toHttpUrl()
        // The static data files ignore query strings and SIMKL asks not to add any.
        if (base.host == DATA_HOST) return base
        return base.newBuilder().apply {
            clientId()?.let { addQueryParameter("client_id", it) }
            addQueryParameter("app-name", APP_NAME)
            addQueryParameter("app-version", BuildConfig.VERSION_NAME)
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
    }

    private suspend fun call(
        method: String,
        path: String,
        params: Map<String, String?> = emptyMap(),
        auth: Boolean = true,
    ): JsonElement {
        val url = url(path, params)
        val dataFile = url.host == DATA_HOST
        val id = clientId()
        if (!dataFile && id == null) throw ApiException(0, "This build has no SIMKL client ID")
        val bearer = if (auth && !dataFile) token() else null
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "$APP_NAME/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .apply {
                    if (!dataFile && id != null) {
                        header("simkl-api-key", id)
                        header("Content-Type", "application/json")
                    }
                    bearer?.let { header("Authorization", "Bearer $it") }
                    if (method == "POST") post("{}".toRequestBody(jsonType)) else get()
                }.build()
        val (code, text) =
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response -> response.code to response.body.string() }
            }
        if (code !in 200..299) {
            val message = runCatching { json.parseToJsonElement(text).asObject()?.let { it.str("message") ?: it.str("error") } }.getOrNull()
            throw ApiException(code, message ?: "HTTP $code")
        }
        if (text.isBlank()) return JsonNull
        // A 2xx that is not JSON (e.g. a captive portal or proxy page) is reported as an API error.
        return try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ApiException(code, "Unexpected response from SIMKL")
        }
    }

    // ---- Sign-in (PIN, made for TVs) ----------------------------------------------------------

    /** Starts a PIN sign-in: SIMKL returns a short code the user enters at simkl.com/pin. */
    suspend fun startPin(): JsonElement = call("GET", "/oauth/pin", auth = false)

    /** Checks whether the user entered [userCode]; returns the access token once they did. */
    suspend fun checkPin(userCode: String): JsonElement = call("GET", "/oauth/pin/$userCode", auth = false)

    // ---- Sync ---------------------------------------------------------------------------------

    suspend fun activities(): JsonObject? = call("POST", "/sync/activities").asObject()

    /** Everything in the user's lists, or what changed since [dateFrom]. Null JSON when the lists are empty. */
    suspend fun allItems(dateFrom: String? = null): JsonElement =
        call(
            "GET",
            "/sync/all-items/",
            mapOf("extended" to "full", "next_watch_info" to "yes", "date_from" to dateFrom),
        )

    /** The minified list (only ids) used to find items removed from the lists. */
    suspend fun allItemIds(): JsonElement = call("GET", "/sync/all-items/", mapOf("extended" to "simkl_ids_only"))

    /** Paused, partly watched episodes and movies ("Continue watching"). */
    suspend fun playback(): JsonElement = call("GET", "/sync/playback")

    // ---- Catalogue ----------------------------------------------------------------------------

    suspend fun details(
        type: MediaType,
        simklId: Long,
    ): JsonElement = call("GET", "/${type.catalogPath}/$simklId", auth = false)

    /** SIMKL's trending titles of today (a public file, credited to Simkl on screen). */
    suspend fun trending(type: MediaType): JsonElement = call("GET", "https://$DATA_HOST/discover/trending/${type.trendingPath}/today_100.json", auth = false)

    suspend fun userSettings(): JsonElement = call("POST", "/users/settings")

    companion object {
        const val BASE = "https://api.simkl.com"
        private const val DATA_HOST = "data.simkl.in"
        const val APP_NAME = "launcher-for-simkl"
    }
}
