package com.f1n0.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch

private val Bg = Color(0xFF120D1A)
private val Surface = Color(0xFF20172B)
private val SurfaceAlt = Color(0xFF2D213B)
private val Accent = Color(0xFFC9A7FF)
private val Muted = Color(0xFFCABFD5)
private val Ink = Color(0xFFFFF7FF)
private val SelectedSurface = Color(0xFF45305D)

private data class NvidiaPreset(val label: String, val id: String)

// IDs verified against NVIDIA's API Catalog documentation on 2026-11-09.
// Catalog availability can vary by account, region, and time; the manual field remains available.
private val NvidiaPresets = listOf(
    NvidiaPreset("Llama 3.1 8B · rápido", "meta/llama-3.1-8b-instruct"),
    NvidiaPreset("Llama 3.1 70B · geral", "meta/llama-3.1-70b-instruct"),
    NvidiaPreset("Llama 3.3 70B · multilíngue", "meta/llama-3.3-70b-instruct"),
    NvidiaPreset("Nemotron Ultra 253B · raciocínio", "nvidia/llama-3.1-nemotron-ultra-253b-v1")
)

class MainActivity : ComponentActivity() {
    private lateinit var db: NexusDatabase
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = NexusDatabase.get(this)
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        prefs = EncryptedSharedPreferences.create(
            this, "nexus_secure_settings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        // Keep legacy provider values until the migration has copied them into per-provider keys.
        // In particular, Claude Code has a separate bridge URL.
        prefs.edit().remove("key_Kimi").apply()
        migrateProviderSettings()
        setContent { NexusApp() }
    }

