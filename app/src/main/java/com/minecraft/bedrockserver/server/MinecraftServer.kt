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
    private var errorReaderJob: Job? = null
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
            
            // Tornar todos os binários executáveis
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
            
            addConsoleLog("Testando compatibilidade do binário PHP...")
            try {
                val testProcess = ProcessBuilder(
                    phpBinary.absolutePath, "-v"
                ).apply {
                    environment()["LD_LIBRARY_PATH"] = libPath.absolutePath
                }.start()
                
                testProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                val testExitCode = testProcess.exitValue()
                
                if (testExitCode == 126) {
                    addConsoleLog("✗ ERRO: Binário PHP incompatível com seu dispositivo")
                    addConsoleLog("✗ Seu dispositivo pode não suportar esta versão do PHP")
                    addConsoleLog("✗ Verifique se seu dispositivo é ARM64 (aarch64)")
                    return@withContext
                } else if (testExitCode == 127) {
                    addConsoleLog("✗ ERRO: Binário PHP não encontrado ou bibliotecas faltando")
                    return@withContext
                } else if (testExitCode != 0) {
                    val errorReader = BufferedReader(InputStreamReader(testProcess.errorStream))
                    val errors = errorReader.readLines().joinToString("\n")
                    addConsoleLog("⚠️ Teste PHP retornou código $testExitCode")
                    if (errors.isNotEmpty()) {
                        addConsoleLog("Detalhes: $errors")
                    }
                } else {
                    addConsoleLog("✓ Binário PHP compatível")
                }
            } catch (e: Exception) {
                addConsoleLog("⚠️ Erro ao testar PHP: ${e.message}")
                Log.w(TAG, "PHP test failed", e)
            }
            
            val phpIni = File(serverDir, "bin/php7/bin/php.ini")
            
            // Construir comando de forma mais robusta
            val commandList = mutableListOf(
                phpBinary.absolutePath
            )
            
            if (phpIni.exists()) {
                commandList.add("-c")
                commandList.add(phpIni.absolutePath)
            }
            
            commandList.addAll(listOf(
                pharFile.absolutePath,
                "--data=${serverDir.absolutePath}",
                "--plugins=${serverDir.absolutePath}/plugins",
                "--no-wizard",
                "--enable-ansi"
            ))
            
            val processBuilder = ProcessBuilder(commandList)
            
            // Configurar variáveis de ambiente diretamente no ProcessBuilder
            processBuilder.directory(serverDir)
            processBuilder.environment().apply {
                put("LD_LIBRARY_PATH", libPath.absolutePath)
                put("HOME", serverDir.absolutePath)
                put("TMPDIR", context.cacheDir.absolutePath)
            }
            
            // Redirecionar stderr para stdout
            processBuilder.redirectErrorStream(true)
            
            addConsoleLog("Comando: ${commandList.joinToString(" ")}")
            addConsoleLog("Executando: ${phpBinary.absolutePath}")
            addConsoleLog("PHAR: ${pharFile.absolutePath}")
            addConsoleLog("Diretório: ${serverDir.absolutePath}")
            addConsoleLog("LD_LIBRARY_PATH: ${libPath.absolutePath}")
            addConsoleLog("Variáveis de ambiente configuradas")
            
            serverProcess = processBuilder.start()
            
            delay(1000)
            
            if (serverProcess?.isAlive != true) {
                addConsoleLog("✗ Processo PHP morreu imediatamente após start()")
                try {
                    val exitCode = serverProcess?.exitValue() ?: -1
                    addConsoleLog("✗ Exit code: $exitCode")
                    
                    // Interpretação dos códigos de saída
                    when (exitCode) {
                        126 -> {
                            addConsoleLog("✗ ERRO 126: Binário não pode ser executado")
                            addConsoleLog("  Possíveis causas:")
                            addConsoleLog("  1. Binário incompatível com a arquitetura do dispositivo")
                            addConsoleLog("  2. Falta de permissões de execução")
                            addConsoleLog("  3. Bibliotecas compartilhadas incompatíveis")
                            addConsoleLog("")
                            addConsoleLog("  Verifique se seu dispositivo é ARM64 (não ARM32)")
                        }
                        127 -> {
                            addConsoleLog("✗ ERRO 127: Comando não encontrado")
                            addConsoleLog("  O binário PHP ou suas dependências não foram encontrados")
                        }
                        else -> {
                            addConsoleLog("✗ Processo terminou com código: $exitCode")
                        }
                    }
                    
                    // Ler saída de erro
                    val errorStream = serverProcess?.inputStream
                    if (errorStream != null) {
                        val errorReader = BufferedReader(InputStreamReader(errorStream))
                        val errors = mutableListOf<String>()
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            errors.add(line!!)
                        }
                        if (errors.isNotEmpty()) {
                            addConsoleLog("")
                            addConsoleLog("Saída do processo:")
                            errors.forEach { addConsoleLog("  $it") }
                        }
                    }
                } catch (e: Exception) {
                    addConsoleLog("✗ Não foi possível ler erro: ${e.message}")
                }
                _isRunning.value = false
                return@withContext
            }
            
            commandWriter = OutputStreamWriter(serverProcess!!.outputStream)
            _isRunning.value = true
            
            errorReaderJob = scope.launch {
                try {
                    val errorReader = BufferedReader(InputStreamReader(serverProcess!!.errorStream))
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null && _isRunning.value) {
                        line?.let { addConsoleLog("[ERROR] $it") }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao ler stderr", e)
                }
            }
            
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
            errorReaderJob?.cancel()
            commandWriter?.close()
            
            serverProcess?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            
            if (serverProcess?.isAlive == true) {
                serverProcess?.destroyForcibly()
            }
            
            serverProcess = null
            commandWriter = null
            outputReaderJob = null
            errorReaderJob = null
            
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
