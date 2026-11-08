package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TelegramGalleryItem
import com.example.tdlib.TelegramGalleryClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class TelegramGalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val client = TelegramGalleryClient(app)
    val authState = client.authState
    val selfId = client.selfId
    private val mutableItems = MutableStateFlow<List<TelegramGalleryItem>>(emptyList())
    val items: StateFlow<List<TelegramGalleryItem>> = mutableItems.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()

    fun phone(value: String) = client.submitPhone(value)
    fun code(value: String) = client.submitCode(value)
    fun password(value: String) = client.submitPassword(value)
    fun logout() = client.logout()
    fun refresh() {
        mutableLoading.value = true
        client.loadSavedMessages { result ->
            result.onSuccess { messages ->
                val mapped = messages.mapNotNull { m ->
                    when (val c = m.content) {
                        is TdApi.MessagePhoto -> c.photo.sizes.lastOrNull()?.photo?.let { TelegramGalleryItem(m.id, m.chatId, "Foto", c.caption.text, it.id, it.local.path) }
                        is TdApi.MessageVideo -> c.video.video.let { TelegramGalleryItem(m.id, m.chatId, "Video", c.caption.text, it.id, it.local.path) }
                        else -> null
                    }
                }
                mutableItems.value = mapped
                mapped.forEach { item -> if (item.localPath.isBlank()) {
                    val msg = messages.firstOrNull { it.id == item.messageId } ?: return@forEach
                    val f = when (val c = msg.content) { is TdApi.MessagePhoto -> c.photo.sizes.lastOrNull()?.photo; is TdApi.MessageVideo -> c.video.video; else -> null } ?: return@forEach
                    client.download(f) { downloaded -> downloaded.onSuccess { path -> mutableItems.value = mutableItems.value.map { if (it.messageId == item.messageId) it.copy(localPath = path) else it } } }
                } }
            }
            mutableLoading.value = false
        }
    }
}
