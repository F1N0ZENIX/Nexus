package com.f1n0.nexus

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Transport kept separate from the Nexus UI while the OpenCode Android port is
 * being validated. OpenCode's fork expects HTTP Basic user `opencode`, not
 * the application name (`nexus`).
 */
internal class OpenCodePortClient(
    private val endpoint: String,
    private val token: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    fun postJson(payload: String): String {
        require(endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            "OpenCode endpoint must be HTTP(S)."
        }
        require(token.isNotBlank()) { "OpenCode token is required." }

        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("Authorization", Credentials.basic("opencode", token))
            .post(payload.toRequestBody(JSON))
            .build()

        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "OpenCode returned HTTP ${response.code}." }
            response.body?.string().orEmpty()
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
