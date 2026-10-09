package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.R as CommonR
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.mainIntent
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlinx.coroutines.channels.Channel

class StaticNotificationModule(service: Service) : Module<Unit>(service) {
  private val builder =
    NotificationCompat.Builder(service, CHANNEL_ID)
      .setSmallIcon(CommonR.drawable.ic_tabby_small)
      .setOngoing(true)
      .setColor(service.getColorCompat(CommonR.color.color_tabby_light))
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
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

  override suspend fun run() {
    val loaded =
      receiveBroadcast(capacity = Channel.CONFLATED) { addAction(Intents.ACTION_PROFILE_LOADED) }

    while (true) {
      loaded.receive()

      val profileName = StatusProvider.currentProfile ?: "Not selected"

      val notification =
        builder
          .setContentTitle(profileName)
          .setContentText(service.getText(R.string.running))
          .build()

      service.startForegroundCompat(R.id.nf_tabby_status, notification)
    }
  }

  companion object {
    const val CHANNEL_ID = "clash_status_channel"

    fun createNotificationChannel(service: Service) {
      NotificationManagerCompat.from(service)
        .createNotificationChannel(
          NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(service.getText(R.string.tabby_service_status_channel))
            .build(),
        )
    }

    fun notifyLoadingNotification(service: Service) {
      val notification =
        NotificationCompat.Builder(service, CHANNEL_ID)
          .setSmallIcon(CommonR.drawable.ic_tabby_small)
          .setOngoing(true)
          .setColor(service.getColorCompat(CommonR.color.color_tabby_light))
          .setOnlyAlertOnce(true)
          .setShowWhen(false)
          .setContentTitle(service.getText(R.string.loading))
          .build()

      service.startForegroundCompat(R.id.nf_tabby_status, notification)
    }
  }
}
