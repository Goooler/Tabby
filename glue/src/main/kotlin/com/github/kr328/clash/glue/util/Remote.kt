package com.github.kr328.clash.glue.util

import android.os.DeadObjectException
import co.touchlab.kermit.Logger
import com.github.kr328.clash.glue.remote.ProfileClient
import com.github.kr328.clash.glue.remote.Remote
import com.github.kr328.clash.service.remote.IClashManager
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> withClash(
  context: CoroutineContext = Dispatchers.IO,
  block: suspend IClashManager.() -> T,
): T {
  while (true) {
    val remote = Remote.service.remote.get()
    val client = remote.clash()

    try {
      return withContext(context) { client.block() }
    } catch (_: DeadObjectException) {
      Logger.w("Remote services panic")

      Remote.service.remote.reset(remote)
    }
  }
}

suspend fun <T> withProfile(
  context: CoroutineContext = Dispatchers.IO,
  block: suspend ProfileClient.() -> T,
): T {
  while (true) {
    val remote = Remote.service.remote.get()
    val client = ProfileClient(remote.profile())

    try {
      return withContext(context) { client.block() }
    } catch (_: DeadObjectException) {
      Logger.w("Remote services panic")

      Remote.service.remote.reset(remote)
    }
  }
}
