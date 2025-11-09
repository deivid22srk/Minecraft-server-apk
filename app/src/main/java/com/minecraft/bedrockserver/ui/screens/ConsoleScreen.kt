package com.minecraft.bedrockserver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    consoleOutput: List<String>,
    isServerRunning: Boolean,
    onExecuteCommand: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    LaunchedEffect(consoleOutput.size) {
        if (consoleOutput.isNotEmpty()) {
            listState.animateScrollToItem(consoleOutput.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Console do Servidor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isServerRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ Servidor não está rodando",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp),
                state = listState
            ) {
                if (consoleOutput.isEmpty()) {
                    item {
                        Text(
                            text = "Console vazio. Inicie o servidor para ver os logs.",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    items(consoleOutput) { line ->
                        Text(
                            text = line,
                            color = getConsoleLineColor(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Digite um comando...") },
                        singleLine = true,
                        enabled = isServerRunning,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FilledIconButton(
                        onClick = {
                            if (command.isNotBlank()) {
                                onExecuteCommand(command)
                                command = ""
                            }
                        },
                        enabled = isServerRunning && command.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar comando")
                    }
                }
            }
        }
    }
}

@Composable
fun getConsoleLineColor(line: String): Color {
    return when {
        line.contains("ERROR", ignoreCase = true) || line.contains("✗") -> Color(0xFFFF5252)
        line.contains("WARN", ignoreCase = true) || line.contains("⚠") -> Color(0xFFFFB74D)
        line.contains("INFO", ignoreCase = true) || line.contains("✓") -> Color(0xFF81C784)
        line.contains(">") -> Color(0xFF64B5F6)
        else -> Color(0xFFE0E0E0)
    }
}
