package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.core.database.Imported
import com.github.kr328.clash.core.database.ImportedDao
import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.service.util.importedDir
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProfileReceiver : BroadcastReceiver(), KoinComponent {
  private val scope: CoroutineScope by inject()

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      Intent.ACTION_TIMEZONE_CHANGED,
      Intent.ACTION_TIME_CHANGED -> {
        scope.launch {
          reset()

          val service =
            Intent(Intents.ACTION_PROFILE_SCHEDULE_UPDATES)
              .setComponent(ProfileWorker::class.componentName)

          context.startForegroundService(service)
        }
      }
      Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
        val redirect = intent.setComponent(ProfileWorker::class.componentName)

        context.startForegroundService(redirect)
      }
    }
  }

  companion object {
    private val lock = Mutex()
    private var initialized: Boolean = false

    suspend fun rescheduleAll(context: Context) = lock.withLock {
      if (initialized) return@withLock

      initialized = true

      Logger.i("Reschedule all profiles update")

      ImportedDao()
        .queryAllUUIDs()
        .mapNotNull { ImportedDao().queryByUUID(it) }
        .filter { it.type != Profile.Type.File }
        .forEach { scheduleNext(context, it) }
    }

    fun cancelNext(context: Context, imported: Imported) {
      val intent = pendingIntentOf(context, imported)

      context.getSystemService<AlarmManager>()?.cancel(intent)
    }

    fun schedule(context: Context, imported: Imported) {
      val intent = pendingIntentOf(context, imported)

      context.getSystemService<AlarmManager>()?.cancel(intent)

      intent.send(context, 0, null)
    }

    fun scheduleNext(context: Context, imported: Imported) {
      val intent = pendingIntentOf(context, imported)

      context.getSystemService<AlarmManager>()?.cancel(intent)

      if (imported.interval < 15.minutes.inWholeMilliseconds) return

      val current = System.currentTimeMillis()
      val last =
        context.importedDir.resolve(imported.uuid.toString()).resolve("config.yaml").lastModified()

      // file not existed
      if (last < 0) return

      val interval = (imported.interval - (current - last)).coerceAtLeast(0)

      context.getSystemService<AlarmManager>()?.set(AlarmManager.RTC, current + interval, intent)
    }

    private suspend fun reset() = lock.withLock { initialized = false }

    private fun pendingIntentOf(context: Context, imported: Imported): PendingIntent {
      val intent =
        Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE)
          .setComponent(ProfileReceiver::class.componentName)
          .setUUID(imported.uuid)

      return PendingIntent.getBroadcast(
        context,
        0,
        intent,
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
      )
    }
  }
}