    @Composable private fun NexusApp() {
        var config by remember { mutableStateOf(loadConfig()) }
        var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
        var sessions by remember { mutableStateOf(emptyList<ChatSession>()) }
        var activeChatId by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        var streaming by remember { mutableStateOf(false) }
        var streamingChatId by remember { mutableStateOf("") }
        var liveAnswer by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var historyOpen by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        var modelSwitcherOpen by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        fun send() {
            if (streaming || prompt.isBlank() || activeChatId.isBlank()) return
            // The user can switch chats while the provider is responding. Keep this request
            // bound to its original conversation and configuration.
            val chatId = activeChatId
            val history = messages
            val requestConfig = config
            val text = prompt.trim(); prompt = ""; error = null; liveAnswer = ""; streaming = true; streamingChatId = chatId
            scope.launch {
                if (!db.sessions().exists(chatId)) {
                    streaming = false; streamingChatId = ""
                    return@launch
                }
                if (history.isEmpty()) db.sessions().rename(chatId, text.take(44).ifBlank { "Nova conversa" })
                db.sessions().touch(chatId)
                db.messages().insert(ChatMessage(role = "user", content = text, chatId = chatId))
                try {
                    val answer = AIClient.stream(requestConfig, history, text) {
                        if (streamingChatId == chatId) liveAnswer += it
                    }
                    // A deleted chat must not be recreated by a late provider response.
                    if (db.sessions().exists(chatId)) {
                        db.messages().insert(ChatMessage(role = "assistant", content = answer, chatId = chatId))
                    }
                } catch (e: Exception) { error = e.message ?: "Falha ao comunicar com o modelo." }
                finally { streaming = false; streamingChatId = ""; liveAnswer = "" }
            }
        }
        LaunchedEffect(Unit) {
            db.sessions().observeAll().collect { all ->
                sessions = all
                if (activeChatId.isBlank()) {
                    if (all.isNotEmpty()) activeChatId = all.first().id
                    else {
                        val id = "chat-${System.currentTimeMillis()}"
                        db.sessions().insert(ChatSession(id, "Nova conversa")); activeChatId = id
                    }
                } else if (all.isNotEmpty() && all.none { it.id == activeChatId }) activeChatId = all.first().id
            }
        }
        LaunchedEffect(activeChatId) { if (activeChatId.isNotBlank()) db.messages().observeChat(activeChatId).collect { messages = it } }

        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface, onSurface = Ink, onBackground = Ink)) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                    Header(
                        onHistory = { historyOpen = true },
                        onNew = { scope.launch { val id = "chat-${System.currentTimeMillis()}"; db.sessions().insert(ChatSession(id, "Nova conversa")); activeChatId = id; error = null; liveAnswer = "" } },
                        onSettings = { settingsOpen = true }
                    )
                    ProviderCard(
                        config = config,
                        streaming = streaming,
                        onChangeModel = { modelSwitcherOpen = true },
                        onConfigure = { settingsOpen = true }
                    )
                    if (messages.isEmpty() && (streamingChatId != activeChatId || liveAnswer.isBlank())) EmptyState { prompt = it }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(messages, key = { it.id }) { MessageBubble(it) }
                        if (streamingChatId == activeChatId && liveAnswer.isNotBlank()) item { MessageBubble(ChatMessage(role = "assistant", content = liveAnswer)) }
                        if (streamingChatId == activeChatId && streaming && liveAnswer.isBlank()) item { Text("Pensando…", color = Accent, modifier = Modifier.padding(12.dp)) }
                    }
                    error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                    Composer(prompt, streaming, { prompt = it }, ::send)
                }
            }
        }
        if (historyOpen) HistoryDialog(sessions, activeChatId, onSelect = { activeChatId = it; historyOpen = false; error = null; liveAnswer = "" }, onDelete = { id -> scope.launch { db.messages().deleteForChat(id); db.sessions().delete(id); if (activeChatId == id) activeChatId = "" } }, onNew = { scope.launch { val id = "chat-${System.currentTimeMillis()}"; db.sessions().insert(ChatSession(id, "Nova conversa")); activeChatId = id; historyOpen = false } }, onDismiss = { historyOpen = false })
        if (modelSwitcherOpen) ModelSwitcherDialog(
            initial = config,
            streaming = streaming,
            configForProvider = ::configForProvider,
            onDismiss = { modelSwitcherOpen = false },
            onApply = { provider, model ->
                config = saveModelSelection(provider, model)
                modelSwitcherOpen = false
            }
        )
        if (settingsOpen) SettingsDialog(config, streaming = streaming, configForProvider = ::configForProvider, onDismiss = { settingsOpen = false }, onSave = { config = it; saveConfig(it); settingsOpen = false })
    }

    private fun providerId(provider: String) = when (provider) {
        LOCAL_PROVIDER -> "local"
        NVIDIA_NIM_PROVIDER -> "nvidia_nim"
        CUSTOM_PROVIDER -> "custom"
        ANTHROPIC_PROVIDER -> "anthropic"
        CLAUDE_CODE_PROVIDER -> "claude_code"
        else -> "local"
    }

    private fun isKnownProvider(provider: String) = provider in setOf(
        LOCAL_PROVIDER, NVIDIA_NIM_PROVIDER, CUSTOM_PROVIDER, ANTHROPIC_PROVIDER, CLAUDE_CODE_PROVIDER
    )
    private fun modelKey(provider: String) = "provider_${providerId(provider)}_model"
    private fun endpointKey(provider: String) = "provider_${providerId(provider)}_endpoint"
    private fun apiKeyKey(provider: String) = "provider_${providerId(provider)}_api_key"
    private fun claudeCodeUrlKey(provider: String) = "provider_${providerId(provider)}_claude_code_url"
    private fun defaultModel(provider: String) = if (provider == LOCAL_PROVIDER) "llama3.2" else ""
    private fun defaultEndpoint(provider: String) = when (provider) {
        LOCAL_PROVIDER -> DEFAULT_LOCAL_URL
        NVIDIA_NIM_PROVIDER -> NVIDIA_NIM_URL
        ANTHROPIC_PROVIDER -> ANTHROPIC_URL
        else -> ""
    }

    /** Copies former shared and named settings into per-provider keys without removing other providers' values. */
    private fun migrateProviderSettings() {
        if (prefs.getBoolean("provider_settings_v3_migrated", false)) return
        val oldProvider = prefs.getString("provider", LOCAL_PROVIDER) ?: LOCAL_PROVIDER
        val provider = if (isKnownProvider(oldProvider)) oldProvider else LOCAL_PROVIDER
        val editor = prefs.edit()
        if (!prefs.contains(modelKey(provider))) prefs.getString("model", null)?.let { editor.putString(modelKey(provider), it) }
        if (provider != NVIDIA_NIM_PROVIDER && !prefs.contains(endpointKey(provider))) {
            val legacyEndpointKey = if (provider == LOCAL_PROVIDER) "localEndpoint" else "baseUrl"
            prefs.getString(legacyEndpointKey, null)?.let { editor.putString(endpointKey(provider), it) }
        }
        val providers = listOf(LOCAL_PROVIDER, NVIDIA_NIM_PROVIDER, CUSTOM_PROVIDER, ANTHROPIC_PROVIDER, CLAUDE_CODE_PROVIDER)
        providers.forEach { candidate ->
            val oldNamedKey = "key_$candidate"
            if (candidate != LOCAL_PROVIDER && !prefs.contains(apiKeyKey(candidate)) && prefs.contains(oldNamedKey)) {
                prefs.getString(oldNamedKey, "")?.let { editor.putString(apiKeyKey(candidate), it) }
            }
        }
        val selectedAlreadyHasKey = prefs.contains(apiKeyKey(provider)) || prefs.contains("key_$provider")
        if (provider != LOCAL_PROVIDER && !selectedAlreadyHasKey && prefs.contains("apiKey")) {
            prefs.getString("apiKey", "")?.let { editor.putString(apiKeyKey(provider), it) }
        }
        if (!prefs.contains(claudeCodeUrlKey(CLAUDE_CODE_PROVIDER))) {
            prefs.getString("claudeCodeUrl", null)?.let { editor.putString(claudeCodeUrlKey(CLAUDE_CODE_PROVIDER), it) }
        }
        editor.putString("provider", provider)
            .putBoolean("provider_settings_v3_migrated", true)
            .apply()
    }

    private fun configForProvider(provider: String): ProviderConfig {
        val safeProvider = if (isKnownProvider(provider)) provider else LOCAL_PROVIDER
        val endpoint = if (safeProvider == NVIDIA_NIM_PROVIDER) NVIDIA_NIM_URL
        else prefs.getString(endpointKey(safeProvider), defaultEndpoint(safeProvider)) ?: defaultEndpoint(safeProvider)
        return ProviderConfig(
            provider = safeProvider,
            apiKey = if (safeProvider == LOCAL_PROVIDER) "" else prefs.getString(apiKeyKey(safeProvider), "") ?: "",
            baseUrl = endpoint,
            model = prefs.getString(modelKey(safeProvider), defaultModel(safeProvider)) ?: defaultModel(safeProvider),
            claudeCodeUrl = if (safeProvider == CLAUDE_CODE_PROVIDER) {
                prefs.getString(claudeCodeUrlKey(safeProvider), prefs.getString("claudeCodeUrl", "")) ?: ""
            } else ""
        )
    }

    private fun loadConfig(): ProviderConfig {
        val provider = prefs.getString("provider", LOCAL_PROVIDER) ?: LOCAL_PROVIDER
        return configForProvider(provider)
    }

    /** Persists only the active provider and its model; credentials and endpoints stay untouched. */
    private fun saveModelSelection(provider: String, model: String): ProviderConfig {
        val safeProvider = if (isKnownProvider(provider)) provider else LOCAL_PROVIDER
        val safeModel = model.trim()
        require(safeModel.isNotBlank()) { "Escolha ou informe um modelo." }
        prefs.edit()
            .putString("provider", safeProvider)
            .putString(modelKey(safeProvider), safeModel)
            .apply()
        return configForProvider(safeProvider)
    }

    private fun saveConfig(config: ProviderConfig) {
        val safeProvider = if (isKnownProvider(config.provider)) config.provider else LOCAL_PROVIDER
        prefs.edit()
            .putString("provider", safeProvider)
            .putString(modelKey(safeProvider), config.model)
            .putString(endpointKey(safeProvider), if (safeProvider == NVIDIA_NIM_PROVIDER) NVIDIA_NIM_URL else config.baseUrl)
            .putString(apiKeyKey(safeProvider), if (safeProvider == LOCAL_PROVIDER) "" else config.apiKey)
            .putString(claudeCodeUrlKey(safeProvider), if (safeProvider == CLAUDE_CODE_PROVIDER) config.claudeCodeUrl else "")
            .apply()
    }
}

