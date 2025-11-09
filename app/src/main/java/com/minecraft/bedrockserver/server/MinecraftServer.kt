package com.minecraft.bedrockserver.server

import android.content.Context
import android.util.Log
import com.minecraft.bedrockserver.data.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface

class MinecraftServer(private val context: Context) {
    private val TAG = "MinecraftServer"
    private var serverProcess: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val phpManager = PhpManager(context)
    
    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _playersOnline = MutableStateFlow(0)
    val playersOnline: StateFlow<Int> = _playersOnline
    
    private val _isPhpInstalled = MutableStateFlow(false)
    val isPhpInstalled: StateFlow<Boolean> = _isPhpInstalled
    
    private val serverDir: File
        get() = File(context.filesDir, "bedrock_server")
    
    private val pocketMineFile: File
        get() = File(serverDir, "PocketMine-MP.phar")
    
    init {
        setupServerDirectory()
        checkPhpInstallation()
    }
    
    private fun setupServerDirectory() {
        if (!serverDir.exists()) {
            serverDir.mkdirs()
        }
        
        File(serverDir, "worlds").mkdirs()
        File(serverDir, "plugins").mkdirs()
        File(serverDir, "players").mkdirs()
        File(serverDir, "plugin_data").mkdirs()
        
        extractPocketMine()
    }
    
    private fun checkPhpInstallation() {
        _isPhpInstalled.value = phpManager.phpBinary.exists() && phpManager.phpBinary.canExecute()
    }
    
    suspend fun installPhp(): Boolean {
        return phpManager.ensurePhpInstalled { progress ->
            addConsoleLog(progress)
        }.also {
            checkPhpInstallation()
        }
    }
    
    private fun extractPocketMine() {
        try {
            if (!pocketMineFile.exists()) {
                addConsoleLog("📦 Extraindo PocketMine-MP...")
                
                val inputStream = context.assets.open("PocketMine-MP.phar")
                val outputStream = FileOutputStream(pocketMineFile)
                
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                pocketMineFile.setReadable(true, false)
                
                val sizeMB = pocketMineFile.length() / (1024 * 1024)
                addConsoleLog("✅ PocketMine-MP extraído (${sizeMB}MB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair PocketMine", e)
            addConsoleLog("⚠️  PocketMine-MP não encontrado nos assets")
        }
    }
    
    suspend fun startServer(config: ServerConfig) {
        if (_isRunning.value) {
            addConsoleLog("⚠️  Servidor já está rodando")
            return
        }
        
        if (!_isPhpInstalled.value) {
            addConsoleLog("❌ PHP não está instalado")
            addConsoleLog("Instale o PHP primeiro na tela inicial")
            return
        }
        
        try {
            updateServerProperties(config)
            
            addConsoleLog("")
            addConsoleLog("===========================================")
            addConsoleLog("    🎮 MINECRAFT BEDROCK SERVER")
            addConsoleLog("       v1.21.120.4 - PocketMine-MP")
            addConsoleLog("===========================================")
            addConsoleLog("")
            
            val localIp = getLocalIpAddress()
            val port = config.port
            
            addConsoleLog("📍 ENDEREÇOS DE CONEXÃO:")
            addConsoleLog("")
            addConsoleLog("   🏠 Mesma WiFi:")
            addConsoleLog("      → $localIp:$port")
            addConsoleLog("")
            
            if (config.publicServer) {
                addConsoleLog("   🌐 WiFi Diferente (Escolha uma):")
                addConsoleLog("")
                addConsoleLog("   1️⃣  Playit.gg (Recomendado)")
                addConsoleLog("      → https://playit.gg")
                addConsoleLog("      → Crie túnel UDP → porta $port")
                addConsoleLog("")
                addConsoleLog("   2️⃣  Ngrok")
                addConsoleLog("      → ngrok tcp $port")
                addConsoleLog("")
                addConsoleLog("   3️⃣  Radmin VPN")
                addConsoleLog("      → Rede virtual gratuita")
                addConsoleLog("")
            }
            
            addConsoleLog("===========================================")
            addConsoleLog("")
            
            startPocketMineServer(config)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar servidor", e)
            addConsoleLog("❌ Erro: ${e.message}")
            _isRunning.value = false
        }
    }
    
    private suspend fun startPocketMineServer(config: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                _isRunning.value = true
                
                addConsoleLog("🚀 Iniciando PocketMine-MP...")
                addConsoleLog("")
                
                val phpPath = phpManager.getPhpPath()
                addConsoleLog("PHP: $phpPath")
                
                val phpVersion = phpManager.getPhpVersion()
                if (phpVersion != null) {
                    addConsoleLog("Versão: $phpVersion")
                }
                addConsoleLog("")
                
                val pharPath = pocketMineFile.absolutePath
                
                val startScript = File(serverDir, "start.sh")
                startScript.writeText("""
                    #!/system/bin/sh
                    cd ${serverDir.absolutePath}
                    exec ${phpPath} \
                        -d memory_limit=1024M \
                        -d error_reporting=E_ALL \
                        -d display_errors=1 \
                        ${pharPath} \
                        --no-wizard \
                        --disable-readline
                """.trimIndent())
                startScript.setExecutable(true, false)
                
                val processBuilder = ProcessBuilder("sh", startScript.absolutePath)
                processBuilder.directory(serverDir)
                processBuilder.redirectErrorStream(true)
                
                val env = processBuilder.environment()
                env["POCKETMINE_LANGUAGE"] = "por"
                env["POCKETMINE_IN_GAME"] = "1"
                env["HOME"] = serverDir.absolutePath
                
                serverProcess = processBuilder.start()
                
                scope.launch {
                    readProcessOutput(serverProcess!!)
                }
                
                scope.launch {
                    monitorServerProcess(serverProcess!!)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar PocketMine", e)
                addConsoleLog("❌ Erro ao iniciar: ${e.message}")
                addConsoleLog("")
                addConsoleLog("Stack trace:")
                e.stackTrace.take(5).forEach {
                    addConsoleLog("   ${it}")
                }
                _isRunning.value = false
            }
        }
    }
    
