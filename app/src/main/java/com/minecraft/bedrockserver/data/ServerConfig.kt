package com.minecraft.bedrockserver.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class ServerConfig(
    val serverName: String = "Minecraft Bedrock Server",
    val maxPlayers: Int = 10,
    val gameMode: String = "survival",
    val difficulty: String = "normal",
    val showCoordinates: Boolean = true,
    val keepInventory: Boolean = true,
    val pvp: Boolean = true,
    val port: Int = 19132,
    val publicServer: Boolean = true,
    val aternosServerUrl: String = "",
    val enableWhitelist: Boolean = false,
    val levelSeed: String = "",
    val useTunnel: Boolean = true,
    val tunnelService: String = "playit"
) {
    companion object {
        private const val CONFIG_FILE = "server_config.json"
        
        fun load(context: Context): ServerConfig {
            val file = File(context.filesDir, CONFIG_FILE)
            return if (file.exists()) {
                try {
                    Gson().fromJson(file.readText(), ServerConfig::class.java)
                } catch (e: Exception) {
                    ServerConfig()
                }
            } else {
                ServerConfig()
            }
        }
        
        fun save(context: Context, config: ServerConfig) {
            val file = File(context.filesDir, CONFIG_FILE)
            file.writeText(Gson().toJson(config))
        }
    }
    
    fun toProperties(): String {
        return """
            |server-name=$serverName
            |gamemode=$gameMode
            |difficulty=$difficulty
            |max-players=$maxPlayers
            |server-port=$port
            |white-list=${if (enableWhitelist) "on" else "off"}
            |allow-cheats=on
            |show-coordinates=${if (showCoordinates) "true" else "false"}
            |pvp=${if (pvp) "true" else "false"}
            |level-seed=$levelSeed
            |server-authoritative-movement=server-auth
            |player-movement-score-threshold=20
            |server-authoritative-block-breaking=true
        """.trimMargin()
    }
}
