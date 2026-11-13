package com.example.catalog

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val PlayGold = Color(0xFFE6BD63)
private val PlayBg = Color(0xFF101014)
private val PlayCard = Color(0xFF202026)

@Composable
fun CatalogScreen(source: String) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeCategory by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val isArchive = source == "Internet Archive"

    fun search(term: String, category: String = "") {
        query = term
        activeCategory = category
        scope.launch {
            loading = true; error = null; selected = null; results = emptyList()
            runCatching { if (isArchive) CatalogRepository.searchInternetArchive(term, categoryBrowse = category.isNotBlank()) else CatalogRepository.searchWikimedia(term, categoryBrowse = category.isNotBlank()) }
                .onSuccess {
                    results = it
                    if (it.isEmpty()) error = "No encontramos coincidencias exactas con licencia abierta. Probá otro título o categoría."
                }
                .onFailure { error = "No se pudo consultar el catálogo. Revisá tu conexión e intentá de nuevo." }
            loading = false
        }
    }

    val categories = listOf("Clásicos" to "Classic films", "Animación" to "Animated films", "Cortos" to "Short films")
    Column(Modifier.fillMaxSize().background(PlayBg)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("LA CLAVE PLAY", color = PlayGold, style = MaterialTheme.typography.labelLarge)
            Text(if (isArchive) "Películas" else "Videos abiertos", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(if (isArchive) "Catálogo abierto · títulos disponibles según licencia" else "Wikimedia Commons · videos con licencia abierta", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Buscar un título") }, trailingIcon = { IconButton(onClick = { search(query) }) { Icon(Icons.Default.Search, contentDescription = "Buscar") } })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { search(query) }, enabled = !loading && query.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PlayGold, contentColor = Color.Black)) { Text("Buscar en $source") }
            Spacer(Modifier.height(12.dp))
            Text("Explorar categorías", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (name, term) ->
                    FilterChip(selected = activeCategory == name, onClick = { search(term, name) }, label = { Text(name) })
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = PlayGold)
            error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(vertical = 12.dp)) }
            selected?.let { item -> PlayerPanel(item = item, onClose = { selected = null }) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results, key = { it.id }) { item ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { selected = item }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.elevatedCardColors(containerColor = PlayCard)) {
                    Column {
                        AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val byline = listOf(item.creator, item.year).filter(String::isNotBlank).joinToString(" · ")
                            if (byline.isNotBlank()) Text(byline, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Text(item.rights, color = PlayGold, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Text("Reproducir", color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
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
            setMediaItem(MediaItem.fromUri(item.mediaUrl)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.elevatedCardColors(containerColor = PlayCard)) {
        Column {
            Box {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(220.dp))
                if (isBuffering && playbackError == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = PlayGold)
            }
            playbackError?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            Text("Fuente: ${item.sourceName} · ${item.rights}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }) { Text("Abrir ficha original") }
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
        }
    }
}
