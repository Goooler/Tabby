package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.R as CommonR
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.mainIntent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

class DynamicNotificationModule(service: Service) : Module<Unit>(service) {
  private val builder =
    NotificationCompat.Builder(service, StaticNotificationModule.CHANNEL_ID)
      .setSmallIcon(CommonR.drawable.ic_tabby_small)
      .setOngoing(true)
      .setColor(service.getColorCompat(CommonR.color.color_tabby_light))
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setContentTitle("Not Selected")
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(
        PendingIntent.getActivity(
          service,
          R.id.nf_tabby_status,
          mainIntent {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          },
          pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        ),
      )

  private val notificationManager = NotificationManagerCompat.from(service)

  private fun update() {
    val now = Clash.queryTrafficNow()
    val total = Clash.queryTrafficTotal()

    val uploading = now.trafficUpload()
    val downloading = now.trafficDownload()
    val uploaded = total.trafficUpload()
    val downloaded = total.trafficDownload()

    val notification =
      builder
        .setContentText(
          service.getString(R.string.tabby_notification_content, "$uploading/s", "$downloading/s"),
        )
        .setSubText(service.getString(R.string.tabby_notification_content, uploaded, downloaded))
        .build()

    @Suppress("MissingPermission") // We don't care whether the permission is granted.
    notificationManager.notify(R.id.nf_tabby_status, notification)
  }

  override suspend fun run() = coroutineScope {
    var shouldUpdate = service.getSystemService<PowerManager>()?.isInteractive ?: true

    val screenToggle =
      receiveBroadcast(false, Channel.CONFLATED) {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
      }

    val profileLoaded =
      receiveBroadcast(capacity = Channel.CONFLATED) { addAction(Intents.ACTION_PROFILE_LOADED) }

    val ticker = ticker(1.seconds)

    while (true) {
      select<Unit> {
        screenToggle.onReceive {
          when (it.action) {
            Intent.ACTION_SCREEN_ON -> shouldUpdate = true
            Intent.ACTION_SCREEN_OFF -> shouldUpdate = false
          }
        }
        profileLoaded.onReceive {
          builder.setContentTitle(StatusProvider.currentProfile ?: "Not selected")
        }
        if (shouldUpdate) {
          ticker.onReceive { update() }
        }
      }
    }
  }
}
