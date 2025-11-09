package com.minecraft.bedrockserver.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class PhpManager(private val context: Context) {
    private val TAG = "PhpManager"
    private val client = OkHttpClient()
    
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
                    return@withContext true
                }
                
                onProgress("📦 Preparando PHP para Android...")
                
                val arch = getDeviceArchitecture()
                onProgress("🔍 Arquitetura: $arch")
                
                if (!phpDir.exists()) {
                    phpDir.mkdirs()
                }
                
                onProgress("")
                onProgress("⏬ Baixando PHP 8.2 (~15MB)...")
                onProgress("Isso pode levar alguns minutos...")
                onProgress("")
                
                val phpUrl = when (arch) {
                    "arm64-v8a" -> "https://github.com/lz233/php-build/releases/download/8.2.0/php-8.2.0-aarch64-linux-android.tar.xz"
                    "armeabi-v7a" -> "https://github.com/lz233/php-build/releases/download/8.2.0/php-8.2.0-armv7a-linux-android.tar.xz"
                    else -> {
                        onProgress("❌ Arquitetura não suportada: $arch")
                        return@withContext false
                    }
                }
                
                val phpArchive = File(serverDir, "php.tar.xz")
                
                val downloaded = downloadFile(phpUrl, phpArchive) { progress ->
                    onProgress("⏬ Download: $progress%")
                }
                
                if (!downloaded) {
                    onProgress("")
                    onProgress("❌ Falha no download do PHP")
                    onProgress("")
                    onProgress("ALTERNATIVA: Use Termux")
                    onProgress("1. Instale: https://f-droid.org/packages/com.termux")
                    onProgress("2. Execute: pkg install php")
                    onProgress("3. Reinicie este app")
                    return@withContext false
                }
                
                onProgress("")
                onProgress("📂 Extraindo arquivos...")
                
                extractTarXz(phpArchive, phpDir)
                
                phpArchive.delete()
                
                phpBinary.setExecutable(true, false)
                
                onProgress("")
                onProgress("✅ PHP instalado com sucesso!")
                
                val version = getPhpVersion()
                if (version != null) {
                    onProgress("📌 Versão: $version")
                }
                
                onProgress("")
                
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao instalar PHP", e)
                onProgress("❌ Erro: ${e.message}")
                return@withContext false
            }
        }
    }
    
    private fun isPhpInstalled(): Boolean {
        return phpBinary.exists() && phpBinary.canExecute()
    }
    
    fun getPhpVersion(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(phpBinary.absolutePath, "-v"))
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
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext false
                }
                
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                
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
                        onProgress(progress)
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
    
    private fun extractTarXz(archive: File, destDir: File) {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("tar", "-xJf", archive.absolutePath, "-C", destDir.absolutePath)
            )
            
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val errorReader = process.errorStream.bufferedReader()
                val error = errorReader.readText()
                Log.e(TAG, "Erro ao extrair: $error")
                
                extractWithJava(archive, destDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no tar", e)
            extractWithJava(archive, destDir)
        }
    }
    
    private fun extractWithJava(archive: File, destDir: File) {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "cd ${destDir.absolutePath} && xz -d < ${archive.absolutePath} | tar -x")
            )
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Erro na extração Java", e)
        }
    }
}
