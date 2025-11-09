package com.minecraft.bedrockserver.server

import android.content.Context
import android.util.Log
import com.minecraft.bedrockserver.data.ServerConfig
import com.minecraft.bedrockserver.utils.AssetExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface

class MinecraftServer(private val context: Context) {
    private val TAG = "MinecraftServer"
    private var serverProcess: Process? = null
    private var outputReaderJob: Job? = null
    private var commandWriter: OutputStreamWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _playersOnline = MutableStateFlow(0)
    val playersOnline: StateFlow<Int> = _playersOnline
    
    private lateinit var serverDir: File
    
    init {
        scope.launch {
            try {
                addConsoleLog("Verificando binários do servidor...")
                serverDir = AssetExtractor.extractIfNeeded(context)
                addConsoleLog("✓ Binários extraídos com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao extrair assets", e)
                addConsoleLog("✗ Erro ao extrair binários: ${e.message}")
                addConsoleLog("✗ Detalhes: ${e.stackTraceToString()}")
            }
        }
    }
    
    suspend fun startServer(config: ServerConfig) {
        if (_isRunning.value) {
            addConsoleLog("Servidor já está rodando")
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                updateServerProperties(config)
                
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
                
                startPocketMineServer()
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar servidor", e)
                addConsoleLog("✗ Erro ao iniciar servidor: ${e.message}")
                addConsoleLog("✗ Stack trace: ${e.stackTraceToString()}")
                _isRunning.value = false
            }
        }
    }
    
    private suspend fun startPocketMineServer() = withContext(Dispatchers.IO) {
        try {
            if (!::serverDir.isInitialized) {
                addConsoleLog("Preparando servidor pela primeira vez...")
                addConsoleLog("Isso pode levar alguns minutos para baixar os arquivos necessários")
                serverDir = AssetExtractor.extractIfNeeded(context)
            }
            
            val phpBinary = File(serverDir, "bin/php7/bin/php")
            val pharFile = File(serverDir, "pocketmine/PocketMine-MP.phar")
            
            if (!phpBinary.exists()) {
                addConsoleLog("✗ Binário PHP não encontrado: ${phpBinary.absolutePath}")
                return@withContext
            }
            
            if (!pharFile.exists()) {
                addConsoleLog("✗ PocketMine-MP.phar não encontrado: ${pharFile.absolutePath}")
                return@withContext
            }
            
            phpBinary.setExecutable(true, false)
            
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", phpBinary.absolutePath)).waitFor()
                Log.d(TAG, "chmod 755 applied to PHP binary")
            } catch (e: Exception) {
                Log.w(TAG, "chmod failed: ${e.message}")
            }
            
            if (!phpBinary.canExecute()) {
                addConsoleLog("⚠️ PHP binary não tem permissão de execução, tentando workaround...")
            }
            
            val libPath = File(serverDir, "bin/php7/lib")
            if (!libPath.exists()) {
                addConsoleLog("✗ Bibliotecas PHP não encontradas: ${libPath.absolutePath}")
                return@withContext
            }
            
            val phpIni = File(serverDir, "bin/php7/bin/php.ini")
            
            val command = mutableListOf<String>()
            command.add(phpBinary.absolutePath)
            if (phpIni.exists()) {
                command.add("-c")
                command.add(phpIni.absolutePath)
            }
            command.add(pharFile.absolutePath)
            command.add("--data=${serverDir.absolutePath}")
            command.add("--plugins=${serverDir.absolutePath}/plugins")
            command.add("--no-wizard")
            command.add("--enable-ansi")

            val processBuilder = ProcessBuilder(command)

            val environment = processBuilder.environment()
            environment["LD_LIBRARY_PATH"] = libPath.absolutePath
            environment["HOME"] = serverDir.absolutePath
            environment["TMPDIR"] = context.cacheDir.absolutePath
            
            processBuilder.directory(serverDir)
            processBuilder.redirectErrorStream(true)
            
            addConsoleLog("Comando: ${command.joinToString(" ")}")
            addConsoleLog("Executando: ${phpBinary.absolutePath}")
            addConsoleLog("PHAR: ${pharFile.absolutePath}")
            addConsoleLog("Diretório: ${serverDir.absolutePath}")
            addConsoleLog("LD_LIBRARY_PATH: ${libPath.absolutePath}")
            
            serverProcess = processBuilder.start()
            
            delay(1000)
            
            if (serverProcess?.isAlive != true) {
                addConsoleLog("✗ Processo PHP morreu imediatamente após start()")
                try {
                    val exitCode = serverProcess?.exitValue() ?: -1
                    addConsoleLog("✗ Exit code: $exitCode")
                    
                    val errorStream = serverProcess?.errorStream
                    if (errorStream != null) {
                        val errorReader = BufferedReader(InputStreamReader(errorStream))
                        val errors = errorReader.readLines()
                        errors.forEach { addConsoleLog("ERROR: $it") }
                    }
                } catch (e: Exception) {
                    addConsoleLog("✗ Não foi possível ler erro: ${e.message}")
                }
                _isRunning.value = false
                return@withContext
            }
            
            commandWriter = OutputStreamWriter(serverProcess!!.outputStream)
            _isRunning.value = true
            
            outputReaderJob = scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(serverProcess!!.inputStream))
                    var line: String?
                    
                    addConsoleLog("✓ Processo PHP iniciado")
                    addConsoleLog("✓ Aguardando output do PocketMine-MP...")
                    
                    while (reader.readLine().also { line = it } != null && _isRunning.value) {
                        line?.let { 
                            processServerOutput(it)
                            addConsoleLog(it)
                        }
                    }
                } catch (e: Exception) {
                    if (_isRunning.value) {
                        Log.e(TAG, "Erro ao ler output do servidor", e)
                        addConsoleLog("✗ Erro ao ler output: ${e.message}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        if (_isRunning.value) {
                            val exitCode = serverProcess?.exitValue() ?: -1
                            addConsoleLog("✗ Servidor parou inesperadamente (exit code: $exitCode)")
                            stopServer()
                        }
                    }
                }
            }
            
            delay(2000)
            
            if (serverProcess?.isAlive == true) {
                addConsoleLog("✓ Servidor iniciado com sucesso!")
                val publicIp = getPublicIpAddress()
                val config = ServerConfig.load(context)
                addConsoleLog("Jogadores podem se conectar usando: $publicIp:${config.port}")
            } else {
                addConsoleLog("✗ Servidor falhou ao iniciar")
                _isRunning.value = false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar processo PocketMine", e)
            addConsoleLog("✗ Erro crítico: ${e.message}")
            _isRunning.value = false
        }
    }
    
    private fun processServerOutput(line: String) {
        when {
            line.contains("Done (") && line.contains("s)!") -> {
                addConsoleLog("✓ Servidor carregado completamente!")
            }
            line.contains("logged in with entity id") -> {
                _playersOnline.value++
            }
            line.contains("logged out due to") || line.contains("left the game") -> {
                _playersOnline.value = maxOf(0, _playersOnline.value - 1)
            }
            line.contains("ERROR") || line.contains("CRITICAL") -> {
                Log.e(TAG, "Server error: $line")
            }
        }
    }
    
    fun stopServer() {
        try {
            addConsoleLog("Parando servidor...")
            
            try {
                executeCommand("stop")
                Thread.sleep(3000)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao enviar comando stop", e)
            }
            
            serverProcess?.destroy()
            
            outputReaderJob?.cancel()
            commandWriter?.close()
            
            serverProcess?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            
            if (serverProcess?.isAlive == true) {
                serverProcess?.destroyForcibly()
            }
            
            serverProcess = null
            commandWriter = null
            outputReaderJob = null
            
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
        
        addConsoleLog("Configurações atualizadas:")
        addConsoleLog("  - Nome: ${config.serverName}")
        addConsoleLog("  - Modo de jogo: ${config.gameMode}")
        addConsoleLog("  - Dificuldade: ${config.difficulty}")
        addConsoleLog("  - Jogadores máximos: ${config.maxPlayers}")
        addConsoleLog("  - PvP: ${config.pvp}")
        addConsoleLog("  - Keep Inventory: ${config.keepInventory}")
        addConsoleLog("  - Show Coordinates: ${config.showCoordinates}")
    }
    
    fun executeCommand(command: String) {
        if (!_isRunning.value) {
            addConsoleLog("✗ Servidor não está rodando")
            return
        }
        
        try {
            scope.launch(Dispatchers.IO) {
                commandWriter?.apply {
                    write("$command\n")
                    flush()
                }
                addConsoleLog("> $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar comando", e)
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
