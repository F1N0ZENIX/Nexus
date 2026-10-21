package com.f1n0.nexus

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF000000)
private val Panel = Color(0xFF101010)
private val AccentRed = Color(0xFFE05252)
private val Muted = Color(0xFF9BA69E)
private val Ink = Color(0xFFFFFFFF)

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
        // Remove legacy cloud Kimi credentials from older app versions.
        prefs.edit().remove("key_Kimi").apply()
        setContent {
            var config by remember { mutableStateOf(loadConfig()) }
            var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
            var sessions by remember { mutableStateOf(emptyList<ChatSession>()) }
            var activeChatId by remember { mutableStateOf("") }
            var historyOpen by remember { mutableStateOf(false) }
            var prompt by remember { mutableStateOf("") }
            var streaming by remember { mutableStateOf(false) }
            var liveAnswer by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var settingsOpen by remember { mutableStateOf(false) }
            var showModels by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                db.sessions().observeAll().collect { all ->
                    sessions = all
                    if (activeChatId.isBlank()) {
                        if (all.isNotEmpty()) activeChatId = all.first().id
                        else {
                            val id = "chat-${System.currentTimeMillis()}"
                            db.sessions().insert(ChatSession(id, "Nova conversa"))
                            activeChatId = id
                        }
                    } else if (all.isNotEmpty() && all.none { it.id == activeChatId }) activeChatId = all.first().id
                }
            }
            LaunchedEffect(activeChatId) {
                if (activeChatId.isNotBlank()) db.messages().observeChat(activeChatId).collect { messages = it }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = AccentRed, onPrimary = Bg, background = Bg, surface = Panel,
                onSurface = Ink, onBackground = Ink, secondary = AccentRed
            )) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(36.dp).background(AccentRed, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text("N", color = Bg, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("NEXUS", fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 17.sp)
                                Text("AI WORKSPACE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            TextButton(onClick = { startActivity(Intent(this@MainActivity, ReaDroidActivity::class.java)) }) {
                                Text("READROID", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { historyOpen = true }) {
                                Icon(Icons.Default.History, "Histórico de conversas", tint = Ink)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val id = "chat-${System.currentTimeMillis()}"
                                    db.sessions().insert(ChatSession(id, "Nova conversa"))
                                    activeChatId = id
                                    liveAnswer = ""; error = null
                                }
                            }) { Icon(Icons.Default.Add, "Nova conversa", tint = AccentRed) }
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Default.Settings, "Preferências", tint = Ink)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("MODELO LOCAL", color = AccentRed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(config.model, color = Ink, fontSize = 13.sp, maxLines = 1)
                            }
                            TextButton(onClick = { showModels = true }) { Text("TROCAR", color = AccentRed, fontSize = 11.sp) }
                        }
                        if (messages.isEmpty() && liveAnswer.isBlank()) {
                            Spacer(Modifier.height(42.dp))
                            Text("Sua próxima ideia começa aqui.", fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 33.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Um espaço para pensar, criar e programar com seus modelos favoritos.", color = Muted, fontSize = 14.sp)
                            Spacer(Modifier.height(24.dp))
                            listOf("Explique um conceito difícil", "Ajude a criar um aplicativo", "Revise ou escreva um código").forEach { idea ->
                                Surface(
                                    onClick = { prompt = idea },
                                    color = Panel, shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                                ) { Text(idea, Modifier.padding(16.dp), color = Ink, fontSize = 14.sp) }
                            }
                        }
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages) { msg -> MessageBubble(msg) }
                            if (liveAnswer.isNotBlank()) item { MessageBubble(ChatMessage(role = "assistant", content = liveAnswer)) }
                            if (streaming && liveAnswer.isBlank()) item { Text("Pensando…", color = AccentRed, modifier = Modifier.padding(12.dp)) }
                        }
                        error?.let {
                            Text(it, color = Color(0xFFFF8D83), fontSize = 12.sp, modifier = Modifier.padding(vertical = 5.dp))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp)
                                .background(Panel, RoundedCornerShape(20.dp)).padding(10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            BasicTextField(
                                value = prompt, onValueChange = { prompt = it },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 130.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 15.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentRed),
                                decorationBox = { inner ->
                                    Box {
                                        if (prompt.isBlank()) Text("Pergunte ao Nexus…", color = Muted, fontSize = 15.sp)
                                        inner()
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (!streaming && prompt.isNotBlank()) {
                                        val send = prompt.trim(); prompt = ""; error = null; liveAnswer = ""; streaming = true
                                        scope.launch {
                                            if (messages.isEmpty()) db.sessions().rename(activeChatId, send.take(36).ifBlank { "Nova conversa" })
                                            db.sessions().touch(activeChatId)
                                            db.messages().insert(ChatMessage(role = "user", content = send, chatId = activeChatId))
                                            try {
                                                val result = AIClient.stream(config, messages, send) { liveAnswer += it }
                                                db.messages().insert(ChatMessage(role = "assistant", content = result, chatId = activeChatId))
                                            } catch (e: Exception) {
                                                error = e.message ?: "Falha ao comunicar com o modelo."
                                            } finally { streaming = false; liveAnswer = "" }
                                        }
                                    }
                                })
                            )
                            Spacer(Modifier.width(6.dp))
                            FilledIconButton(
                                onClick = {
                                    if (!streaming && prompt.isNotBlank()) {
                                        val send = prompt.trim(); prompt = ""; error = null; liveAnswer = ""; streaming = true
                                        scope.launch {
                                            if (messages.isEmpty()) db.sessions().rename(activeChatId, send.take(36).ifBlank { "Nova conversa" })
                                            db.sessions().touch(activeChatId)
                                            db.messages().insert(ChatMessage(role = "user", content = send, chatId = activeChatId))
                                            try {
                                                val result = AIClient.stream(config, messages, send) { liveAnswer += it }
                                                db.messages().insert(ChatMessage(role = "assistant", content = result, chatId = activeChatId))
                                            } catch (e: Exception) { error = e.message ?: "Falha na conexão." }
                                            finally { streaming = false; liveAnswer = "" }
                                        }
                                    }
                                },
                                enabled = !streaming && prompt.isNotBlank(),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentRed, contentColor = Bg)
                            ) { Icon(Icons.Default.ArrowUpward, "Enviar") }
                        }
                    }
                }
            }

            if (showModels) {
                var modelSearch by remember { mutableStateOf("") }
                var modelCategory by remember { mutableStateOf("Leves") }
                                var installedModels by remember { mutableStateOf(emptyList<InstalledOllamaModel>()) }
                var modelManagerMessage by remember { mutableStateOf<String?>(null) }
                var loadingInstalled by remember { mutableStateOf(false) }
                val localEndpoint = prefs.getString("localEndpoint", "http://10.0.2.2:11434/v1") ?: "http://10.0.2.2:11434/v1"
                val localProvider = "Local / OpenAI-compatible"
                val localOptions = listOf(
                    ModelOption("Leves", "llama3.2:1b", "1–1,5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Leves", "qwen2.5:3b", "2–2,5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Leves", "deepseek-r1:1.5b", "1–1,5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Leves", "phi4-mini", "2,5–3 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Leves", "granite3.3:2b", "1,5–2 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Equilibrados", "llama3.2", "2–3 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Equilibrados", "llama3.1", "4,5–5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Equilibrados", "qwen2.5:7b", "4,5–5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Equilibrados", "qwen3:4b", "2,5–3 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Equilibrados", "mistral:7b", "4–5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Equilibrados", "mistral-nemo", "7–8 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Código", "deepseek-coder-v2:lite", "8–10 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Código", "codellama:7b", "4–5 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Código", "starcoder2:3b", "2–3 GB", "http://10.0.2.2:11434/v1"),
                    ModelOption("Kimi Local", "qwen3:4b", "2,5–3 GB · alternativa local", "http://10.0.2.2:11434/v1")
                )
                LaunchedEffect(showModels, localEndpoint) {
                    if (showModels) {
                        loadingInstalled = true
                        modelManagerMessage = null
                        try {
                            installedModels = OllamaModelManager.list(localEndpoint)
                        } catch (e: Exception) {
                            modelManagerMessage = "Não foi possível ler os modelos do Ollama. Confira o endereço e se o servidor está ligado."
                        } finally { loadingInstalled = false }
                    }
                }
                AlertDialog(
                    onDismissRequest = { showModels = false },
                    containerColor = Panel,
                    title = { Text("Modelos", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                            OutlinedTextField(
                                value = modelSearch, onValueChange = { modelSearch = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Pesquisar modelos", color = Muted) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                            )
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                listOf("Leves", "Equilibrados", "Código", "Kimi Local", "Instalados").forEach { category ->
                                    val active = modelCategory == category
                                    Surface(
                                        onClick = { modelCategory = category },
                                        color = if (active) AccentRed else Color(0xFF202020),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(category, Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                                            color = if (active) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (modelCategory == "Kimi Local") {
                                Text("EXECUÇÃO LOCAL · SEM API KIMI", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Os modelos oficiais Kimi atuais são grandes demais para rodar diretamente neste celular. Esta categoria oferece Qwen3 4B como alternativa local leve. As mensagens vão apenas ao servidor local configurado, não à API Kimi.", color = Muted, fontSize = 10.sp)
                                Spacer(Modifier.height(5.dp))
                            }
                            if (modelCategory == "Instalados") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("MODELOS NO OLLAMA", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        scope.launch {
                                            loadingInstalled = true
                                            try { installedModels = OllamaModelManager.list(localEndpoint); modelManagerMessage = null }
                                            catch (_: Exception) { modelManagerMessage = "Falha ao consultar o Ollama." }
                                            finally { loadingInstalled = false }
                                        }
                                    }) { Text("Atualizar", color = AccentRed, fontSize = 11.sp) }
                                }
                                Text("Servidor: $localEndpoint", color = Muted, fontSize = 9.sp)
                                if (loadingInstalled) Text("Consultando modelos…", color = Muted, fontSize = 12.sp)
                                if (installedModels.isEmpty() && !loadingInstalled) Text("Nenhum modelo encontrado ou servidor indisponível.", color = Muted, fontSize = 12.sp)
                                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    items(installedModels.filter { it.name.contains(modelSearch, true) }) { installed ->
                                        Surface(color = Color(0xFF171717), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(installed.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(installed.sizeLabel, color = Muted, fontSize = 10.sp)
                                                }
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        try {
                                                            OllamaModelManager.delete(localEndpoint, installed.name)
                                                            installedModels = OllamaModelManager.list(localEndpoint)
                                                            if (config.model == installed.name) {
                                                                config = config.copy(model = "llama3.2")
                                                                saveConfig(config)
                                                            }
                                                            modelManagerMessage = "${installed.name} removido do Ollama."
                                                        } catch (e: Exception) {
                                                            modelManagerMessage = "Não foi possível remover ${installed.name}."
                                                        }
                                                    }
                                                }) { Text("Desinstalar", color = AccentRed, fontSize = 10.sp) }
                                            }
                                        }
                                    }
                                }
                                modelManagerMessage?.let { Text(it, color = Muted, fontSize = 10.sp) }
                            } else {
                                val options = localOptions.filter {
                                    it.category == modelCategory &&
                                        (it.name.contains(modelSearch, true) || it.category.contains(modelSearch, true))
                                }
                                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    items(options) { item ->
                                        val selected = config.model == item.name && config.provider == localProvider
                                        Surface(
                                            onClick = {
                                                config = config.copy(provider = localProvider, model = item.name, baseUrl = (prefs.getString("localEndpoint", item.endpoint) ?: item.endpoint), apiKey = "", claudeCodeUrl = "")
                                                saveConfig(config); showModels = false
                                            },
                                            color = if (selected) Color(0xFF2B1919) else Color(0xFF171717),
                                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(item.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("${item.sizeLabel} · tamanho estimado", color = Muted, fontSize = 10.sp)
                                                }
                                                if (selected) Text("✓", color = AccentRed, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Text("Estimativas para quantizações comuns; o tamanho real depende da versão. Baixar e remover modelos é feito pelo Ollama na aba Instalados.", color = Muted, fontSize = 9.sp)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showModels = false }) { Text("Fechar", color = AccentRed) } }
                )
            }

            if (historyOpen) AlertDialog(
                onDismissRequest = { historyOpen = false },
                containerColor = Panel,
                title = { Text("Histórico de conversas", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sessions) { session ->
                            Surface(
                                onClick = { activeChatId = session.id; historyOpen = false; error = null; liveAnswer = "" },
                                color = if (session.id == activeChatId) Color(0xFF2B1919) else Color(0xFF171717),
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(session.title, color = Color.White, maxLines = 2, fontSize = 13.sp)
                                        Text(java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.updatedAt)), color = Muted, fontSize = 10.sp)
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            db.messages().deleteForChat(session.id)
                                            db.sessions().delete(session.id)
                                            if (activeChatId == session.id) activeChatId = ""
                                        }
                                    }) { Text("Excluir", color = AccentRed, fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Fechar", color = AccentRed) } },
                dismissButton = { TextButton(onClick = {
                    scope.launch {
                        val id = "chat-${System.currentTimeMillis()}"
                        db.sessions().insert(ChatSession(id, "Nova conversa"))
                        activeChatId = id; historyOpen = false; error = null; liveAnswer = ""
                    }
                }) { Text("＋ Nova conversa", color = AccentRed) } }
            )

            if (settingsOpen) SettingsDialog(
                initial = config,
                onDismiss = { settingsOpen = false },
                onSave = { config = it; prefs.edit().putString("localEndpoint", it.baseUrl).apply(); saveConfig(it); settingsOpen = false },
                keyForProvider = { name -> prefs.getString("key_$name", "") ?: "" }
            )
        }
    }

    private fun loadConfig(): ProviderConfig {
        val localProvider = "Local / OpenAI-compatible"
        val savedProvider = prefs.getString("provider", "") ?: ""
        val savedModel = prefs.getString("model", "") ?: ""
        val localModels = setOf("llama3.2:1b", "llama3.2", "llama3.1", "qwen2.5:3b", "qwen2.5:7b", "qwen3:4b",
            "deepseek-r1:1.5b", "deepseek-r1:7b", "deepseek-coder-v2:lite", "mistral:7b",
            "mistral-nemo", "gemma3:4b", "phi4-mini", "codellama:7b", "starcoder2:3b", "granite3.3:2b")
        return ProviderConfig(
            provider = localProvider,
            apiKey = "",
            baseUrl = prefs.getString("localEndpoint", "http://10.0.2.2:11434/v1") ?: "http://10.0.2.2:11434/v1",
            model = if (savedProvider == localProvider && savedModel in localModels) savedModel else "llama3.2",
            claudeCodeUrl = ""
        )
    }

    private fun saveConfig(c: ProviderConfig) {
        prefs.edit().putString("provider", c.provider).putString("apiKey", c.apiKey)
            .putString("key_${c.provider}", c.apiKey).putString("baseUrl", c.baseUrl).putString("model", c.model)
            .putString("claudeCodeUrl", c.claudeCodeUrl).apply()
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "VOCÊ" else "NEXUS", color = if (user) Muted else AccentRed, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Surface(color = if (user) Color(0xFF202020) else Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(if (user) .9f else 1f)) {
            Text(message.content, Modifier.padding(14.dp), color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun SettingsDialog(initial: ProviderConfig, onDismiss: () -> Unit, onSave: (ProviderConfig) -> Unit, keyForProvider: (String) -> String) {
    var model by remember { mutableStateOf(initial.model) }
    var endpoint by remember { mutableStateOf(initial.baseUrl.ifBlank { "http://10.0.2.2:11434/v1" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Preferências", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("MODELO LOCAL", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Nome exato do modelo") }, singleLine = true
                )
                OutlinedTextField(
                    value = endpoint, onValueChange = { endpoint = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Servidor local") }, singleLine = true
                )
                Text("Conecte a um Ollama ou LM Studio. No celular físico, use o IP local do computador, por exemplo http://192.168.1.10:11434/v1. O modelo precisa estar instalado no servidor.", color = Muted, fontSize = 11.sp)
                Text("O histórico fica salvo no armazenamento privado deste celular. Não há sincronização em nuvem.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(provider = "Local / OpenAI-compatible", apiKey = "", baseUrl = endpoint.trim(), model = model.trim(), claudeCodeUrl = "")) }) {
                Text("Salvar", color = AccentRed)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } }
    )
}


private data class ModelOption(val category: String, val name: String, val sizeLabel: String, val endpoint: String)
private data class InstalledOllamaModel(val name: String, val sizeBytes: Long) {
    val sizeLabel: String get() = String.format(java.util.Locale.US, "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
}

private object OllamaModelManager {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun apiRoot(openAiEndpoint: String): String {
        val root = openAiEndpoint.trim().trimEnd('/')
        return root.removeSuffix("/v1")
    }

    suspend fun list(openAiEndpoint: String): List<InstalledOllamaModel> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = Request.Builder().url(apiRoot(openAiEndpoint) + "/api/tags").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Ollama respondeu ${response.code}")
            val models = org.json.JSONObject(response.body?.string().orEmpty()).optJSONArray("models") ?: org.json.JSONArray()
            (0 until models.length()).mapNotNull { i ->
                val item = models.optJSONObject(i) ?: return@mapNotNull null
                InstalledOllamaModel(item.optString("name"), item.optLong("size", 0L))
            }.sortedBy { it.name }
        }
    }

    suspend fun delete(openAiEndpoint: String, modelName: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val body = org.json.JSONObject().put("name", modelName).toString().toRequestBody(jsonType)
        val request = Request.Builder().url(apiRoot(openAiEndpoint) + "/api/delete").delete(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Não foi possível remover o modelo (${response.code})")
        }
    }
}
