package com.minecraft.bedrockserver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.minecraft.bedrockserver.data.ServerConfig
import com.minecraft.bedrockserver.viewmodel.ServerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: ServerConfig,
    onConfigChange: (ServerConfig) -> Unit,
    onNavigateBack: () -> Unit,
    importStatus: ServerViewModel.ImportStatus,
    onImportFromAternos: (String) -> Unit,
    onResetImportStatus: () -> Unit
) {
    var serverName by remember { mutableStateOf(config.serverName) }
    var maxPlayers by remember { mutableStateOf(config.maxPlayers.toString()) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var gameMode by remember { mutableStateOf(config.gameMode) }
    var difficulty by remember { mutableStateOf(config.difficulty) }
    var showCoordinates by remember { mutableStateOf(config.showCoordinates) }
    var keepInventory by remember { mutableStateOf(config.keepInventory) }
    var pvp by remember { mutableStateOf(config.pvp) }
    var publicServer by remember { mutableStateOf(config.publicServer) }
    var aternosUrl by remember { mutableStateOf(config.aternosServerUrl) }
    var showAternosDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newConfig = config.copy(
                            serverName = serverName,
                            maxPlayers = maxPlayers.toIntOrNull() ?: 10,
                            port = port.toIntOrNull() ?: 19132,
                            gameMode = gameMode,
                            difficulty = difficulty,
                            showCoordinates = showCoordinates,
                            keepInventory = keepInventory,
                            pvp = pvp,
                            publicServer = publicServer,
                            aternosServerUrl = aternosUrl
                        )
                        onConfigChange(newConfig)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("Servidor")
            
            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("Nome do Servidor") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = maxPlayers,
                    onValueChange = { maxPlayers = it },
                    label = { Text("Max Players") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null) }
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Porta") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SectionTitle("Modo de Jogo")
            
            GameModeSelector(
                selectedMode = gameMode,
                onModeSelected = { gameMode = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SectionTitle("Dificuldade")
            
            DifficultySelector(
                selectedDifficulty = difficulty,
                onDifficultySelected = { difficulty = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SectionTitle("Opções de Gameplay")
            
            SwitchSetting(
                icon = Icons.Default.GpsFixed,
                title = "Mostrar Coordenadas",
                description = "Exibe coordenadas no jogo",
                checked = showCoordinates,
                onCheckedChange = { showCoordinates = it }
            )
            
            SwitchSetting(
                icon = Icons.Default.Inventory,
                title = "Manter Inventário",
                description = "Não perde itens ao morrer",
                checked = keepInventory,
                onCheckedChange = { keepInventory = it }
            )
            
            SwitchSetting(
                icon = Icons.Default.Security,
                title = "PvP",
                description = "Permite combate entre jogadores",
                checked = pvp,
                onCheckedChange = { pvp = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SectionTitle("Rede")
            
            SwitchSetting(
                icon = Icons.Default.Public,
                title = "Servidor Público",
                description = "Acessível de qualquer rede WiFi",
                checked = publicServer,
                onCheckedChange = { publicServer = it }
            )
            
            if (publicServer) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Configure Port Forwarding no seu roteador para a porta ${port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SectionTitle("Importar do Aternos")
            
            Button(
                onClick = { showAternosDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importar Mundo do Aternos")
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    if (showAternosDialog) {
        AternosImportDialog(
            aternosUrl = aternosUrl,
            onUrlChange = { aternosUrl = it },
            onDismiss = { 
                showAternosDialog = false
                onResetImportStatus()
            },
            onImport = {
                onImportFromAternos(aternosUrl)
                showAternosDialog = false
            },
            importStatus = importStatus
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SwitchSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun GameModeSelector(selectedMode: String, onModeSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedMode == "survival",
            onClick = { onModeSelected("survival") },
            label = { Text("Survival") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedMode == "creative",
            onClick = { onModeSelected("creative") },
            label = { Text("Creative") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedMode == "adventure",
            onClick = { onModeSelected("adventure") },
            label = { Text("Adventure") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DifficultySelector(selectedDifficulty: String, onDifficultySelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedDifficulty == "peaceful",
            onClick = { onDifficultySelected("peaceful") },
            label = { Text("Peaceful") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedDifficulty == "easy",
            onClick = { onDifficultySelected("easy") },
            label = { Text("Easy") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedDifficulty == "normal",
            onClick = { onDifficultySelected("normal") },
            label = { Text("Normal") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedDifficulty == "hard",
            onClick = { onDifficultySelected("hard") },
            label = { Text("Hard") },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AternosImportDialog(
    aternosUrl: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    importStatus: ServerViewModel.ImportStatus
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar do Aternos") },
        text = {
            Column {
                Text("Cole a URL do seu servidor Aternos:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = aternosUrl,
                    onValueChange = onUrlChange,
                    label = { Text("URL do Aternos") },
                    placeholder = { Text("https://aternos.org/server/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                when (importStatus) {
                    is ServerViewModel.ImportStatus.Loading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    is ServerViewModel.ImportStatus.Success -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "✓ Importação concluída!",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is ServerViewModel.ImportStatus.Error -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "✗ Erro na importação",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onImport,
                enabled = aternosUrl.isNotBlank() && importStatus !is ServerViewModel.ImportStatus.Loading
            ) {
                Text("Importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
