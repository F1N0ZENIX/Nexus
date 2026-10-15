package com.f1n0.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
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

private val Bg = Color(0xFF080A09)
private val Panel = Color(0xFF131715)
private val Lime = Color(0xFFC8FF45)
private val Muted = Color(0xFF9BA69E)
private val Ink = Color(0xFFEAF0EB)

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
        setContent {
            var config by remember { mutableStateOf(loadConfig()) }
            var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
            var prompt by remember { mutableStateOf("") }
            var streaming by remember { mutableStateOf(false) }
            var liveAnswer by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var settingsOpen by remember { mutableStateOf(false) }
            var showModels by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { db.messages().observeAll().collect { messages = it } }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Lime, onPrimary = Bg, background = Bg, surface = Panel,
                onSurface = Ink, onBackground = Ink, secondary = Lime
            )) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(36.dp).background(Lime, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text("N", color = Bg, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("NEXUS", fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 17.sp)
                                Text("AI WORKSPACE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Default.Settings, "Configurações", tint = Ink)
                            }
                            IconButton(onClick = { scope.launch { db.messages().clear() } }) {
                                Icon(Icons.Default.Delete, "Limpar conversa", tint = Muted)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(config.provider, color = Lime, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(config.model, color = Ink, fontSize = 13.sp, maxLines = 1)
                            }
                            TextButton(onClick = { showModels = true }) { Text("TROCAR", color = Lime, fontSize = 11.sp) }
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
                            if (streaming && liveAnswer.isBlank()) item { Text("Pensando…", color = Lime, modifier = Modifier.padding(12.dp)) }
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
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Lime),
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
                                            db.messages().insert(ChatMessage(role = "user", content = send))
                                            try {
                                                val result = AIClient.stream(config, messages, send) { liveAnswer += it }
                                                db.messages().insert(ChatMessage(role = "assistant", content = result))
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
                                            db.messages().insert(ChatMessage(role = "user", content = send))
                                            try {
                                                val result = AIClient.stream(config, messages, send) { liveAnswer += it }
                                                db.messages().insert(ChatMessage(role = "assistant", content = result))
                                            } catch (e: Exception) { error = e.message ?: "Falha na conexão." }
                                            finally { streaming = false; liveAnswer = "" }
                                        }
                                    }
                                },
                                enabled = !streaming && prompt.isNotBlank(),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Bg)
                            ) { Icon(Icons.Default.ArrowUpward, "Enviar") }
                        }
                    }
                }
            }

            if (showModels) AlertDialog(
                onDismissRequest = { showModels = false },
                containerColor = Panel, title = { Text("Modelos", color = Ink) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Triple("Groq", "openai/gpt-oss-20b", "https://api.groq.com/openai/v1"),
                            Triple("Groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1"),
                            Triple("Groq", "qwen/qwen3-32b", "https://api.groq.com/openai/v1"),
                            Triple("Groq", "moonshotai/kimi-k2-instruct", "https://api.groq.com/openai/v1"),
                            Triple("Anthropic", "claude-sonnet-4-5", "https://api.anthropic.com/v1"),
                            Triple("Anthropic", "claude-haiku-4-5", "https://api.anthropic.com/v1"),
                            Triple("Anthropic", "claude-opus-4-1", "https://api.anthropic.com/v1"),
                            Triple("OpenAI", "gpt-4o-mini", "https://api.openai.com/v1"),
                            Triple("Local / OpenAI-compatible", "llama3.2", "http://10.0.2.2:11434/v1")
                        ).forEach { item ->
                            TextButton(onClick = {
                                config = config.copy(
                                    provider = item.first, model = item.second, baseUrl = item.third,
                                    apiKey = prefs.getString("key_${item.first}", "") ?: ""
                                )
                                saveConfig(config); showModels = false
                            }) { Text("${item.first}  ·  ${item.second}", color = if (config.model == item.second) Lime else Ink) }
                        }
                        Text("Modelos locais exigem um endpoint compatível, como Ollama ou LM Studio.", color = Muted, fontSize = 11.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showModels = false }) { Text("Fechar", color = Lime) } }
            )

            if (settingsOpen) SettingsDialog(
                initial = config,
                onDismiss = { settingsOpen = false },
                onSave = { config = it; saveConfig(it); settingsOpen = false },
                keyForProvider = { name -> prefs.getString("key_$name", "") ?: "" }
            )
        }
    }

    private fun loadConfig(): ProviderConfig = ProviderConfig(
        provider = prefs.getString("provider", "Groq") ?: "Groq",
        apiKey = prefs.getString("key_${prefs.getString("provider", "Groq") ?: "Groq"}", prefs.getString("apiKey", "")) ?: "",
        baseUrl = prefs.getString("baseUrl", "https://api.groq.com/openai/v1") ?: "",
        model = prefs.getString("model", "openai/gpt-oss-20b") ?: "",
        claudeCodeUrl = prefs.getString("claudeCodeUrl", "") ?: ""
    )

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
        Text(if (user) "VOCÊ" else "NEXUS", color = if (user) Muted else Lime, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Surface(color = if (user) Color(0xFF263022) else Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(if (user) .9f else 1f)) {
            Text(message.content, Modifier.padding(14.dp), color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun SettingsDialog(initial: ProviderConfig, onDismiss: () -> Unit, onSave: (ProviderConfig) -> Unit, keyForProvider: (String) -> String) {
    var provider by remember { mutableStateOf(initial.provider) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var base by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var codeUrl by remember { mutableStateOf(initial.claudeCodeUrl) }
    var keyVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Configurações", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PROVEDOR", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(provider, color = Ink) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Anthropic", "Groq", "OpenAI", "OpenAI-compatible", "Local / OpenAI-compatible", "Claude Code").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = {
                                provider = it; key = keyForProvider(it); expanded = false
                                when (it) {
                                    "Anthropic" -> { base = "https://api.anthropic.com/v1"; if (model.startsWith("gpt")) model = "claude-sonnet-4-5" }
                                    "Groq" -> { base = "https://api.groq.com/openai/v1"; model = "openai/gpt-oss-20b" }
                                    "OpenAI" -> { base = "https://api.openai.com/v1"; model = "gpt-4o-mini" }
                                    "Local / OpenAI-compatible" -> { base = "http://10.0.2.2:11434/v1"; model = "llama3.2" }
                                }
                            })
                        }
                    }
                }
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("ID do modelo") }, singleLine = true)
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("Chave API (opcional para local)") },
                    singleLine = true, visualTransformation = if (keyVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { keyVisible = !keyVisible }) { Text(if (keyVisible) "Ocultar" else "Ver") } }
                )
                OutlinedTextField(value = base, onValueChange = { base = it }, label = { Text("Endpoint base") }, singleLine = true)
                OutlinedTextField(value = codeUrl, onValueChange = { codeUrl = it }, label = { Text("Claude Code backend URL") }, singleLine = true)
                Text("Chaves guardadas em armazenamento cifrado do Android. Nunca partilhe seu APK com chaves já configuradas.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ProviderConfig(provider, key.trim(), base.trim(), model.trim(), codeUrl.trim())) }) {
                Text("Salvar", color = Lime)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } }
    )
}
