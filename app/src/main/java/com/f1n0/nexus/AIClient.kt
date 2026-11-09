package com.f1n0.nexus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class ProviderConfig(
    val provider: String = LOCAL_PROVIDER,
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_LOCAL_URL,
    val model: String = "llama3.2",
    val claudeCodeUrl: String = ""
)

const val LOCAL_PROVIDER = "Local / OpenAI-compatible"
const val NVIDIA_NIM_PROVIDER = "NVIDIA NIM"
const val CUSTOM_PROVIDER = "Personalizado (OpenAI-compatible)"
const val ANTHROPIC_PROVIDER = "Anthropic"
const val CLAUDE_CODE_PROVIDER = "Claude Code"
const val DEFAULT_LOCAL_URL = "http://10.0.2.2:11434/v1"
const val NVIDIA_NIM_URL = "https://integrate.api.nvidia.com/v1"
const val ANTHROPIC_URL = "https://api.anthropic.com"

object AIClient {
    // Never follow redirects: a credential must not be forwarded to a different host.
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun validHttpUrl(value: String) = try {
        URI(value).let { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() }
    } catch (_: Exception) { false }

    fun validate(config: ProviderConfig) {
        require(config.model.isNotBlank()) { "Informe o nome do modelo." }
        val endpoint = config.baseUrl.trim()
        val isClaudeCode = config.provider == CLAUDE_CODE_PROVIDER
        val effectiveEndpoint = if (isClaudeCode) config.claudeCodeUrl.trim() else endpoint
        require(validHttpUrl(effectiveEndpoint)) { "Informe um endpoint HTTP(S) válido." }
        if (config.apiKey.isNotBlank() && !isClaudeCode) {
            require(effectiveEndpoint.startsWith("https://")) { "Endpoints com chave precisam usar HTTPS." }
        }
        if (config.provider == NVIDIA_NIM_PROVIDER) {
            require(endpoint.trimEnd('/') == NVIDIA_NIM_URL) { "NVIDIA NIM usa o endpoint oficial $NVIDIA_NIM_URL." }
            require(config.apiKey.isNotBlank()) { "Informe a chave da NVIDIA NIM." }
        }
        if (config.provider == ANTHROPIC_PROVIDER) {
            require(endpoint.startsWith("https://")) { "Anthropic exige um endpoint HTTPS." }
            require(config.apiKey.isNotBlank()) { "Informe a chave da Anthropic." }
        }
    }

    suspend fun stream(
        config: ProviderConfig,
        history: List<ChatMessage>,
        prompt: String,
        onText: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        validate(config)
        val base = config.baseUrl.trimEnd('/')
        val isClaude = config.provider == ANTHROPIC_PROVIDER
        val isClaudeCode = config.provider == CLAUDE_CODE_PROVIDER
        val url = when {
            isClaudeCode -> config.claudeCodeUrl.trimEnd('/') + "/api/claude-code"
            isClaude -> if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
            else -> if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
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
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                // Do not include a potentially sensitive upstream body in errors or logs.
                error("API respondeu HTTP ${response.code}. Confira endpoint, modelo e chave.")
            }
            val source = response.body ?: error("A API retornou uma resposta vazia.")
            if (isClaudeCode) {
                val raw = source.string()
                val obj = JSONObject(raw)
                val result = obj.optString("result", obj.optString("content", raw))
                if (result.isBlank()) error("Claude Code não enviou texto.")
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
                        if (event.optString("type") == "content_block_delta") {
                            event.optJSONObject("delta")?.optString("text").orEmpty()
                        } else ""
                    } else {
                        event.optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content").orEmpty()
                    }
                    if (delta.isNotEmpty()) {
                        collected.append(delta)
                        withContext(Dispatchers.Main) { onText(delta) }
                    }
                } catch (_: Exception) { /* Ignore non-content SSE events. */ }
            }
            collected.toString().ifBlank { error("A API não enviou texto. Confira modelo, chave e endpoint.") }
        }
    }
}
