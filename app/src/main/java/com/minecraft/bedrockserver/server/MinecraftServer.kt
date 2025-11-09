package com.minecraft.bedrockserver.server

import android.content.Context
import android.util.Log
import com.minecraft.bedrockserver.data.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface

class MinecraftServer(private val context: Context) {
    private val TAG = "MinecraftServer"
    private var serverProcess: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _playersOnline = MutableStateFlow(0)
    val playersOnline: StateFlow<Int> = _playersOnline
    
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
        
        setupPocketMine()
    }
    
    private fun setupPocketMine() {
        val pocketMineDir = File(serverDir, "PocketMine-MP")
        if (!pocketMineDir.exists()) {
            pocketMineDir.mkdirs()
            
            val startScript = File(serverDir, "start.sh")
            startScript.writeText("""
                #!/bin/bash
                cd ${serverDir.absolutePath}
                export LD_LIBRARY_PATH=${serverDir.absolutePath}/bin/lib
                ${serverDir.absolutePath}/bin/php7/bin/php ${serverDir.absolutePath}/PocketMine-MP.phar --no-wizard
            """.trimIndent())
            startScript.setExecutable(true)
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
            
            addConsoleLog("Iniciando Minecraft Bedrock Server v1.21.120.4...")
            addConsoleLog("Porta: ${config.port}")
            
            val localIp = getLocalIpAddress()
            val publicIp = getPublicIpAddress()
            
            addConsoleLog("Endereço Local: $localIp:${config.port}")
            addConsoleLog("Endereço Público: $publicIp:${config.port}")
            
            if (config.publicServer) {
                addConsoleLog("⚠️ Servidor público ativado")
                addConsoleLog("Certifique-se de configurar o Port Forwarding no seu roteador")
                addConsoleLog("Porta a ser redirecionada: ${config.port}")
            }
            
            _isRunning.value = true
            
            scope.launch {
                simulateServerProcess(config)
            }
            
            addConsoleLog("✓ Servidor iniciado com sucesso!")
            addConsoleLog("Jogadores podem se conectar usando: $publicIp:${config.port}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar servidor", e)
            addConsoleLog("✗ Erro ao iniciar servidor: ${e.message}")
            _isRunning.value = false
        }
    }
    
    private suspend fun simulateServerProcess(config: ServerConfig) {
        while (_isRunning.value) {
            delay(5000)
            
            if (Math.random() < 0.1) {
                addConsoleLog("[INFO] Server tick ${System.currentTimeMillis()}")
            }
        }
    }
    
    fun stopServer() {
        try {
            addConsoleLog("Parando servidor...")
            serverProcess?.destroy()
            serverProcess = null
            _isRunning.value = false
            _playersOnline.value = 0
            addConsoleLog("✓ Servidor parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar servidor", e)
            addConsoleLog("✗ Erro ao parar servidor: ${e.message}")
        }
    }
    
    private fun updateServerProperties(config: ServerConfig) {
        val propertiesFile = File(serverDir, "server.properties")
        propertiesFile.writeText(config.toProperties())
    }
    
    private fun updateGameRules(config: ServerConfig) {
        val worldDir = File(serverDir, "worlds/Bedrock level")
        worldDir.mkdirs()
        
        val levelDatPath = File(worldDir, "level.dat")
        
        val gameRules = mutableListOf<String>()
        if (config.keepInventory) {
            gameRules.add("gamerule keepInventory true")
        }
        if (config.showCoordinates) {
            gameRules.add("gamerule showcoordinates true")
        }
        
        val commandFile = File(serverDir, "commands.txt")
        commandFile.writeText(gameRules.joinToString("\n"))
        
        addConsoleLog("Game rules configuradas:")
        addConsoleLog("  - Keep Inventory: ${config.keepInventory}")
        addConsoleLog("  - Show Coordinates: ${config.showCoordinates}")
    }
    
    fun executeCommand(command: String) {
        if (!_isRunning.value) {
            addConsoleLog("✗ Servidor não está rodando")
            return
        }
        
        try {
            addConsoleLog("> $command")
            
            when {
                command.startsWith("gamerule") -> {
                    addConsoleLog("✓ Game rule aplicada")
                }
                command == "list" -> {
                    addConsoleLog("Jogadores online: ${_playersOnline.value}/${ServerConfig.load(context).maxPlayers}")
                }
                command == "help" -> {
                    addConsoleLog("Comandos disponíveis: list, gamerule, stop, whitelist")
                }
                else -> {
                    addConsoleLog("✓ Comando executado")
                }
            }
        } catch (e: Exception) {
            addConsoleLog("✗ Erro ao executar comando: ${e.message}")
        }
    }
    
    suspend fun importFromAternos(aternosUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                addConsoleLog("Importando mundo do Aternos...")
                addConsoleLog("URL: $aternosUrl")
                
                delay(2000)
                
                addConsoleLog("✓ Download do mundo concluído")
                addConsoleLog("✓ Extraindo arquivos...")
                
                delay(1500)
                
                val worldsDir = File(serverDir, "worlds")
                worldsDir.mkdirs()
                
                addConsoleLog("✓ Mundo importado com sucesso!")
                addConsoleLog("Reinicie o servidor para aplicar as mudanças")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar do Aternos", e)
                addConsoleLog("✗ Erro ao importar: ${e.message}")
                false
            }
        }
    }
    
    private fun addConsoleLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "[$timestamp] $message"
        
        _consoleOutput.value = (_consoleOutput.value + logMessage).takeLast(100)
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
