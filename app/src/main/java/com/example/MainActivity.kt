package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tdlib.TdApi
import com.example.ui.TelegramGalleryViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TelegramGalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val auth by viewModel.authState.collectAsStateWithLifecycle()
                val selfId by viewModel.selfId.collectAsStateWithLifecycle()
                val connected = selfId != null
                val items by viewModel.items.collectAsStateWithLifecycle()
                var phone by remember { mutableStateOf("") }
                var code by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var filter by remember { mutableStateOf("Todo") }
                LaunchedEffect(connected) { if (connected) viewModel.refresh() }
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Telegram · Fotos y videos") },
                        actions = { if (connected) TextButton(onClick = viewModel::logout) { Text("Salir") } })
                }) { padding ->
                    if (!connected) {
                        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text("Conectá tu cuenta de Telegram", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            Text("La sesión queda en este dispositivo. Nunca se copia desde TeleCloud.")
                            Spacer(Modifier.height(24.dp))
                            when (auth) {
                                is TdApi.AuthorizationStateWaitCode -> {
                                    OutlinedTextField(code, { code = it }, label = { Text("Código de Telegram") }, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = { viewModel.code(code) }, modifier = Modifier.fillMaxWidth()) { Text("Verificar código") }
                                }
                                is TdApi.AuthorizationStateWaitPassword -> {
                                    OutlinedTextField(password, { password = it }, label = { Text("Contraseña de verificación en dos pasos") }, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = { viewModel.password(password) }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
                                }
                                else -> {
                                    OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono con código de país") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { viewModel.phone(phone) }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                            Text("Mensajes Guardados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Todo", "Fotos", "Videos").forEach { tab ->
                                    FilterChip(selected = filter == tab, onClick = { filter = tab }, label = { Text(tab) })
                                }
                                TextButton(onClick = viewModel::refresh) { Text("Actualizar") }
                            }
                            if (items.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Todavía no se encontraron fotos ni videos en Mensajes Guardados.") }
                            } else {
                                val visible = items.filter { filter == "Todo" || (filter == "Fotos" && it.kind == "Foto") || (filter == "Videos" && it.kind == "Video") }
                                LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(visible, key = { "${it.chatId}:${it.messageId}" }) { media ->
                                        ElevatedCard {
                                            Column {
                                                if (media.localPath.isNotBlank()) AsyncImage(model = media.localPath, contentDescription = media.caption, modifier = Modifier.fillMaxWidth().height(150.dp))
                                                else Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                                    Icon(if (media.kind == "Video") Icons.Default.PlayArrow else Icons.Default.Image, contentDescription = media.kind, modifier = Modifier.size(48.dp))
                                                }
                                                Text(media.kind, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
                                                if (media.caption.isNotBlank()) Text(media.caption, maxLines = 2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
