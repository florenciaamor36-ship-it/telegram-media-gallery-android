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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

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
        Text(if (isArchive) "Solo se muestran videos con una licencia abierta identificada. Revisá la ficha antes de reutilizar." else "Wikimedia Commons · se muestran videos con licencia abierta identificada; revisá atribución y condiciones.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Buscar") }, trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                loading = true; error = null; selected = null; results = emptyList()
                runCatching { if (isArchive) CatalogRepository.searchInternetArchive(query) else CatalogRepository.searchWikimedia(query) }
                    .onSuccess {
                        results = it
                        if (it.isEmpty()) error = "No encontramos videos que tengan a la vez un formato reproducible y una licencia abierta identificada. Probá otra búsqueda."
                    }
                    .onFailure { error = "No se pudo consultar el catálogo. Revisá tu conexión e intentá de nuevo." }
                loading = false
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Buscar en $source") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
        selected?.let { item -> PlayerPanel(item = item, onClose = { selected = null }) }
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
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null); Text("Ficha")
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
    var playbackError by remember(item.mediaUrl) { mutableStateOf<String?>(null) }
    var isBuffering by remember(item.mediaUrl) { mutableStateOf(true) }
    val player = remember(item.mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                    if (playbackState == Player.STATE_READY) playbackError = null
                }
                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    playbackError = "No se pudo reproducir este archivo (${error.errorCodeName}). Puede usar un códec no compatible o haber sido retirado. Probá otro video o abrí la ficha original."
                }
            })
            setMediaItem(MediaItem.fromUri(item.mediaUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column {
            Box {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(220.dp))
                if (isBuffering && playbackError == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            Text("Fuente: ${item.sourceName} · ${item.rights}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }) { Text("Abrir ficha original") }
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
        }
    }
}
