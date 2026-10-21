package com.f1n0.nexus

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.DexFileFactory
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.AlgorithmParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Offline APK extraction, DEX/Smali editing, reassembly and local APK signing. */
internal object ReaDroidEngine {
    fun workspace(context: Context) = File(context.cacheDir, "readroid-workspace")
    fun firstSmali(context: Context): File? {
        val candidates = File(workspace(context), "decompiled-smali").walkTopDown()
            .filter { it.isFile && it.extension == "smali" }.toList()
        return candidates.firstOrNull { it.name.equals("MainActivity.smali", ignoreCase = true) }
            ?: candidates.sortedBy { it.relativeTo(workspace(context)).path }.firstOrNull()
    }

    suspend fun buildWorkspace(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val root = workspace(context).apply { deleteRecursively(); mkdirs() }
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
            "A montagem local substitui DEX e assina o APK com uma chave local da Nexus. Recursos compilados continuam como no APK original.\n"
        )
        File(root, "ReaDroid-workspace.zip").also { archive -> zipDirectory(root, archive, exclude = setOf(archive.canonicalPath)) }
        File(root, "ReaDroid-workspace.zip")
    }

    suspend fun rebuildAndSign(context: Context): File = withContext(Dispatchers.IO) {
        val root = workspace(context)
        val original = File(root, "original.apk")
        val extracted = File(root, "files")
        val smaliRoot = File(root, "decompiled-smali")
        require(original.exists() && extracted.exists() && smaliRoot.exists()) { "Descompile primeiro o APK e gere o workspace." }
        val dexDirs = smaliRoot.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("dex[0-9]+")) }?.sortedBy { it.name.removePrefix("dex").toInt() }.orEmpty()
        require(dexDirs.isNotEmpty()) { "Nenhum projeto Smali encontrado." }
        val compiled = File(root, "compiled-dex").apply { deleteRecursively(); mkdirs() }
        val dexNames = mutableListOf<String>()
        dexDirs.forEachIndexed { index, dir ->
            val dexName = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
            val output = File(compiled, dexName)
            val options = SmaliOptions().apply {
                apiLevel = 35
                outputDexFile = output.absolutePath
                jobs = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                verboseErrors = true
            }
            if (!Smali.assemble(options, listOf(dir.absolutePath)) || !output.exists()) {
                error("Falha ao montar ${dir.name}. Revise o Smali gerado pela IA.")
            }
            dexNames.add(dexName)
        }
        val unsigned = File(root, "rebuilt-unsigned.apk")
        ZipOutputStream(FileOutputStream(unsigned)).use { zip ->
            extracted.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = extracted.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                if (rel.matches(Regex("classes([0-9]+)?\\.dex"))) return@forEach
                if (rel.startsWith("META-INF/") && (rel.endsWith(".RSA", true) || rel.endsWith(".DSA", true) || rel.endsWith(".EC", true) || rel.endsWith(".SF", true) || rel == "META-INF/MANIFEST.MF")) return@forEach
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            dexNames.forEach { dexName ->
                zip.putNextEntry(ZipEntry(dexName))
                File(compiled, dexName).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val signed = File(root, "ReaDroid-rebuilt-signed.apk")
        val config = signerConfig()
        ApkSigner.Builder(listOf(config))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build().sign()
        require(signed.exists() && signed.length() > 0) { "O APK assinado não foi criado." }
        signed
    }

    private fun signerConfig(): ApkSigner.SignerConfig {
        val alias = "nexus_readroid_signing_key"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val spec: AlgorithmParameterSpec = KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=Nexus ReaDroid"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000L))
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 86400000L))
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val privateKey = store.getKey(alias, null) as java.security.PrivateKey
        val certificate = store.getCertificate(alias) as java.security.cert.X509Certificate
        return ApkSigner.SignerConfig.Builder("Nexus ReaDroid", privateKey, listOf(certificate)).build()
    }

    private fun zipDirectory(root: File, archive: File, exclude: Set<String> = emptySet()) {
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            root.walkTopDown().filter { it.isFile && it.canonicalPath !in exclude }.forEach { file ->
                val rel = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
