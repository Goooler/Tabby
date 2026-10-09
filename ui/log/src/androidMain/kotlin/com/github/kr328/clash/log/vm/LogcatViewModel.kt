package com.github.kr328.clash.log.vm

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.unknown
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.glue.util.logsDir
import com.github.kr328.clash.log.LogcatService
import com.github.kr328.clash.log.Res
import com.github.kr328.clash.log.file_exported
import com.github.kr328.clash.log.model.LogFile
import com.github.kr328.clash.log.util.LogcatFilter
import com.github.kr328.clash.log.util.LogcatReader
import java.io.OutputStreamWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

internal class LogcatViewModel(private val application: Application) : ViewModel() {
  private var conn: ServiceConnection? = null
  @Suppress("StaticFieldLeak") private var logcat: LogcatService? = null
  private var pollJob: Job? = null
  private var currentFile: LogFile? = null
  private var initialized = false
  private var started = false
  private var initialSnapshot = true

  val uiState: StateFlow<UiState>
    field = MutableStateFlow(UiState())

  val eventState: SharedFlow<EventState>
    field = MutableSharedFlow(extraBufferCapacity = 64)

  fun init(fileName: String?) {
    if (initialized) return
    initialized = true

    val file = fileName?.let(LogFile::parse)

    if (fileName != null && file == null) {
      eventState.tryEmit(EventState.InvalidFile)
      return
    }

    if (file != null) {
      currentFile = file
      uiState.update { it.copy(streaming = false) }
      loadLocalFile(file)
      return
    }

    uiState.update { it.copy(streaming = true) }
    startStreaming()
  }

  fun close() {
    val e =
      if (uiState.value.streaming) {
        application.stopService(LogcatService::class.intent)
        EventState.OpenLogs
      } else {
        EventState.Close
      }
    eventState.tryEmit(e)
  }

  fun delete() {
    val file = currentFile ?: return

    viewModelScope.launch {
      withContext(Dispatchers.IO) { application.logsDir.resolve(file.fileName).delete() }
      eventState.tryEmit(EventState.Close)
    }
  }

  fun requestExport() {
    val file = currentFile ?: return
    eventState.tryEmit(EventState.RequestExport(file.fileName))
  }

  fun exportTo(uri: Uri?) {
    val file = currentFile ?: return
    if (uri == null) return

    viewModelScope.launch {
      val messages = uiState.value.messages

      val e =
        try {
          writeLogTo(messages, file, uri)
          EventState.ShowMessage(getString(Res.string.file_exported))
        } catch (ex: Exception) {
          Logger.e("Export log file failed: ${ex.message}", ex)
          EventState.ShowMessage(ex.message ?: getString(CommonRes.string.unknown))
        }
      eventState.tryEmit(e)
    }
  }

  fun resumePolling() {
    started = true
  }

  fun pausePolling() {
    started = false
  }

  override fun onCleared() {
    pollJob?.cancel()
    reset()
  }

  private fun loadLocalFile(file: LogFile) {
    viewModelScope.launch {
      val messages =
        try {
          LogcatReader(application, file).use { it.readAll() }
        } catch (e: Exception) {
          Logger.e("Fail to read log file ${file.fileName}: ${e.message}", e)
          eventState.tryEmit(EventState.InvalidFile)
          return@launch
        }

      uiState.update { it.copy(messages = messages) }
    }
  }

  private fun startStreaming() {
    application.startForegroundService(LogcatService::class.intent)

    viewModelScope.launch {
      try {
        logcat = bindLogcatService()
        startPolling()
      } catch (e: Exception) {
        Logger.e("Bind logcat service failed: ${e.message}", e)
        runCatching { application.stopService(LogcatService::class.intent) }
          .onFailure { ex -> Logger.e("Stop logcat service failed: ${ex.message}", ex) }
        reset()
        eventState.tryEmit(EventState.OpenLogs)
      }
    }
  }

  private fun startPolling() {
    pollJob?.cancel()
    pollJob = viewModelScope.launch {
      while (isActive) {
        if (started) {
          val snapshot = logcat?.snapshot(initialSnapshot)
          if (snapshot != null) {
            uiState.update { it.copy(messages = snapshot.messages) }
            initialSnapshot = false
          }
        }
        delay(500.milliseconds)
      }
    }
  }

