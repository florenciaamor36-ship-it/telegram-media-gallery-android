package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.catalog.CatalogScreen

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(
                    topBar = { TopAppBar(title = { Text("La Clave Play") }) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("IA") }, label = { Text("Internet Archive") })
                            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("WC") }, label = { Text("Wikimedia Commons") })
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        CatalogScreen(if (tab == 0) "Internet Archive" else "Wikimedia Commons")
                    }
                }
            }
        }
    }
}
