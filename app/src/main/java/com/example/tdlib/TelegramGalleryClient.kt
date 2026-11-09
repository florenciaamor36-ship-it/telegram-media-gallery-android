package com.example.tdlib

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One local TDLib session for this app. No TeleCloud session or backup service is reused. */
class TelegramGalleryClient(context: Context) {
    private val appContext = context.applicationContext
    private val authMutable = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = authMutable.asStateFlow()
    private val selfIdMutable = MutableStateFlow<Long?>(null)
    val selfId: StateFlow<Long?> = selfIdMutable.asStateFlow()
    private val fileCallbacks = ConcurrentHashMap<Int, (Result<String>) -> Unit>()
    private lateinit var client: Client

    init {
        val root = File(appContext.filesDir, "telegram-media")
        File(root, "db").mkdirs(); File(root, "files").mkdirs()
        client = Client.create({ obj ->
            when (obj) {
                is TdApi.UpdateAuthorizationState -> {
                    authMutable.value = obj.authorizationState
                    if (obj.authorizationState is TdApi.AuthorizationStateReady) {
                        client.send(TdApi.GetMe()) { me -> if (me is TdApi.User) selfIdMutable.value = me.id }
                    }
                }
                is TdApi.UpdateFile -> {
                    val f = obj.file
                    if (f.local.isDownloadingCompleted) fileCallbacks.remove(f.id)?.invoke(Result.success(f.local.path))
                }
            }
        }, { error -> fileCallbacks.values.toList().forEach { it(Result.failure(error ?: Exception("Error TDLib"))) } },
            { error -> fileCallbacks.values.toList().forEach { it(Result.failure(error ?: Exception("Error TDLib"))) } })
        val p = TdApi.SetTdlibParameters()
        p.useTestDc = false
        p.databaseDirectory = File(root, "db").absolutePath
        p.filesDirectory = File(root, "files").absolutePath
        p.useFileDatabase = true; p.useChatInfoDatabase = true; p.useMessageDatabase = true; p.useSecretChats = false
        p.apiId = BuildConfig.TELEGRAM_API_ID; p.apiHash = BuildConfig.TELEGRAM_API_HASH
        p.systemLanguageCode = "es"; p.deviceModel = "Android"; p.systemVersion = android.os.Build.VERSION.RELEASE
        p.applicationVersion = BuildConfig.VERSION_NAME; p.databaseEncryptionKey = ByteArray(0)
        client.send(p) { }
        client.send(TdApi.GetAuthorizationState()) { state -> if (state is TdApi.AuthorizationState) authMutable.value = state }
    }

    fun submitPhone(phone: String) = client.send(TdApi.SetAuthenticationPhoneNumber(phone.trim(), TdApi.PhoneNumberAuthenticationSettings(false, false, false, false, false, null, null))) { }
    fun submitCode(code: String) = client.send(TdApi.CheckAuthenticationCode(code.trim())) { }
    fun submitPassword(password: String) = client.send(TdApi.CheckAuthenticationPassword(password)) { }
    fun logout() = client.send(TdApi.LogOut()) { }

    fun loadSavedMessages(onResult: (Result<Array<TdApi.Message>>) -> Unit) {
        val id = selfIdMutable.value ?: run { onResult(Result.failure(IllegalStateException("Telegram todavía no inició sesión"))); return }
        client.send(TdApi.CreatePrivateChat(id, false)) { chat ->
            if (chat is TdApi.Error) onResult(Result.failure(IllegalStateException(chat.message)))
            else client.send(TdApi.GetChatHistory(id, 0, 0, 100, false)) { result ->
                when (result) {
                    is TdApi.Messages -> onResult(Result.success(result.messages))
                    is TdApi.Error -> onResult(Result.failure(IllegalStateException(result.message)))
                    else -> onResult(Result.failure(IllegalStateException("Respuesta inesperada de Telegram")))
                }
            }
        }
    }

    fun download(file: TdApi.File, onResult: (Result<String>) -> Unit) {
        if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) { onResult(Result.success(file.local.path)); return }
        fileCallbacks[file.id] = onResult
        client.send(TdApi.DownloadFile(file.id, 16, 0, 0, false)) { result ->
            when (result) {
                is TdApi.File -> if (result.local.isDownloadingCompleted) fileCallbacks.remove(result.id)?.invoke(Result.success(result.local.path))
                is TdApi.Error -> fileCallbacks.remove(file.id)?.invoke(Result.failure(IllegalStateException(result.message)))
            }
        }
    }
}
