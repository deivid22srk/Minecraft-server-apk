package com.minecraft.bedrockserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.minecraft.bedrockserver.MainActivity
import com.minecraft.bedrockserver.server.MinecraftServer

class MinecraftServerService : Service() {
    private lateinit var minecraftServer: MinecraftServer
    private val binder = LocalBinder()
    
    companion object {
        private const val CHANNEL_ID = "minecraft_server_channel"
        private const val NOTIFICATION_ID = 1
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): MinecraftServerService = this@MinecraftServerService
    }
    
    override fun onCreate() {
        super.onCreate()
        minecraftServer = MinecraftServer(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    fun getMinecraftServer(): MinecraftServer = minecraftServer
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Minecraft Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Minecraft Bedrock Server rodando"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Minecraft Server")
            .setContentText("Servidor rodando")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        minecraftServer.cleanup()
        super.onDestroy()
    }
}
