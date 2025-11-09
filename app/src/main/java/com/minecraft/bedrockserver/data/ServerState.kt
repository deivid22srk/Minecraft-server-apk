package com.minecraft.bedrockserver.data

data class ServerState(
    val isRunning: Boolean = false,
    val serverAddress: String = "",
    val publicAddress: String = "",
    val port: Int = 19132,
    val playersOnline: Int = 0,
    val maxPlayers: Int = 10,
    val consoleOutput: List<String> = emptyList(),
    val cpuUsage: Float = 0f,
    val memoryUsage: Float = 0f
)
