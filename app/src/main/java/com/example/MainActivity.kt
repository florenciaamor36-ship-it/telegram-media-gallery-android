package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalog.CatalogScreen
import com.example.catalog.TelegramGalleryTab
import com.example.ui.TelegramGalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: TelegramGalleryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(topBar = { TopAppBar(title = { Text("La Clave Play · catálogo abierto") }) }, bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("IA") }, label = { Text("Internet Archive") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("WC") }, label = { Text("Wikimedia") })
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("TG") }, label = { Text("Telegram") })
                    }
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            0 -> CatalogScreen("Internet Archive")
                            1 -> CatalogScreen("Wikimedia Commons")
                            else -> TelegramGalleryTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}
