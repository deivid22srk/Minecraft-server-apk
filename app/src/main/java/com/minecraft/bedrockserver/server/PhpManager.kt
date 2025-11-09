package com.minecraft.bedrockserver.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class PhpManager(private val context: Context) {
    private val TAG = "PhpManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val serverDir: File
        get() = File(context.filesDir, "bedrock_server")
    
    private val phpDir: File
        get() = File(serverDir, "php")
    
    val phpBinary: File
        get() = File(phpDir, "bin/php")
    
    suspend fun ensurePhpInstalled(onProgress: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (isPhpInstalled()) {
                    onProgress("✅ PHP já instalado")
                    val version = getPhpVersion()
                    if (version != null) {
                        onProgress("   Versão: $version")
                    }
                    return@withContext true
                }
                
                onProgress("📦 Configurando PHP para Android...")
                onProgress("")
                
                val arch = getDeviceArchitecture()
                onProgress("🔍 Arquitetura detectada: $arch")
                onProgress("")
                
                if (!phpDir.exists()) {
                    phpDir.mkdirs()
                }
                
                val downloadSources = getPhpDownloadUrls(arch)
                
                onProgress("⏬ Iniciando download do PHP...")
                onProgress("   Tamanho estimado: ~15-20MB")
                onProgress("   Isso pode levar 2-5 minutos")
                onProgress("")
                
                var downloaded = false
                for ((index, source) in downloadSources.withIndex()) {
                    onProgress("Tentando fonte ${index + 1}/${downloadSources.size}...")
                    onProgress("📡 ${source.name}")
                    
                    val phpArchive = File(serverDir, "php.tar.gz")
                    
                    downloaded = downloadFile(source.url, phpArchive) { progress ->
                        onProgress("   Progresso: $progress%")
                    }
                    
                    if (downloaded && phpArchive.length() > 1000) {
                        onProgress("")
                        onProgress("✅ Download concluído (${phpArchive.length() / (1024 * 1024)}MB)")
                        onProgress("")
                        onProgress("📂 Extraindo arquivos...")
                        
                        val extractSuccess = extractTarGz(phpArchive, phpDir, onProgress)
                        phpArchive.delete()
                        
                        if (extractSuccess) {
                            phpBinary.setExecutable(true, false)
                            
                            if (isPhpInstalled()) {
                                onProgress("")
                                onProgress("🎉 PHP instalado com sucesso!")
                                
                                val version = getPhpVersion()
                                if (version != null) {
                                    onProgress("✅ $version")
                                }
                                onProgress("")
                                return@withContext true
                            }
                        } else {
                            onProgress("⚠️  Falha na extração, tentando próxima fonte...")
                        }
                    } else {
                        onProgress("⚠️  Download falhou, tentando próxima fonte...")
                    }
                    onProgress("")
                }
                
                onProgress("❌ Falha no download do PHP")
                onProgress("")
                onProgress("═══════════════════════════════════════")
                onProgress("SOLUÇÃO ALTERNATIVA - TERMUX")
                onProgress("═══════════════════════════════════════")
                onProgress("")
                onProgress("1️⃣  Instale o Termux:")
                onProgress("   → https://f-droid.org/packages/com.termux")
                onProgress("")
                onProgress("2️⃣  Abra o Termux e execute:")
                onProgress("   → pkg update && pkg upgrade -y")
                onProgress("   → pkg install php -y")
                onProgress("")
                onProgress("3️⃣  Volte neste app e inicie o servidor")
                onProgress("")
                onProgress("O app irá detectar o PHP do Termux")
                onProgress("automaticamente!")
                onProgress("")
                
                return@withContext false
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao instalar PHP", e)
                onProgress("❌ Erro: ${e.message}")
                onProgress("")
                onProgress("Use o Termux como alternativa (veja acima)")
                return@withContext false
            }
        }
    }
    
    private data class DownloadSource(val name: String, val url: String)
    
    private fun getPhpDownloadUrls(arch: String): List<DownloadSource> {
        return when (arch) {
            "arm64-v8a" -> listOf(
                DownloadSource(
                    "PMMP Official (GitHub)",
                    "https://jenkins.pmmp.io/job/PHP-8.2-Aggregate/lastSuccessfulBuild/artifact/PHP-8.2-Android-aarch64.tar.gz"
                ),
                DownloadSource(
                    "Static PHP Build",
                    "https://github.com/lz233/php-build/releases/download/v8.2.0/php-8.2.0-android-aarch64.tar.gz"
                ),
                DownloadSource(
                    "Alternate Mirror",
                    "https://github.com/TukangM/php8-aarch64-builds/releases/download/8.2.25/php-8.2.25-Linux-aarch64.tar.gz"
                )
            )
            else -> listOf(
                DownloadSource(
                    "Generic ARM Build",
                    "https://jenkins.pmmp.io/job/PHP-8.2-Aggregate/lastSuccessfulBuild/artifact/PHP-8.2-Android-aarch64.tar.gz"
                )
            )
        }
    }
    
    private fun isPhpInstalled(): Boolean {
        if (phpBinary.exists() && phpBinary.canExecute()) {
            return true
        }
        
        val termuxPhp = File("/data/data/com.termux/files/usr/bin/php")
        if (termuxPhp.exists() && termuxPhp.canExecute()) {
            return true
        }
        
        return false
    }
    
    fun getPhpPath(): String {
        if (phpBinary.exists() && phpBinary.canExecute()) {
            return phpBinary.absolutePath
        }
        
        val termuxPhp = File("/data/data/com.termux/files/usr/bin/php")
        if (termuxPhp.exists() && termuxPhp.canExecute()) {
            return termuxPhp.absolutePath
        }
        
        return "php"
    }
    
    fun getPhpVersion(): String? {
        return try {
            val phpPath = getPhpPath()
            val process = Runtime.getRuntime().exec(arrayOf(phpPath, "-v"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readLine()
            process.waitFor()
            output?.substringBefore("(")?.trim()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getDeviceArchitecture(): String {
        val abis = android.os.Build.SUPPORTED_ABIS
        return when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> abis.firstOrNull() ?: "unknown"
        }
    }
    
    private suspend fun downloadFile(url: String, destFile: File, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            var lastProgress = 0
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "MinecraftServer-Android/1.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code}")
                    return@withContext false
                }
                
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                
                if (contentLength < 1000) {
                    Log.e(TAG, "File too small: $contentLength bytes")
                    return@withContext false
                }
                
                val input = body.byteStream()
                val output = FileOutputStream(destFile)
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    if (contentLength > 0) {
                        val progress = (totalBytesRead * 100 / contentLength).toInt()
                        if (progress != lastProgress && progress % 5 == 0) {
                            onProgress(progress)
                            lastProgress = progress
                        }
                    }
                }
                
                output.close()
                input.close()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro no download", e)
                false
            }
        }
    }
    
    private fun extractTarGz(archive: File, destDir: File, onProgress: (String) -> Unit): Boolean {
        return try {
            onProgress("   Método 1: Usando comando tar...")
            val process = Runtime.getRuntime().exec(
                arrayOf("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            )
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                onProgress("   ✅ Extração concluída")
                true
            } else {
                val error = process.errorStream.bufferedReader().readText()
                onProgress("   ⚠️  Método 1 falhou, tentando alternativa...")
                Log.e(TAG, "Erro tar: $error")
                
                extractWithGzip(archive, destDir, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro na extração", e)
            onProgress("   ⚠️  Tentando método alternativo...")
            extractWithGzip(archive, destDir, onProgress)
        }
    }
    
    private fun extractWithGzip(archive: File, destDir: File, onProgress: (String) -> Unit): Boolean {
        return try {
            onProgress("   Método 2: Usando gzip...")
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "cd ${destDir.absolutePath} && gunzip -c ${archive.absolutePath} | tar -x")
            )
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                onProgress("   ✅ Extração concluída")
                true
            } else {
                onProgress("   ❌ Extração falhou")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro gzip", e)
            onProgress("   ❌ Extração falhou")
            false
        }
    }
}
