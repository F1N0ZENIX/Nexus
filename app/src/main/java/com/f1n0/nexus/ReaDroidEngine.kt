package com.f1n0.nexus

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.DexFileFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Offline APK extraction and DEX-to-Smali conversion. */
internal object ReaDroidEngine {
    suspend fun buildWorkspace(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "readroid-workspace").apply { deleteRecursively(); mkdirs() }
        val source = File(root, "original.apk")
        context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { input.copyTo(it) } }
            ?: error("Não foi possível ler o APK.")
        val extracted = File(root, "files").apply { mkdirs() }
        val dexFiles = mutableListOf<File>()
        ZipInputStream(FileInputStream(source)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(extracted, entry.name).canonicalFile
                require(out.path.startsWith(extracted.canonicalPath + File.separator) || out == extracted.canonicalFile) {
                    "O APK contém um caminho de arquivo inválido."
                }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { target -> zip.copyTo(target) }
                    if (entry.name.matches(Regex("classes([0-9]+)?\\.dex"))) dexFiles.add(out)
                }
                zip.closeEntry()
            }
        }
        if (dexFiles.isEmpty()) error("Nenhum classes.dex foi encontrado neste APK.")
        val smaliRoot = File(root, "decompiled-smali").apply { mkdirs() }
        dexFiles.forEachIndexed { index, dex ->
            val output = File(smaliRoot, "dex${index + 1}").apply { mkdirs() }
            val dexFile = DexFileFactory.loadDexFile(dex.absolutePath, Opcodes.getDefault())
            val options = BaksmaliOptions().apply { apiLevel = 35 }
            val ok = Baksmali.disassembleDexFile(dexFile, output.absolutePath, Runtime.getRuntime().availableProcessors().coerceIn(1, 4), options)
            if (!ok) error("Não foi possível descompilar ${dex.name}.")
        }
        File(root, "LEIA-ME.txt").writeText(
            "Nexus AI / ReaDroid - workspace offline\n\n" +
            "files/: arquivos extraídos do APK original.\n" +
            "decompiled-smali/: DEX convertido para Smali legível.\n" +
            "original.apk: cópia do APK analisado.\n\n" +
            "Smali não é o código-fonte original Java/Kotlin. Recursos binários e AndroidManifest.xml podem continuar em formato compilado.\n" +
            "Modificar arquivos extraídos não recompila nem assina automaticamente um APK.\n"
        )
        val archive = File(context.cacheDir, "ReaDroid-workspace.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            root.walkTopDown().filter { it.isFile && it != archive }.forEach { file ->
                val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        archive
    }
}