    private suspend fun readProcessOutput(process: Process) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    line?.let { 
                        addConsoleLog(it)
                        parseServerOutput(it)
                    }
                }
                
            } catch (e: Exception) {
                if (_isRunning.value) {
                    Log.e(TAG, "Erro ao ler output", e)
                }
            }
        }
    }
    
    private suspend fun monitorServerProcess(process: Process) {
        withContext(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                
                addConsoleLog("")
                addConsoleLog("⚠️  Servidor encerrado (código: $exitCode)")
                
                _isRunning.value = false
                _playersOnline.value = 0
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao monitorar processo", e)
            }
        }
    }
    
    private fun parseServerOutput(line: String) {
        when {
            line.contains("Done", ignoreCase = true) && line.contains("For help") -> {
                addConsoleLog("")
                addConsoleLog("🎉 SERVIDOR PRONTO!")
                addConsoleLog("✅ Jogadores podem se conectar agora!")
                addConsoleLog("")
            }
            line.contains("logged in with entity id", ignoreCase = true) -> {
                _playersOnline.value = _playersOnline.value + 1
                val playerName = line.substringAfter("INFO]: ").substringBefore("[/")
                addConsoleLog("👋 $playerName entrou no servidor!")
            }
            line.contains("logged out due to", ignoreCase = true) -> {
                _playersOnline.value = maxOf(0, _playersOnline.value - 1)
            }
            line.contains("ERROR", ignoreCase = true) -> {
                Log.e(TAG, line)
            }
        }
    }
    
    fun stopServer() {
        try {
            addConsoleLog("")
            addConsoleLog("⏹️  Enviando comando stop...")
            
            serverProcess?.outputStream?.write("stop\n".toByteArray())
            serverProcess?.outputStream?.flush()
            
            scope.launch {
                delay(10000)
                if (serverProcess?.isAlive == true) {
                    addConsoleLog("⚠️  Forçando encerramento...")
                    serverProcess?.destroyForcibly()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar servidor", e)
            addConsoleLog("❌ Erro ao parar: ${e.message}")
            serverProcess?.destroyForcibly()
            _isRunning.value = false
            _playersOnline.value = 0
        }
    }
    
    private fun updateServerProperties(config: ServerConfig) {
        val propertiesFile = File(serverDir, "server.properties")
        propertiesFile.writeText(config.toProperties())
        
        createPocketMineYml(config)
    }
    
    private fun createPocketMineYml(config: ServerConfig) {
        val yml = File(serverDir, "pocketmine.yml")
        yml.writeText("""
settings:
 language: "por"
 force-language: false
 shutdown-message: "Servidor encerrado"
 query-plugins: true
 deprecated-verbose: default
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
 garbage-collection:
  period: 36000
  collect-async-worker: true
  low-memory-trigger: true

network:
 batch-threshold: 256
 compression-level: 7
 compression-async: true
 upnp-forwarding: false
 max-mtu-size: 1492

debug:
 commands: false
 level: 1

level-settings:
 default-format: leveldb
 auto-tick-rate: true
 auto-tick-rate-limit: 20
 base-tick-rate: 1
 always-tick-players: false

chunk-sending:
 per-tick: 4
 max-chunks: 192
 spawn-threshold: 56
 cache-chunks: true

chunk-ticking:
 per-tick: 40
 tick-radius: 3
 light-updates: false
 clear-tick-list: true
 disable-block-ticking: false

chunk-generation:
 queue-size: 8
 population-queue-size: 8

ticks-per:
 autosave: 6000

auto-report:
 enabled: false
        """.trimIndent())
        
        createServerPropertiesPm(config)
    }
    
    private fun createServerPropertiesPm(config: ServerConfig) {
        val props = File(serverDir, "server.properties")
        props.writeText("""
motd=${config.serverName}
server-port=${config.port}
server-ip=0.0.0.0
max-players=${config.maxPlayers}
gamemode=${when(config.gameMode) { "survival" -> "0"; "creative" -> "1"; "adventure" -> "2"; else -> "0" }}
difficulty=${when(config.difficulty) { "peaceful" -> "0"; "easy" -> "1"; "normal" -> "2"; "hard" -> "3"; else -> "1" }}
pvp=${config.pvp}
white-list=${config.enableWhitelist}
announce-player-achievements=true
spawn-protection=16
allow-flight=false
force-gamemode=false
hardcore=false
online-mode=false
level-name=world
level-seed=${config.levelSeed}
level-type=DEFAULT
enable-command-block=true
spawn-animals=true
spawn-mobs=true
generate-structures=true
        """.trimIndent())
        
        if (config.keepInventory || config.showCoordinates) {
            createWorldGameRules(config)
        }
    }
    
    private fun createWorldGameRules(config: ServerConfig) {
        val worldDir = File(serverDir, "worlds/world")
        worldDir.mkdirs()
        
        val commandsFile = File(serverDir, "startup_commands.txt")
        val commands = mutableListOf<String>()
        
        if (config.keepInventory) {
            commands.add("gamerule keepInventory true")
        }
        if (config.showCoordinates) {
            commands.add("gamerule showcoordinates true")
        }
        
        commandsFile.writeText(commands.joinToString("\n"))
    }
    
    fun executeCommand(command: String) {
        if (!_isRunning.value) {
            addConsoleLog("❌ Servidor não está rodando")
            return
        }
        
        try {
            addConsoleLog("> $command")
            
            serverProcess?.outputStream?.write("$command\n".toByteArray())
            serverProcess?.outputStream?.flush()
            
        } catch (e: Exception) {
            addConsoleLog("❌ Erro ao executar comando: ${e.message}")
        }
    }
    
    suspend fun importFromAternos(aternosUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                addConsoleLog("📥 Importando do Aternos...")
                addConsoleLog("URL: $aternosUrl")
                addConsoleLog("")
                
                delay(1500)
                
                addConsoleLog("⚠️  Importação automática em desenvolvimento")
                addConsoleLog("")
                addConsoleLog("COMO IMPORTAR MANUALMENTE:")
                addConsoleLog("1. Faça backup do mundo no Aternos")
                addConsoleLog("2. Baixe o arquivo .zip")
                addConsoleLog("3. Extraia em:")
                addConsoleLog("   ${serverDir.absolutePath}/worlds/world")
                addConsoleLog("4. Reinicie o servidor")
                addConsoleLog("")
                
                false
            } catch (e: Exception) {
                addConsoleLog("❌ Erro: ${e.message}")
                false
            }
        }
    }
    
    private fun addConsoleLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        val logMessage = if (message.startsWith("[")) {
            message
        } else {
            "[$timestamp] $message"
        }
        
        _consoleOutput.value = (_consoleOutput.value + logMessage).takeLast(500)
        Log.d(TAG, message)
    }
    
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (ip != null && ip.startsWith("192.168.")) {
                                return ip
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IP local", e)
        }
        return "Unknown"
    }
    
    suspend fun getPublicIpAddress(): String {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "curl -s https://api.ipify.org"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val ip = reader.readLine() ?: "Unknown"
                process.waitFor()
                ip
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }
    
    fun cleanup() {
        stopServer()
        scope.cancel()
    }
}
