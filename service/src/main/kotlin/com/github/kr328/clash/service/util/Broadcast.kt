package com.github.kr328.clash.service.util

import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.di.AppInfoProvider
import kotlin.uuid.Uuid

fun Context.sendBroadcastSelf(intent: Intent) {
  sendBroadcast(intent.setPackage(this.packageName), AppInfoProvider().receiveBroadcastsPermission)
}

fun Context.sendProfileChanged(uuid: Uuid) {
  val intent = Intent(Intents.ACTION_PROFILE_CHANGED).putExtra(Intents.EXTRA_UUID, uuid)

  sendBroadcastSelf(intent)
}

fun Context.sendProfileLoaded(uuid: Uuid) {
  val intent = Intent(Intents.ACTION_PROFILE_LOADED).putExtra(Intents.EXTRA_UUID, uuid)

  sendBroadcastSelf(intent)
}

fun Context.sendProfileUpdateCompleted(uuid: Uuid) {
  val intent = Intent(Intents.ACTION_PROFILE_UPDATE_COMPLETED).putExtra(Intents.EXTRA_UUID, uuid)

  sendBroadcastSelf(intent)
}

fun Context.sendProfileUpdateFailed(uuid: Uuid, reason: String) {
  val intent =
    Intent(Intents.ACTION_PROFILE_UPDATE_FAILED)
      .putExtra(Intents.EXTRA_UUID, uuid)
      .putExtra(Intents.EXTRA_FAIL_REASON, reason)

  sendBroadcastSelf(intent)
}

fun Context.sendOverrideChanged() {
  val intent = Intent(Intents.ACTION_OVERRIDE_CHANGED)

  sendBroadcastSelf(intent)
}

fun Context.sendServiceRecreated() {
  sendBroadcastSelf(Intent(Intents.ACTION_SERVICE_RECREATED))
}

fun Context.sendClashStarted() {
  sendBroadcastSelf(Intent(Intents.ACTION_CLASH_STARTED))
}

fun Context.sendClashStopped(reason: String?) {
  sendBroadcastSelf(
    Intent(Intents.ACTION_CLASH_STOPPED).putExtra(Intents.EXTRA_STOP_REASON, reason),
  )
}
