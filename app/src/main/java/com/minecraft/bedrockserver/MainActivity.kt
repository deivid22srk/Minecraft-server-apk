package com.minecraft.bedrockserver

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minecraft.bedrockserver.service.MinecraftServerService
import com.minecraft.bedrockserver.ui.screens.ConsoleScreen
import com.minecraft.bedrockserver.ui.screens.HomeScreen
import com.minecraft.bedrockserver.ui.screens.SettingsScreen
import com.minecraft.bedrockserver.ui.screens.SetupScreen
import com.minecraft.bedrockserver.ui.theme.MinecraftServerTheme
import com.minecraft.bedrockserver.viewmodel.ServerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ServerViewModel by viewModels()
    private var serverService: MinecraftServerService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MinecraftServerService.LocalBinder
            serverService = binder.getService()
            viewModel.setMinecraftServer(serverService!!.getMinecraftServer())
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { entry ->
            if (!entry.value) {
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestRequiredPermissions()
        
        val intent = Intent(this, MinecraftServerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        setContent {
            MinecraftServerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(viewModel = viewModel)
                }
            }
        }
    }
    
    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

@Composable
fun MainNavigation(viewModel: ServerViewModel) {
    val navController = rememberNavController()
    val serverState by viewModel.serverState.collectAsState()
    val config by viewModel.config.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val isPhpInstalled by viewModel.isPhpInstalled.collectAsState()
    
    val startDestination = if (isPhpInstalled) "home" else "setup"
    
    NavHost(navController = navController, startDestination = startDestination) {
        composable("setup") {
            SetupScreen(
                isPhpInstalled = isPhpInstalled,
                onInstallPhp = { viewModel.installPhp() },
                onSkip = { navController.navigate("home") }
            )
        }
        composable("home") {
            HomeScreen(
                serverState = serverState,
                onStartServer = { viewModel.startServer() },
                onStopServer = { viewModel.stopServer() },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToConsole = { navController.navigate("console") }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                config = config,
                onConfigChange = { viewModel.updateConfig(it) },
                onNavigateBack = { navController.popBackStack() },
                importStatus = importStatus,
                onImportFromAternos = { viewModel.importFromAternos(it) },
                onResetImportStatus = { viewModel.resetImportStatus() }
            )
        }
        
        composable("console") {
            ConsoleScreen(
                consoleOutput = serverState.consoleOutput,
                isServerRunning = serverState.isRunning,
                onExecuteCommand = { viewModel.executeCommand(it) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