  private suspend fun bindLogcatService(): LogcatService {
    return suspendCancellableCoroutine { continuation ->
      val connection =
        object : ServiceConnection {
          override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder =
              service
                ?: run {
                  if (!continuation.isActive) {
                    runCatching { application.unbindService(this) }
                      .onFailure { e -> Logger.e("Unbind logcat service failed: ${e.message}", e) }
                    if (conn === this) {
                      conn = null
                    }
                    return
                  }
                  runCatching {
                    continuation.resumeWithException(
                      IllegalStateException("Logcat service returned a null binder"),
                    )
                  }
                    .onFailure {
                      Logger.e("Resume bind failure: ${it.message}", it)
                      runCatching { application.unbindService(this) }
                        .onFailure { e ->
                          Logger.e("Unbind logcat service failed: ${e.message}", e)
                        }
                      if (conn === this) {
                        conn = null
                      }
                    }
                  return
                }
            val logcatService = binder.queryLocalInterface("") as LogcatService

            if (!continuation.isActive) {
              runCatching { application.unbindService(this) }
                .onFailure { e -> Logger.e("Unbind logcat service failed: ${e.message}", e) }
              if (conn === this) {
                conn = null
              }
              return
            }

            runCatching { continuation.resume(logcatService) }
              .onFailure {
                Logger.e("Resume logcat continuation failed: ${it.message}", it)
                runCatching { application.unbindService(this) }
                  .onFailure { e -> Logger.e("Unbind logcat service failed: ${e.message}", e) }
                if (conn === this) {
                  conn = null
                }
              }
          }

          override fun onServiceDisconnected(name: ComponentName?) {
            if (conn === this) {
              conn = null
            }
            logcat = null
          }
        }

      conn = connection

      val bound =
        application.bindService(LogcatService::class.intent, connection, Context.BIND_AUTO_CREATE)

      if (!bound) {
        conn = null
        continuation.resumeWithException(IllegalStateException("Failed to bind logcat service"))
        return@suspendCancellableCoroutine
      }

      continuation.invokeOnCancellation {
        runCatching { application.unbindService(connection) }
          .onFailure { e -> Logger.e("Unbind canceled logcat service failed: ${e.message}", e) }

        if (conn === connection) {
          conn = null
        }
      }
    }
  }

  private suspend fun writeLogTo(messages: List<LogMessage>, file: LogFile, uri: Uri) =
    withContext(Dispatchers.IO) {
      LogcatFilter(
          OutputStreamWriter(checkNotNull(application.contentResolver.openOutputStream(uri))),
          application,
        )
        .use { filter ->
          uiState.update {
            it.copy(
              exportProgress =
                ExportProgress(
                  visible = true,
                  isIndeterminate = true,
                  progress = 0,
                  max = messages.size,
                ),
            )
          }

          try {
            filter.writeHeader(file.created)

            messages.forEachIndexed { index, message ->
              uiState.update {
                it.copy(
                  exportProgress =
                    it.exportProgress.copy(isIndeterminate = false, progress = index + 1),
                )
              }

              filter.writeMessage(message)
            }
          } finally {
            uiState.update { it.copy(exportProgress = ExportProgress()) }
          }
        }
    }

  private fun reset() {
    conn?.let { connection ->
      runCatching { application.unbindService(connection) }
        .onFailure { e -> Logger.e("Unbind logcat service failed: ${e.message}", e) }
    }
    conn = null
    logcat = null
  }

  data class UiState(
    val streaming: Boolean = true,
    val messages: List<LogMessage> = emptyList(),
    val exportProgress: ExportProgress = ExportProgress(),
  )

  data class ExportProgress(
    val visible: Boolean = false,
    val isIndeterminate: Boolean = true,
    val text: String? = null,
    val progress: Int = 0,
    val max: Int = 0,
  )

  sealed interface EventState {
    data object Close : EventState

    data object InvalidFile : EventState

    data object OpenLogs : EventState

    data class RequestExport(val fileName: String) : EventState

    data class ShowMessage(val message: String) : EventState
  }
}
