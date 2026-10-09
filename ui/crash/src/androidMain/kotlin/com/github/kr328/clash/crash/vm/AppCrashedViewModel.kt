package com.github.kr328.clash.crash.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

internal class AppCrashedViewModel(private val application: Application) : ViewModel() {
  val logs: StateFlow<String> = flow {
    val log = runCatching {
      val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
      Logger.i(
        "App version: versionName = ${packageInfo.versionName} versionCode = ${packageInfo.longVersionCode}",
      )
      dumpCrash()
    }
      .getOrElse { e ->
        Logger.e("Failed to load crash logs", e)
        "Failed to load crash logs: ${e.stackTraceToString()}"
      }
    emit(log)
  }
    .stateIn(viewModelScope, SharingStarted.Lazily, "")

  private suspend fun dumpCrash(): String =
    withContext(Dispatchers.IO) {
      val process = ProcessBuilder(*crashDumpCommand).redirectErrorStream(true).start()
      val result =
        process.inputStream
          .bufferedReader()
          .readLines()
          .filterNot { it.startsWith("------") }
          .joinToString("\n")
      val exitCode = process.waitFor()

      if (exitCode != 0) {
        error("logcat exited with code $exitCode: ${result.trim()}")
      }
      result.trim()
    }
}

private val crashDumpCommand =
  arrayOf("logcat", "-d", "-s", "Go", "DEBUG", "AndroidRuntime", "Tabby", "LwIP")
