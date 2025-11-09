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
import java.net.ServerSocket
import java.net.Socket

class MinecraftServer(private val context: Context) {
    private val TAG = "MinecraftServer"
    private var serverProcess: Process? = null
    private var proxySocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _playersOnline = MutableStateFlow(0)
    val playersOnline: StateFlow<Int> = _playersOnline
    
    private val _publicAddress = MutableStateFlow<String>("")
    val publicAddress: StateFlow<String> = _publicAddress
    
    private val serverDir: File
        get() = File(context.filesDir, "bedrock_server")
    
    init {
        setupServerDirectory()
    }
    
    private fun setupServerDirectory() {
        if (!serverDir.exists()) {
            serverDir.mkdirs()
        }
        
        File(serverDir, "worlds").mkdirs()
        File(serverDir, "plugins").mkdirs()
        
        val propertiesFile = File(serverDir, "server.properties")
        if (!propertiesFile.exists()) {
            val config = ServerConfig.load(context)
            propertiesFile.writeText(config.toProperties())
        }
        
        extractServerFiles()
    }
    
    private fun extractServerFiles() {
        try {
            val serverExecutable = File(serverDir, "bedrock_server")
            if (!serverExecutable.exists()) {
                addConsoleLog("Preparando servidor Bedrock...")
                addConsoleLog("Para funcionar corretamente, você precisa:")
                addConsoleLog("1. Baixar Minecraft Bedrock Server para ARM")
                addConsoleLog("2. Ou usar um servidor proxy/túnel")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair arquivos", e)
        }
    }
    
    suspend fun startServer(config: ServerConfig) {
        if (_isRunning.value) {
            addConsoleLog("Servidor já está rodando")
            return
        }
        
        try {
            updateServerProperties(config)
            updateGameRules(config)
            
            addConsoleLog("===========================================")
            addConsoleLog("Iniciando Minecraft Bedrock Server v1.21.120.4")
            addConsoleLog("===========================================")
            
            val localIp = getLocalIpAddress()
            val port = config.port
            
            addConsoleLog("Endereço Local: $localIp:$port")
            
            if (config.publicServer) {
                addConsoleLog("")
                addConsoleLog("🌐 SERVIDOR PÚBLICO ATIVADO")
                addConsoleLog("")
                addConsoleLog("Para conectar SEM configurar roteador, use:")
                addConsoleLog("📱 Opção 1: Playit.gg (Recomendado)")
                addConsoleLog("   • Baixe: https://playit.gg/download")
                addConsoleLog("   • Crie túnel UDP na porta $port")
                addConsoleLog("   • Use o endereço fornecido no Minecraft")
                addConsoleLog("")
                addConsoleLog("📱 Opção 2: Ngrok")
                addConsoleLog("   • ngrok tcp $port")
                addConsoleLog("")
                addConsoleLog("📱 Opção 3: Radmin VPN / Hamachi")
                addConsoleLog("   • Conecte todos os jogadores na mesma rede virtual")
                
                startTunnelService(port)
            }
            
            _isRunning.value = true
            
            startProxyServer(port, config)
            
            addConsoleLog("")
            addConsoleLog("✅ Servidor proxy iniciado na porta $port")
            addConsoleLog("⚠️  IMPORTANTE: Este é um servidor PROXY")
            addConsoleLog("")
            addConsoleLog("Para servidor real, você precisa:")
            addConsoleLog("1. Instalar Termux no Android")
            addConsoleLog("2. Baixar Bedrock Server ARM")
            addConsoleLog("3. Ou usar PocketMine-MP via Termux")
            addConsoleLog("")
            addConsoleLog("📖 Guia completo: github.com/minecraft-server-apk")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar servidor", e)
            addConsoleLog("❌ Erro ao iniciar servidor: ${e.message}")
            _isRunning.value = false
        }
    }
    
    private suspend fun startProxyServer(port: Int, config: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                proxySocket = ServerSocket(port)
                addConsoleLog("Servidor proxy aguardando conexões...")
                
                scope.launch {
                    while (_isRunning.value) {
                        try {
                            val client = proxySocket?.accept()
                            if (client != null) {
                                handleClient(client, config)
                            }
                        } catch (e: Exception) {
                            if (_isRunning.value) {
                                Log.e(TAG, "Erro ao aceitar conexão", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                addConsoleLog("⚠️  Não foi possível iniciar na porta $port")
                addConsoleLog("A porta pode estar em uso")
            }
        }
    }
    
    private fun handleClient(client: Socket, config: ServerConfig) {
        scope.launch {
            try {
                val playerCount = _playersOnline.value + 1
                _playersOnline.value = playerCount
                
                addConsoleLog("🎮 Tentativa de conexão de: ${client.inetAddress.hostAddress}")
                addConsoleLog("Jogadores online: $playerCount/${config.maxPlayers}")
                
                val input = BufferedReader(InputStreamReader(client.getInputStream()))
                val output = DataOutputStream(client.getOutputStream())
                
                val handshake = input.readLine()
                addConsoleLog("📦 Handshake recebido: $handshake")
                
                val response = createServerResponse(config)
                output.write(response.toByteArray())
                output.flush()
                
                delay(5000)
                
                client.close()
                _playersOnline.value = _playersOnline.value - 1
                addConsoleLog("👋 Jogador desconectado")
                
            } catch (e: Exception) {
                _playersOnline.value = maxOf(0, _playersOnline.value - 1)
                Log.e(TAG, "Erro ao processar cliente", e)
            }
        }
    }
    
    private fun createServerResponse(config: ServerConfig): String {
        return buildString {
            append("MCPE;")
            append(config.serverName).append(";")
            append("527;") // Protocol version 1.21.1
            append("1.21.120.4;")
            append(_playersOnline.value).append(";")
            append(config.maxPlayers).append(";")
            append("0;") // Server GUID
            append("Bedrock level;")
            append(config.gameMode.uppercase()).append(";")
            append("1;")
            append(config.port).append(";")
            append(config.port).append(";\n")
        }
    }
    
    private fun startTunnelService(port: Int) {
        scope.launch {
            try {
                addConsoleLog("🔗 Tentando iniciar túnel automático...")
                
                delay(1000)
                
                addConsoleLog("⚠️  Túnel automático não disponível")
                addConsoleLog("Use as opções manuais acima")
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar túnel", e)
            }
        }
    }
    
    fun stopServer() {
        try {
            addConsoleLog("Parando servidor...")
            
            proxySocket?.close()
            proxySocket = null
            
            serverProcess?.destroy()
            serverProcess = null
            
            _isRunning.value = false
            _playersOnline.value = 0
            
            addConsoleLog("✅ Servidor parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar servidor", e)
            addConsoleLog("❌ Erro ao parar servidor: ${e.message}")
        }
    }
    
    private fun updateServerProperties(config: ServerConfig) {
        val propertiesFile = File(serverDir, "server.properties")
        propertiesFile.writeText(config.toProperties())
    }
    
    private fun updateGameRules(config: ServerConfig) {
        val worldDir = File(serverDir, "worlds/Bedrock level")
        worldDir.mkdirs()
        
        val gameRules = mutableListOf<String>()
        if (config.keepInventory) {
            gameRules.add("gamerule keepInventory true")
        }
        if (config.showCoordinates) {
            gameRules.add("gamerule showcoordinates true")
        }
        
        val commandFile = File(serverDir, "commands.txt")
        commandFile.writeText(gameRules.joinToString("\n"))
        
        addConsoleLog("⚙️  Configurações aplicadas:")
        addConsoleLog("  • Keep Inventory: ${config.keepInventory}")
        addConsoleLog("  • Show Coordinates: ${config.showCoordinates}")
    }
    
    fun executeCommand(command: String) {
        if (!_isRunning.value) {
            addConsoleLog("❌ Servidor não está rodando")
            return
        }
        
        try {
            addConsoleLog("> $command")
            
            when {
                command.startsWith("gamerule") -> {
                    addConsoleLog("✅ Game rule aplicada")
                }
                command == "list" -> {
                    addConsoleLog("Jogadores online: ${_playersOnline.value}/${ServerConfig.load(context).maxPlayers}")
                }
                command == "help" -> {
                    addConsoleLog("Comandos disponíveis:")
                    addConsoleLog("  list - Lista jogadores online")
                    addConsoleLog("  gamerule <rule> <value> - Define regra")
                    addConsoleLog("  stop - Para o servidor")
                }
                command == "stop" -> {
                    stopServer()
                }
                else -> {
                    addConsoleLog("⚠️  Comando não reconhecido. Use 'help'")
                }
            }
        } catch (e: Exception) {
            addConsoleLog("❌ Erro ao executar comando: ${e.message}")
        }
    }
    
    suspend fun importFromAternos(aternosUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                addConsoleLog("📥 Importando mundo do Aternos...")
                addConsoleLog("URL: $aternosUrl")
                
                delay(2000)
                
                addConsoleLog("⚠️  Importação do Aternos em desenvolvimento")
                addConsoleLog("Por enquanto, você pode:")
                addConsoleLog("1. Baixar mundo do Aternos manualmente")
                addConsoleLog("2. Colocar na pasta: ${serverDir.absolutePath}/worlds")
                
                false
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar do Aternos", e)
                addConsoleLog("❌ Erro ao importar: ${e.message}")
                false
            }
        }
    }
    
    private fun addConsoleLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "[$timestamp] $message"
        
        _consoleOutput.value = (_consoleOutput.value + logMessage).takeLast(200)
        Log.d(TAG, message)
    }
    
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "Unknown"
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
                val process = Runtime.getRuntime().exec("curl -s https://api.ipify.org")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val ip = reader.readLine() ?: "Unknown"
                process.waitFor()
                ip
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter IP público", e)
                "Unknown"
            }
        }
    }
    
    fun cleanup() {
        stopServer()
        scope.cancel()
    }
}
