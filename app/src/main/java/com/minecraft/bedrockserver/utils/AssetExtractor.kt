package com.minecraft.bedrockserver.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

object AssetExtractor {
    private const val TAG = "AssetExtractor"
    private const val EXTRACTION_VERSION = "1.6"
    private const val PREFS_NAME = "asset_extractor"
    private const val KEY_VERSION = "extracted_version"
    
    private const val PHP_BINARY_URL = "https://github.com/pmmp/PHP-Binaries/releases/download/php-8.2.19-pmmp/PHP-8.2.19-Linux-aarch64.tar.gz"
    private const val POCKETMINE_PHAR_URL = "https://github.com/pmmp/PocketMine-MP/releases/download/5.11.2/PocketMine-MP.phar"
    
    fun extractIfNeeded(context: Context): File {
        // Usar codeCacheDir para binários executáveis (Android permite execução aqui)
        // filesDir tem mount flag 'noexec' no Android 10+
        val binDir = File(context.codeCacheDir, "bedrock_bin")
        val dataDir = File(context.filesDir, "bedrock_server")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val extractedVersion = prefs.getString(KEY_VERSION, "")
        
        if (extractedVersion != EXTRACTION_VERSION || !binDir.exists() || !dataDir.exists()) {
            Log.i(TAG, "Extracting assets (version $EXTRACTION_VERSION)...")
            Log.i(TAG, "Binaries will be stored in: ${binDir.absolutePath}")
            Log.i(TAG, "Data will be stored in: ${dataDir.absolutePath}")
            extract(context, binDir, dataDir)
            prefs.edit().putString(KEY_VERSION, EXTRACTION_VERSION).apply()
            Log.i(TAG, "Assets extracted successfully")
        } else {
            Log.i(TAG, "Assets already extracted (version $extractedVersion)")
        }
        
        return dataDir
    }
    
    private fun extract(context: Context, binDir: File, dataDir: File) {
        binDir.mkdirs()
        dataDir.mkdirs()
        
        val abi = getSupportedAbi()
        Log.i(TAG, "Detected ABI: $abi")
        
        extractAssetFolder(context, "php/$abi", binDir)
        
        val phpBinary = File(binDir, "bin/php7/bin/php")
        val libPath = File(binDir, "bin/php7/lib")
        
        // Verificar se binário E bibliotecas existem
        val hasSoLibraries = libPath.exists() && 
                             libPath.walk().any { it.extension == "so" }
        
        if (!phpBinary.exists() || !hasSoLibraries) {
            if (!phpBinary.exists()) {
                Log.i(TAG, "PHP binary not found, downloading...")
            } else {
                Log.i(TAG, "PHP libraries (.so) not found, downloading complete package...")
            }
            
            try {
                // Limpar diretório antes de baixar
                if (binDir.exists()) {
                    Log.i(TAG, "Cleaning existing bin directory...")
                    binDir.deleteRecursively()
                    binDir.mkdirs()
                }
                downloadAndExtractPhpBinary(binDir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download PHP binary", e)
                throw RuntimeException("Falha ao baixar binário PHP: ${e.message}", e)
            }
        }
        
        if (phpBinary.exists()) {
            setExecutablePermissions(phpBinary)
            Log.i(TAG, "PHP binary found and set executable: ${phpBinary.absolutePath}")
        } else {
            Log.e(TAG, "PHP binary not found at: ${phpBinary.absolutePath}")
            throw RuntimeException("Binário PHP não encontrado após extração")
        }
        
        val phpBinDir = File(binDir, "bin/php7/bin")
        if (phpBinDir.exists()) {
            phpBinDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    setExecutablePermissions(file)
                }
            }
            Log.i(TAG, "PHP binaries made executable in: ${phpBinDir.absolutePath}")
        }
        
        // libPath já foi declarado acima
        if (libPath.exists()) {
            var soCount = 0
            libPath.walk().filter { it.extension == "so" }.forEach { 
                setExecutablePermissions(it)
                it.setReadable(true, false)
                soCount++
            }
            Log.i(TAG, "PHP libraries found at: ${libPath.absolutePath}")
            Log.i(TAG, "Configured $soCount .so libraries")
            
            if (soCount == 0) {
                Log.e(TAG, "ERROR: No .so libraries found in ${libPath.absolutePath}")
                Log.e(TAG, "PHP will not be able to run without shared libraries")
                throw RuntimeException("Bibliotecas .so do PHP não encontradas. Tente reinstalar o aplicativo.")
            }
        } else {
            Log.e(TAG, "Library path not found: ${libPath.absolutePath}")
            throw RuntimeException("Diretório de bibliotecas PHP não existe")
        }
        