@Composable private fun Header(onHistory: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Accent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("N", color = Bg, fontWeight = FontWeight.Black, fontSize = 23.sp) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("NEXUS", fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("CHAT", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp) }
        IconButton(onClick = onHistory) { Icon(Icons.Default.History, "Histórico de conversas", tint = Ink) }
        IconButton(onClick = onNew) { Icon(Icons.Default.Add, "Nova conversa", tint = Accent) }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configurar provedor", tint = Ink) }
    }
}

@Composable private fun ProviderCard(
    config: ProviderConfig,
    streaming: Boolean,
    onChangeModel: () -> Unit,
    onConfigure: () -> Unit
) {
    Surface(color = Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(config.provider.uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(config.model.ifBlank { "Configure um modelo" }, maxLines = 1, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChangeModel, enabled = !streaming, contentPadding = PaddingValues(0.dp)) {
                    Text("TROCAR MODELO", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = onConfigure, enabled = !streaming, contentPadding = PaddingValues(0.dp)) {
                    Text("CONFIGURAR", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                if (streaming) {
                    Spacer(Modifier.weight(1f))
                    Text("Muda no próximo envio", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable private fun EmptyState(onIdea: (String) -> Unit) {
    Column(Modifier.padding(top = 34.dp)) { Text("O que vamos resolver?", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); Text("O histórico fica neste celular. Você escolhe para qual provedor cada mensagem vai.", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(22.dp)); listOf("Explique um conceito difícil", "Ajude a criar um aplicativo", "Revise este código").forEach { idea -> Surface(color = Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable { onIdea(idea) }) { Text(idea, Modifier.padding(16.dp), fontSize = 14.sp) } } }
}

@Composable private fun Composer(value: String, streaming: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp).background(Surface, RoundedCornerShape(22.dp)).padding(10.dp), verticalAlignment = Alignment.Bottom) {
        BasicTextField(value, onChange, Modifier.weight(1f).heightIn(min = 44.dp, max = 130.dp).padding(horizontal = 8.dp, vertical = 12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 16.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent), decorationBox = { inner -> Box { if (value.isBlank()) Text("Pergunte ao Nexus…", color = Muted, fontSize = 16.sp); inner() } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }))
        Spacer(Modifier.width(6.dp)); FilledIconButton(onClick = onSend, enabled = !streaming && value.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = Bg)) { Icon(Icons.Default.ArrowUpward, "Enviar") }
    }
}

@Composable private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) { Text(if (user) "VOCÊ" else "NEXUS", color = if (user) Muted else Accent, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Surface(color = if (user) SurfaceAlt else Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(if (user) .9f else 1f)) { Text(message.content, Modifier.padding(14.dp), color = Ink, fontSize = 15.sp, lineHeight = 22.sp) } }
}

@Composable
private fun HistoryDialog(
    sessions: List<ChatSession>, active: String, onSelect: (String) -> Unit,
    onDelete: (String) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Surface,
    title = { Text("Histórico", color = Ink, fontWeight = FontWeight.Bold) },
    text = {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                Surface(
                    color = if (session.id == active) SelectedSurface else SurfaceAlt,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(session.id) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, maxLines = 2, fontSize = 13.sp)
                            Text(
                                java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(session.updatedAt)),
                                color = Muted, fontSize = 10.sp
                            )
                        }
                        TextButton(onClick = { onDelete(session.id) }) {
                            Text("Excluir", color = Color(0xFFFFB4AB), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Accent) } },
    dismissButton = { TextButton(onClick = onNew) { Text("＋ Nova conversa", color = Accent) } }
)

@Composable
private fun ModelSwitcherDialog(
    initial: ProviderConfig,
    streaming: Boolean,
    configForProvider: (String) -> ProviderConfig,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    var provider by remember { mutableStateOf(initial.provider) }
    var model by remember { mutableStateOf(initial.model) }
    var message by remember { mutableStateOf<String?>(null) }
    fun switchProvider(next: String) {
        val saved = configForProvider(next)
        provider = saved.provider
        model = saved.model
        message = null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Trocar modelo", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "A conversa continua aqui. Só o próximo envio usa a escolha nova; endpoint e chave salvos não são alterados.",
                    color = Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
                if (streaming) Text("Há um envio em andamento; a configuração dele fica travada.", color = Muted, fontSize = 12.sp)
                ProviderChoice(LOCAL_PROVIDER, provider) { switchProvider(LOCAL_PROVIDER) }
                ProviderChoice(NVIDIA_NIM_PROVIDER, provider) { switchProvider(NVIDIA_NIM_PROVIDER) }
                ProviderChoice(CUSTOM_PROVIDER, provider) { switchProvider(CUSTOM_PROVIDER) }
                ProviderChoice(ANTHROPIC_PROVIDER, provider) { switchProvider(ANTHROPIC_PROVIDER) }
                ProviderChoice(CLAUDE_CODE_PROVIDER, provider) { switchProvider(CLAUDE_CODE_PROVIDER) }
                if (provider == NVIDIA_NIM_PROVIDER) NvidiaPresetList(model) { model = it }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Modelo (nome livre)") }
                )
                message?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (model.isBlank()) message = "Escolha ou informe um modelo."
                else onApply(provider, model)
            }, enabled = !streaming) { Text("Usar no próximo envio", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } }
    )
}

