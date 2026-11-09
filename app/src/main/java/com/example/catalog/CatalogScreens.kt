package com.example.catalog

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.TelegramGalleryViewModel
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Composable
fun CatalogScreen(source: String) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isArchive = source == "Internet Archive"

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (isArchive) "Películas y videos" else "Videos con licencia abierta", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArchive) "Resultados públicos de Internet Archive. Revisá los derechos de cada obra." else "Wikimedia Commons · las licencias y atribuciones dependen de cada archivo.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Buscar") }, trailingIcon = { IconButton(onClick = { /* Search button below handles submit */ }) { Icon(Icons.Default.Search, null) } })
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                loading = true; error = null; selected = null
                runCatching { if (isArchive) CatalogRepository.searchInternetArchive(query) else CatalogRepository.searchWikimedia(query) }
                    .onSuccess { results = it; if (it.isEmpty()) error = "No encontramos videos reproducibles para esa búsqueda." }
                    .onFailure { error = "No se pudo consultar el catálogo. Revisá tu conexión e intentá de nuevo." }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Buscar en $source") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
        selected?.let { item ->
            PlayerPanel(item = item, onClose = { selected = null })
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { item ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { selected = item }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val byline = listOf(item.creator, item.year).filter(String::isNotBlank).joinToString(" · ")
                        if (byline.isNotBlank()) Text(byline, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text(item.rights, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Reproducir", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }) {
                                Icon(Icons.Default.OpenInBrowser, null); Text("Ficha")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPanel(item: CatalogItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item.mediaUrl) {
        ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(item.mediaUrl)); prepare(); playWhenReady = true }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(220.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            Text("Fuente: ${item.sourceName} · ${item.rights}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }) { Text("Abrir ficha original") }
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
        }
    }
}

@Composable
fun TelegramGalleryTab(viewModel: TelegramGalleryViewModel) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val selfId by viewModel.selfId.collectAsStateWithLifecycle()
    val connected = selfId != null
    val items by viewModel.items.collectAsStateWithLifecycle()
    var phone by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todo") }
    LaunchedEffect(connected) { if (connected) viewModel.refresh() }
    if (!connected) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Telegram · tu galería", style = MaterialTheme.typography.headlineSmall)
        Text("La sesión queda en este dispositivo. Esta pestaña conserva el alcance actual: Mensajes Guardados.")
        when (auth) {
            is TdApi.AuthorizationStateWaitCode -> { OutlinedTextField(code, { code = it }, label = { Text("Código de Telegram") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { viewModel.code(code) }) { Text("Verificar") } }
            is TdApi.AuthorizationStateWaitPassword -> { OutlinedTextField(password, { password = it }, label = { Text("Contraseña 2FA") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { viewModel.password(password) }) { Text("Continuar") } }
            else -> { OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono con código de país") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { viewModel.phone(phone) }) { Text("Iniciar sesión") } }
        }
    } else Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("Todo", "Fotos", "Videos").forEach { tab -> FilterChip(selected = filter == tab, onClick = { filter = tab }, label = { Text(tab) }, modifier = Modifier.padding(end = 4.dp)) }
            TextButton(onClick = viewModel::refresh) { Text("Actualizar") }
            TextButton(onClick = viewModel::logout) { Text("Salir") }
        }
        val visible = items.filter { filter == "Todo" || (filter == "Fotos" && it.kind == "Foto") || (filter == "Videos" && it.kind == "Video") }
        LazyColumn { items(visible, key = { "${it.chatId}:${it.messageId}" }) { item -> ListItem(headlineContent = { Text(item.caption.ifBlank { item.kind }) }, supportingContent = { Text(item.kind) }) } }
    }
}