        // Criar symlink do binário para dataDir para acesso fácil
        val phpLink = File(dataDir, "php_binary")
        try {
            if (phpLink.exists()) phpLink.delete()
            phpLink.writeText(phpBinary.absolutePath)
            Log.i(TAG, "PHP binary path saved at: ${phpLink.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not create PHP binary reference: ${e.message}")
        }
        
        val pocketMineDir = File(dataDir, "pocketmine")
        pocketMineDir.mkdirs()
        extractAssetFolder(context, "pocketmine", pocketMineDir)
        
        val pharFile = File(pocketMineDir, "PocketMine-MP.phar")
        if (!pharFile.exists()) {
            Log.i(TAG, "PocketMine-MP.phar not found, downloading...")
            try {
                downloadPocketMinePhar(pharFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download PocketMine-MP.phar", e)
                throw RuntimeException("Falha ao baixar PocketMine-MP.phar: ${e.message}", e)
            }
        }
        
        File(dataDir, "worlds").mkdirs()
        File(dataDir, "plugins").mkdirs()
        File(dataDir, "players").mkdirs()
        File(dataDir, "resource_packs").mkdirs()
        File(dataDir, "behavior_packs").mkdirs()
        
        createServerProperties(dataDir)
        createPocketMineYml(dataDir)
        createServerYml(dataDir)
    }
    
    private fun createServerYml(baseDir: File) {
        val serverYml = File(baseDir, "server.yml")
        if (!serverYml.exists()) {
            serverYml.writeText("""
                aliases:
                
                auto-report:
                  enabled: false
            """.trimIndent())
        }
    }
    