@Composable
private fun NvidiaPresetList(selectedModel: String, onSelect: (String) -> Unit) {
    Text("Modelos sugeridos pela NVIDIA", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text("A disponibilidade depende da sua conta e do catálogo atual.", color = Muted, fontSize = 11.sp)
    NvidiaPresets.forEach { preset ->
        Surface(
            color = if (selectedModel == preset.id) SelectedSurface else SurfaceAlt,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { onSelect(preset.id) }
        ) {
            Column(Modifier.padding(11.dp)) {
                Text(preset.label, color = if (selectedModel == preset.id) Accent else Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(preset.id, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun SettingsDialog(initial: ProviderConfig, streaming: Boolean, configForProvider: (String) -> ProviderConfig, onDismiss: () -> Unit, onSave: (ProviderConfig) -> Unit) {
    var provider by remember { mutableStateOf(initial.provider) }
    var endpoint by remember { mutableStateOf(initial.baseUrl) }
    var claudeCodeUrl by remember { mutableStateOf(initial.claudeCodeUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var message by remember { mutableStateOf<String?>(null) }
    fun switchProvider(next: String) {
        val saved = configForProvider(next)
        provider = saved.provider
        endpoint = saved.baseUrl
        claudeCodeUrl = saved.claudeCodeUrl
        model = saved.model
        key = saved.apiKey
        message = null
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface, title = { Text("Provedor e privacidade", color = Ink, fontWeight = FontWeight.Bold) }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("As mensagens e o histórico ficam no armazenamento privado deste celular. Ao enviar, a conversa vai somente ao endpoint abaixo; a chave fica criptografada no aparelho.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        if (streaming) Text("O envio atual usa a configuração que já foi travada; mudanças aqui valem só para o próximo envio.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        ProviderChoice(LOCAL_PROVIDER, provider) { switchProvider(LOCAL_PROVIDER) }; ProviderChoice(NVIDIA_NIM_PROVIDER, provider) { switchProvider(NVIDIA_NIM_PROVIDER) }; ProviderChoice(CUSTOM_PROVIDER, provider) { switchProvider(CUSTOM_PROVIDER) }; ProviderChoice(ANTHROPIC_PROVIDER, provider) { switchProvider(ANTHROPIC_PROVIDER) }; ProviderChoice(CLAUDE_CODE_PROVIDER, provider) { switchProvider(CLAUDE_CODE_PROVIDER) }
        if (provider == NVIDIA_NIM_PROVIDER) NvidiaPresetList(model) { model = it }
        OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Modelo (nome livre; aceita futuros IDs)") })
        if (provider == CLAUDE_CODE_PROVIDER) {
            OutlinedTextField(value = claudeCodeUrl, onValueChange = { claudeCodeUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("URL do bridge Claude Code") })
        } else {
            OutlinedTextField(value = endpoint, onValueChange = { if (provider != NVIDIA_NIM_PROVIDER) endpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = provider == NVIDIA_NIM_PROVIDER, label = { Text(if (provider == ANTHROPIC_PROVIDER) "Endpoint Anthropic" else "Endpoint OpenAI-compatible") })
        }
        if (provider != LOCAL_PROVIDER) OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text(if (provider == CLAUDE_CODE_PROVIDER) "Token do bridge" else "Chave API") })
        Text(when (provider) {
            NVIDIA_NIM_PROVIDER -> "NVIDIA NIM usa o endpoint oficial https://integrate.api.nvidia.com/v1. Escolha no catálogo da NVIDIA o modelo e informe o ID aqui."
            CUSTOM_PROVIDER -> "Use um endpoint OpenAI-compatible. Se informar chave, o endpoint precisa ser HTTPS; redirecionamentos são bloqueados para não enviar a chave a outro destino."
            ANTHROPIC_PROVIDER -> "Usa a API Messages da Anthropic, com x-api-key e streaming SSE. O endpoint precisa usar HTTPS."
            CLAUDE_CODE_PROVIDER -> "Usa o bridge Claude Code do backend. Informe a URL base do computador/servidor e o token configurado nele."
            else -> "Local aceita HTTP para Ollama/LM Studio na sua rede. Sem chave, as mensagens vão só para o servidor local configurado."
        }, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        message?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 11.sp) }
    } }, confirmButton = { TextButton(onClick = {
        val c = ProviderConfig(provider, if (provider == LOCAL_PROVIDER) "" else key.trim(), endpoint.trim(), model.trim(), claudeCodeUrl.trim())
        try { AIClient.validate(c); onSave(c) } catch (e: Exception) { message = e.message }
    }) { Text("Salvar", color = Accent) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } })
}


@Composable private fun ProviderChoice(name: String, selected: String, onClick: () -> Unit) { Surface(color = if (selected == name) SelectedSurface else SurfaceAlt, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) { Text(name, Modifier.padding(12.dp), color = if (selected == name) Accent else Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } }
