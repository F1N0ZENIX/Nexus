package com.f1n0.nexus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ProviderConfig(
    val provider: String = "Groq",
    val apiKey: String = "",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "openai/gpt-oss-20b",
    val claudeCodeUrl: String = ""
)

object AIClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun stream(
        config: ProviderConfig,
        history: List<ChatMessage>,
        prompt: String,
        onText: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        val isClaude = config.provider.equals("Anthropic", true)
        val isClaudeCode = config.provider.equals("Claude Code", true)
        val url = when {
            isClaudeCode -> config.claudeCodeUrl.trimEnd('/') + "/api/claude-code"
            isClaude -> if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
            else -> if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "Configure uma URL válida nas configurações."
        }
        val messages = JSONArray()
        history.filter { it.role == "user" || it.role == "assistant" }.takeLast(24).forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val body = when {
            isClaudeCode -> JSONObject().put("prompt", prompt).put("model", config.model)
            isClaude -> JSONObject()
                .put("model", config.model)
                .put("max_tokens", 4096)
                .put("stream", true)
                .put("messages", messages)
            else -> JSONObject()
                .put("model", config.model)
                .put("stream", true)
                .put("messages", messages)
        }
        val builder = Request.Builder().url(url)
            .post(body.toString().toRequestBody(jsonType))
            .header("Accept", if (isClaudeCode) "application/json" else "text/event-stream")
        if (config.apiKey.isNotBlank()) {
            if (isClaude) builder.header("x-api-key", config.apiKey)
                .header("anthropic-version", "2023-06-01")
            else builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        val request = builder.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(600)
                error("API ${response.code}: ${detail.ifBlank { response.message }}")
            }
            val source = response.body ?: error("A API retornou uma resposta vazia.")
            if (isClaudeCode) {
                val raw = source.string()
                val obj = JSONObject(raw)
                val result = obj.optString("result", obj.optString("content", raw))
                withContext(Dispatchers.Main) { onText(result) }
                return@withContext result
            }
            val reader = source.charStream().buffered()
            val collected = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                try {
                    val event = JSONObject(data)
                    val delta = if (isClaude) {
                        if (event.optString("type") == "content_block_delta")
                            event.optJSONObject("delta")?.optString("text").orEmpty()
                        else ""
                    } else {
                        event.optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content").orEmpty()
                    }
                    if (delta.isNotEmpty()) {
                        collected.append(delta)
                        withContext(Dispatchers.Main) { onText(delta) }
                    }
                } catch (_: Exception) { }
            }
            val result = collected.toString()
            if (result.isBlank()) error("A API não enviou texto. Confira modelo, chave e endpoint.")
            result
        }
    }
}
