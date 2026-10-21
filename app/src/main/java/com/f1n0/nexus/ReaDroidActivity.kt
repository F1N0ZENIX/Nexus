package com.f1n0.nexus

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private val RDBg = Color(0xFF000000)
private val RDPanel = Color(0xFF101010)
private val RDRed = Color(0xFFE05252)
private val RDMuted = Color(0xFF9BA69E)

class ReaDroidActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = RDRed, background = RDBg, surface = RDPanel, onSurface = Color.White)) {
                ReaDroidPage(onBack = { finish() })
            }
        }
    }
}

private data class ApkReport(val name: String, val size: Long, val hash: String, val files: List<String>, val dex: List<String>, val libs: List<String>, val manifest: Boolean)

@Composable
private fun ReaDroidPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("Nenhum APK selecionado") }
    var report by remember { mutableStateOf<ApkReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("A análise é feita localmente no celular.") }
    var workspaceZip by remember { mutableStateOf<File?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { target ->
        val source = workspaceZip
        if (target != null && source != null) scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("Não foi possível salvar o arquivo.") }; status = "Workspace exportado com sucesso." }
            catch (e: Exception) { status = "Falha ao exportar: " + (e.message ?: "erro") }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            uri = picked
            name = displayName(context, picked)
            report = null
            status = "Arquivo selecionado. Toque em Analisar APK."
        }
    }
    Column(Modifier.fillMaxSize().background(RDBg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("READROID", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 18.sp)
                Text("APK STATIC INSPECTOR", color = RDMuted, fontSize = 10.sp)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Entenda o que há dentro de um APK.", color = Color.White, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Sem root. Sem upload do arquivo. Sem telemetria.", color = RDMuted, fontSize = 13.sp)
            }
            item {
                Surface(color = RDPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ARQUIVO", color = RDRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FolderOpen, null, tint = RDRed)
                            Spacer(Modifier.width(8.dp))
                            Text("Selecionar APK")
                        }
                        Button(
                            onClick = {
                                val selected = uri
                                if (selected == null) status = "Selecione um APK primeiro."
                                else {
                                    busy = true
                                    status = "Analisando localmente…"
                                    scope.launch {
                                        try {
                                            report = withContext(Dispatchers.IO) { inspect(context, selected, name) }
                                            status = "Análise concluída."
                                        } catch (e: Exception) {
                                            status = "Falha: " + (e.message ?: "arquivo inválido")
                                            report = null
                                        } finally { busy = false }
                                    }
                                }
                            },
                            enabled = uri != null && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RDRed, contentColor = RDBg)
                        ) { Text(if (busy) "Analisando…" else "Analisar APK", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = RDMuted, fontSize = 11.sp)
                    }
                }
            }
            item { Text("RELATÓRIO", color = RDMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
            val r = report
            if (r == null) item {
                Surface(color = RDPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("O relatório mostrará SHA-256, tamanho, manifesto, arquivos DEX, bibliotecas nativas e estrutura ZIP.", color = RDMuted, modifier = Modifier.padding(15.dp), fontSize = 12.sp)
                }
            } else {
                item {
                    Surface(color = RDPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportLine("Tamanho", sizeLabel(r.size))
                            ReportLine("Entradas ZIP", r.files.size.toString())
                            ReportLine("Manifesto", if (r.manifest) "Encontrado (AXML)" else "Não encontrado")
                            ReportLine("DEX", r.dex.size.toString())
                            ReportLine("Bibliotecas nativas", r.libs.size.toString())
                            Text("SHA-256", color = RDRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(r.hash, color = Color.White, fontSize = 10.sp)
                            TextButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("SHA-256", r.hash))
                                Toast.makeText(context, "Hash copiado", Toast.LENGTH_SHORT).show()
                            }) { Text("Copiar hash", color = RDRed) }
                        }
                    }
                }
                item { Text("ARQUIVOS DEX", color = RDMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                if (r.dex.isEmpty()) item { Text("Nenhum arquivo DEX encontrado.", color = RDMuted, fontSize = 12.sp) }
                items(r.dex) { FileItem(it) }
                item { Text("BIBLIOTECAS NATIVAS", color = RDMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                if (r.libs.isEmpty()) item { Text("Nenhuma biblioteca .so encontrada.", color = RDMuted, fontSize = 12.sp) }
                items(r.libs.take(100)) { FileItem(it) }
                item { Text("ESTRUTURA ZIP", color = RDMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                items(r.files.take(100)) { FileItem(it) }
            }
            item {
                Surface(color = RDPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("DESCOMPILAR DEX / EXPORTAR", color = RDRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Extrai os arquivos e converte classes DEX para Smali no próprio celular, sem enviar o APK à internet.", color = RDMuted, fontSize = 12.sp)
                        Button(
                            enabled = uri != null && !busy,
                            onClick = {
                                val selected = uri ?: return@Button
                                busy = true
                                status = "Extraindo e descompilando DEX para Smali…"
                                scope.launch {
                                    try {
                                        workspaceZip = ReaDroidEngine.buildWorkspace(context, selected)
                                        status = "Workspace pronto. Escolha onde salvar o ZIP."
                                        exportPicker.launch("ReaDroid-workspace.zip")
                                    } catch (e: Exception) {
                                        status = "Falha na descompilação: " + (e.message ?: "erro")
                                    } finally { busy = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RDRed, contentColor = RDBg)
                        ) { Text(if (busy) "Processando…" else "Descompilar e exportar workspace", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                Text("Esta primeira integração cobre inspeção estática básica. JADX, edição de manifesto, Apktool e assinatura ainda não estão incorporados nesta tela.", color = RDMuted, fontSize = 10.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable private fun ReportLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = RDMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun FileItem(value: String) {
    Surface(color = RDPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(value, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
    }
}
private fun displayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "arquivo.apk"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && i >= 0) name = c.getString(i) ?: name
    }
    return name
}
private fun inspect(context: Context, uri: Uri, name: String): ApkReport {
    val md = MessageDigest.getInstance("SHA-256")
    var size = 0L
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(32768)
        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n); size += n }
    } ?: error("Não foi possível abrir o arquivo.")
    val files = mutableListOf<String>()
    val dex = mutableListOf<String>()
    val libs = mutableListOf<String>()
    var manifest = false
    context.contentResolver.openInputStream(uri)?.use { raw ->
        ZipInputStream(raw).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val p = entry.name
                files.add(p)
                if (p == "AndroidManifest.xml") manifest = true
                if (p == "classes.dex" || (p.startsWith("classes") && p.endsWith(".dex"))) dex.add(p)
                if (p.startsWith("lib/") && p.endsWith(".so")) libs.add(p)
                zip.closeEntry()
            }
        }
    } ?: error("Não foi possível ler a estrutura ZIP.")
    return ApkReport(name, size, md.digest().joinToString("") { "%02x".format(it) }, files, dex, libs, manifest)
}
private fun sizeLabel(n: Long): String = if (n < 1024L * 1024L) String.format(java.util.Locale.US, "%.1f KB", n / 1024.0) else String.format(java.util.Locale.US, "%.2f MB", n / (1024.0 * 1024.0))
