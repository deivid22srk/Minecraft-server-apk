package com.minecraft.bedrockserver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minecraft.bedrockserver.data.ServerConfig
import com.minecraft.bedrockserver.data.ServerState
import com.minecraft.bedrockserver.server.MinecraftServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val _config = MutableStateFlow(ServerConfig.load(application))
    val config: StateFlow<ServerConfig> = _config
    
    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState
    
    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus
    
    private var minecraftServer: MinecraftServer? = null
    
    fun setMinecraftServer(server: MinecraftServer) {
        minecraftServer = server
        
        viewModelScope.launch {
            server.isRunning.collect { running ->
                updateServerState { it.copy(isRunning = running) }
            }
        }
        
        viewModelScope.launch {
            server.playersOnline.collect { players ->
                updateServerState { it.copy(playersOnline = players) }
            }
        }
        
        viewModelScope.launch {
            server.consoleOutput.collect { output ->
                updateServerState { it.copy(consoleOutput = output) }
            }
        }
        
        viewModelScope.launch {
            combine(server.isRunning, _config) { running, config ->
                if (running) {
                    val localIp = server.getLocalIpAddress()
                    val publicIp = server.getPublicIpAddress()
                    updateServerState { 
                        it.copy(
                            serverAddress = localIp,
                            publicAddress = publicIp,
                            port = config.port,
                            maxPlayers = config.maxPlayers
                        )
                    }
                }
            }.collect {}
        }
    }
    
    fun startServer() {
        viewModelScope.launch {
            minecraftServer?.startServer(_config.value)
        }
    }
    
    fun stopServer() {
        minecraftServer?.stopServer()
    }
    
    fun executeCommand(command: String) {
        minecraftServer?.executeCommand(command)
    }
    
    fun updateConfig(newConfig: ServerConfig) {
        _config.value = newConfig
        ServerConfig.save(getApplication(), newConfig)
    }
    
    fun importFromAternos(url: String) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Loading
            val success = minecraftServer?.importFromAternos(url) ?: false
            _importStatus.value = if (success) ImportStatus.Success else ImportStatus.Error
        }
    }
    
    fun resetImportStatus() {
        _importStatus.value = ImportStatus.Idle
    }
    
    private fun updateServerState(update: (ServerState) -> ServerState) {
        _serverState.value = update(_serverState.value)
    }
    
    sealed class ImportStatus {
        object Idle : ImportStatus()
        object Loading : ImportStatus()
        object Success : ImportStatus()
        object Error : ImportStatus()
    }
}
