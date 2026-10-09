package com.github.kr328.clash.log

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import com.github.kr328.clash.common.R as CommonR
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.running
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.mainIntent
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.glue.util.logsDir
import com.github.kr328.clash.log.util.LogcatCache
import com.github.kr328.clash.log.util.LogcatWriter
import com.github.kr328.clash.service.RemoteService
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.remote.IRemoteService
import com.github.kr328.clash.service.remote.unwrap
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

internal class LogcatService :
  Service(), CoroutineScope by CoroutineScope(Dispatchers.Default), IInterface {
  private val cache = LogcatCache()

  private val connection =
    object : ServiceConnection {
      override fun onServiceDisconnected(name: ComponentName?) {
        stopSelf()
      }

      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        startObserver(service ?: return stopSelf())
      }
    }

  override fun onCreate() {
    super.onCreate()

    running.value = true

    launch {
      createNotificationChannel()
      showNotification()
    }

    bindService(RemoteService::class.intent, connection, BIND_AUTO_CREATE)
  }

  override fun onDestroy() {
    cancel()

    unbindService(connection)

    stopForeground(STOP_FOREGROUND_REMOVE)

    running.value = false

    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder {
    return this.asBinder()
  }

  override fun asBinder(): IBinder {
    return object : Binder() {
      override fun queryLocalInterface(descriptor: String): IInterface {
        return this@LogcatService
      }
    }
  }

  suspend fun snapshot(full: Boolean): LogcatCache.Snapshot? {
    return cache.snapshot(full)
  }

  private fun startObserver(binder: IBinder) {
    if (!binder.isBinderAlive) return stopSelf()

    launch(Dispatchers.IO) {
      val service = binder.unwrap(IRemoteService::class).clash()
      val channel = Channel<LogMessage>(CACHE_CAPACITY)

      try {
        logsDir.mkdirs()

        LogcatWriter(this@LogcatService).use {
          val observer =
            object : ILogObserver {
              override fun newItem(log: LogMessage) {
                channel.trySend(log)
              }
            }

          service.setLogObserver(observer)

          while (isActive) {
            val msg = channel.receive()

            it.appendMessage(msg)

            cache.append(msg)
          }
        }
      } catch (e: IOException) {
        Logger.e("Write log file: $e", e)
      } finally {
        withContext(NonCancellable) {
          if (binder.isBinderAlive) {
            service.setLogObserver(null)
          }

          stopSelf()
        }
      }
    }
  }

  private suspend fun createNotificationChannel() {
    NotificationManagerCompat.from(this)
      .createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
          .setName(getString(Res.string.tabby_logcat))
          .build(),
      )
  }

  private suspend fun showNotification() {
    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(CommonR.drawable.ic_tabby_small)
        .setColor(getColorCompat(CommonR.color.color_tabby_light))
        .setContentTitle(getString(Res.string.tabby_logcat))
        .setContentText(getString(CommonRes.string.running))
        .setContentIntent(
          PendingIntent.getActivity(
            this,
            R.id.nf_logcat_status,
            mainIntent {
              action = Intents.ACTION_LOGCAT
              setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                  Intent.FLAG_ACTIVITY_SINGLE_TOP or
                  Intent.FLAG_ACTIVITY_CLEAR_TOP,
              )
            },
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
          ),
        )
        .build()

    startForegroundCompat(R.id.nf_logcat_status, notification)
  }

  companion object {
    private const val CHANNEL_ID = "clash_logcat_channel"
    private const val CACHE_CAPACITY = 128

    val running: StateFlow<Boolean>
      field = MutableStateFlow(false)
  }
}