    private fun extractAssetFolder(context: Context, assetPath: String, destDir: File) {
        try {
            val files = context.assets.list(assetPath) ?: return
            
            if (files.isEmpty()) {
                copyAssetFile(context, assetPath, File(destDir.parentFile, File(assetPath).name))
            } else {
                for (file in files) {
                    if (file == "README.md" || file == ".gitkeep") continue
                    extractAssetFolder(context, "$assetPath/$file", File(destDir, file))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting $assetPath", e)
        }
    }
    
    private fun copyAssetFile(context: Context, assetPath: String, destFile: File) {
        try {
            destFile.parentFile?.mkdirs()
            
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (destFile.extension == "so" || destFile.name == "php" || destFile.extension == "sh") {
                setExecutablePermissions(destFile)
            }
            
            Log.d(TAG, "Extracted: ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying $assetPath to $destFile", e)
        }
    }
    
    private fun setExecutablePermissions(file: File) {
        try {
            // Tentar múltiplas formas de definir permissões
            file.setExecutable(true, false)
            file.setReadable(true, false)
            file.setWritable(true, false)
            
            // Tentar chmod via Runtime
            try {
                val chmodProcess = Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath))
                val exitCode = chmodProcess.waitFor()
                if (exitCode != 0) {
                    Log.w(TAG, "chmod returned non-zero: $exitCode for ${file.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "chmod via Runtime failed for ${file.name}: ${e.message}")
            }
            
            Log.d(TAG, "Set executable: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set permissions for ${file.absolutePath}: ${e.message}")
        }
    }
    
    private fun downloadAndExtractPhpBinary(baseDir: File) {
        try {
            val tempFile = File(baseDir, "php_binary.tar.gz")
            Log.i(TAG, "Downloading PHP binary from $PHP_BINARY_URL")
            Log.i(TAG, "Aguarde, baixando binário PHP (aproximadamente 50MB)...")
            
            val connection = URL(PHP_BINARY_URL).openConnection()
            connection.connect()
            val fileLength = connection.contentLength
            
            connection.getInputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    var lastProgress = 0
                    
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)
                        
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            if (progress >= lastProgress + 10) {
                                Log.i(TAG, "Download progress: $progress%")
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
            
            Log.i(TAG, "Download concluído! Extraindo PHP binary...")
            extractTarGz(tempFile, baseDir)
            tempFile.delete()
            
            val phpBinary = File(baseDir, "bin/php7/bin/php")
            if (phpBinary.exists()) {
                setExecutablePermissions(phpBinary)
                
                val binDir = File(baseDir, "bin/php7/bin")
                binDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        setExecutablePermissions(file)
                    }
                }
                
                val libPath = File(baseDir, "bin/php7/lib")
                if (libPath.exists()) {
                    libPath.walk().filter { it.extension == "so" }.forEach { 
                        setExecutablePermissions(it)
                        it.setReadable(true, false)
                    }
                    Log.i(TAG, "All .so libraries configured")
                }
                
                Log.i(TAG, "PHP binary extracted and configured successfully")
            } else {
                throw RuntimeException("PHP binary not found after extraction")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading PHP binary", e)
            throw e
        }
    }
    
    private fun downloadPocketMinePhar(pharFile: File) {
        try {
            Log.i(TAG, "Downloading PocketMine-MP.phar from $POCKETMINE_PHAR_URL")
            Log.i(TAG, "Baixando PocketMine-MP (aproximadamente 8MB)...")
            
            val connection = URL(POCKETMINE_PHAR_URL).openConnection()
            connection.connect()
            val fileLength = connection.contentLength
            
            connection.getInputStream().use { input ->
                FileOutputStream(pharFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    var lastProgress = 0
                    
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)
                        
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            if (progress >= lastProgress + 10) {
                                Log.i(TAG, "Download progress: $progress%")
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
            
            Log.i(TAG, "PocketMine-MP.phar downloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading PocketMine-MP.phar", e)
            throw e
        }
    }
    
    private fun extractTarGz(tarGzFile: File, destDir: File) {
        try {
            val process = ProcessBuilder(
                "tar", "-xzf", tarGzFile.absolutePath, "-C", destDir.absolutePath
            ).start()
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                throw RuntimeException("tar extraction failed with code $exitCode: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting tar.gz", e)
            throw e
        }
    }
    
    private fun getSupportedAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        Log.i(TAG, "Device ABIs: ${supportedAbis.joinToString(", ")}")
        
        return if (supportedAbis.contains("arm64-v8a")) {
            Log.i(TAG, "Device is ARM64 compatible")
            "arm64-v8a"
        } else {
            Log.e(TAG, "Device is not ARM64! Supported ABIs: ${supportedAbis.joinToString(", ")}")
            Log.e(TAG, "This app only supports ARM64 (64-bit) Android devices")
            Log.e(TAG, "Your device appears to be: ${supportedAbis.firstOrNull() ?: "unknown"}")
            throw UnsupportedOperationException(
                "Dispositivo não suportado. Este aplicativo requer ARM64 (64-bit).\n" +
                "Seu dispositivo é: ${supportedAbis.firstOrNull() ?: "desconhecido"}"
            )
        }
    }
    
    private fun createServerProperties(baseDir: File) {
        val propertiesFile = File(baseDir, "server.properties")
        if (!propertiesFile.exists()) {
            propertiesFile.writeText("""
                server-name=Minecraft Bedrock Server
                gamemode=survival
                difficulty=normal
                max-players=10
                server-port=19132
                white-list=off
                allow-cheats=on
                show-coordinates=true
                pvp=true
                level-name=world
                level-seed=
                default-player-permission-level=member
                texturepack-required=false
            """.trimIndent())
        }
    }
    
    private fun createPocketMineYml(baseDir: File) {
        val pocketMineYml = File(baseDir, "pocketmine.yml")
        if (!pocketMineYml.exists()) {
            pocketMineYml.writeText("""
                settings:
                  language: "eng"
                  force-language: false
                  shutdown-message: "Server closed"
                  query-plugins: true
                  deprecated-verbose: true
                  enable-profiling: false
                  profile-report-trigger: 20
                  async-workers: auto
                
                memory:
                  global-limit: 0
                  main-limit: 0
                  main-hard-limit: 1024
                  check-rate: 20
                  continuous-trigger: true
                  continuous-trigger-rate: 30
                
                network:
                  batch-threshold: 256
                  compression-level: 7
                  async-compression: true
                
                debug:
                  level: 1
                
                level-settings:
                  default-format: pmanvil
                
                chunk-sending:
                  per-tick: 4
                  max-chunks: 192
                  spawn-threshold: 56
                
                chunk-ticking:
                  per-tick: 40
                  tick-radius: 3
                  light-updates: false
                  clear-tick-list: true
                
                chunk-generation:
                  queue-size: 8
                  population-queue-size: 8
                
                ticks-per:
                  animal-spawns: 400
                  monster-spawns: 1
                  autosave: 6000
                  cache-cleanup: 900
                
                spawn-limits:
                  monsters: 70
                  animals: 15
                  water-animals: 5
                  ambient: 15
                
                aliases:
                
                worlds: {}
            """.trimIndent())
        }
    }
}
