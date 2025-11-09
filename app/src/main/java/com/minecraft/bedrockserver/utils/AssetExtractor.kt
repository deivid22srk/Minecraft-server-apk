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
    private const val EXTRACTION_VERSION = "1.4"
    private const val PREFS_NAME = "asset_extractor"
    private const val KEY_VERSION = "extracted_version"
    
    private const val PHP_BINARY_URL = "https://github.com/pmmp/PHP-Binaries/releases/download/php-8.2.19-pmmp/PHP-8.2.19-Linux-aarch64.tar.gz"
    private const val POCKETMINE_PHAR_URL = "https://github.com/pmmp/PocketMine-MP/releases/download/5.11.2/PocketMine-MP.phar"
    
    fun extractIfNeeded(context: Context): File {
        val baseDir = File(context.filesDir, "bedrock_server")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val extractedVersion = prefs.getString(KEY_VERSION, "")
        
        if (extractedVersion != EXTRACTION_VERSION || !baseDir.exists()) {
            Log.i(TAG, "Extracting assets (version $EXTRACTION_VERSION)...")
            extract(context, baseDir)
            prefs.edit().putString(KEY_VERSION, EXTRACTION_VERSION).apply()
            Log.i(TAG, "Assets extracted successfully")
        } else {
            Log.i(TAG, "Assets already extracted (version $extractedVersion)")
        }
        
        return baseDir
    }
    
    private fun extract(context: Context, baseDir: File) {
        baseDir.mkdirs()
        
        val abi = getSupportedAbi()
        Log.i(TAG, "Detected ABI: $abi")
        
        extractAssetFolder(context, "php/$abi", baseDir)
        
        val phpBinary = File(baseDir, "bin/php7/bin/php")
        if (!phpBinary.exists()) {
            Log.i(TAG, "PHP binary not found, downloading...")
            try {
                downloadAndExtractPhpBinary(baseDir)
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
        
        val binDir = File(baseDir, "bin/php7/bin")
        if (binDir.exists()) {
            binDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    setExecutablePermissions(file)
                }
            }
            Log.i(TAG, "PHP binaries made executable in: ${binDir.absolutePath}")
        }
        
        val libPath = File(baseDir, "bin/php7/lib")
        if (libPath.exists()) {
            libPath.walk().filter { it.extension == "so" }.forEach { 
                setExecutablePermissions(it)
            }
            Log.i(TAG, "PHP libraries found at: ${libPath.absolutePath}")
        }
        
        val pocketMineDir = File(baseDir, "pocketmine")
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
        
        File(baseDir, "worlds").mkdirs()
        File(baseDir, "plugins").mkdirs()
        File(baseDir, "players").mkdirs()
        File(baseDir, "resource_packs").mkdirs()
        File(baseDir, "behavior_packs").mkdirs()
        
        createServerProperties(baseDir)
        createPocketMineYml(baseDir)
        createServerYml(baseDir)
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
            Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
            file.setExecutable(true, false)
            file.setReadable(true, false)
            Log.d(TAG, "Set executable: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set permissions for ${file.absolutePath}: ${e.message}")
            file.setExecutable(true, false)
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
                    }
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
        return if (supportedAbis.contains("arm64-v8a")) {
            "arm64-v8a"
        } else {
            Log.e(TAG, "Device is not ARM64! Supported ABIs: ${supportedAbis.joinToString(", ")}")
            Log.e(TAG, "This app only supports ARM64 (64-bit) Android devices")
            throw UnsupportedOperationException("Only ARM64 (64-bit) devices are supported")
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
