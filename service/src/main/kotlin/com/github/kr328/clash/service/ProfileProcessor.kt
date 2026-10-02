package com.github.kr328.clash.service

import android.content.Context
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.database.Imported
import com.github.kr328.clash.core.database.ImportedDao
import com.github.kr328.clash.core.database.Pending
import com.github.kr328.clash.core.database.PendingDao
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object ProfileProcessor {
  private val profileLock = Mutex()
  private val processLock = Mutex()

  suspend fun apply(context: Context, uuid: Uuid, callback: IFetchObserver? = null) {
    withContext(NonCancellable) {
      processLock.withLock {
        val snapshot = profileLock.withLock {
          val pending =
            PendingDao().queryByUUID(uuid)
              ?: throw IllegalArgumentException("profile $uuid not found")

          pending.enforceFieldValid()

          context.processingDir.deleteRecursively()
          context.processingDir.mkdirs()

          context.pendingDir
            .resolve(pending.uuid.toString())
            .copyRecursively(context.processingDir, overwrite = true)

          pending
        }

        val force = snapshot.type != Profile.Type.File
        val subscriptionInfo =
          fetchProfile(context, snapshot.source, force, snapshot.ageSecretKey, callback)

        profileLock.withLock {
          if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
            context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
            context.processingDir.copyRecursively(
              context.importedDir.resolve(snapshot.uuid.toString())
            )

            val old = ImportedDao().queryByUUID(snapshot.uuid)
            val updateInterval =
              subscriptionInfo?.subUpdateInterval?.takeIf { old == null && snapshot.interval == 0L }
                ?: snapshot.interval

            val new =
              Imported(
                snapshot.uuid,
                snapshot.name,
                snapshot.type,
                snapshot.source,
                updateInterval,
                subscriptionInfo?.subUpload ?: 0,
                subscriptionInfo?.subDownload ?: 0,
                subscriptionInfo?.subTotal ?: 0,
                subscriptionInfo?.subExpire ?: 0,
                old?.createdAt ?: System.currentTimeMillis(),
                ageSecretKey = snapshot.ageSecretKey,
              )

            if (old != null) {
              ImportedDao().update(new)
            } else {
              ImportedDao().insert(new)
            }

            PendingDao().remove(snapshot.uuid)

            context.pendingDir.resolve(snapshot.uuid.toString()).deleteRecursively()

            context.sendProfileChanged(snapshot.uuid)
          }
        }
      }
    }
  }

  suspend fun update(context: Context, uuid: Uuid, callback: IFetchObserver?) {
    withContext(NonCancellable) {
      processLock.withLock {
        val snapshot = profileLock.withLock {
          val imported =
            ImportedDao().queryByUUID(uuid)
              ?: throw IllegalArgumentException("profile $uuid not found")

          context.processingDir.deleteRecursively()
          context.processingDir.mkdirs()

          context.importedDir
            .resolve(imported.uuid.toString())
            .copyRecursively(context.processingDir, overwrite = true)

          imported
        }

        val subscriptionInfo =
          fetchProfile(context, snapshot.source, true, snapshot.ageSecretKey, callback)

        profileLock.withLock {
          val imported = ImportedDao().queryByUUID(snapshot.uuid)
          if (imported != null) {
            context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
            context.processingDir.copyRecursively(
              context.importedDir.resolve(snapshot.uuid.toString())
            )

            if (subscriptionInfo != null && subscriptionInfo.subTotal > 0) {
              ImportedDao()
                .update(
                  imported.copy(
                    upload = subscriptionInfo.subUpload,
                    download = subscriptionInfo.subDownload,
                    total = subscriptionInfo.subTotal,
                    expire = subscriptionInfo.subExpire,
                  )
                )
            }

            context.sendProfileChanged(snapshot.uuid)
          }
        }
      }
    }
  }

  private suspend fun fetchProfile(
    context: Context,
    source: String,
    force: Boolean,
    ageSecretKey: String?,
    callback: IFetchObserver?,
  ): FetchStatus? {
    var subscriptionInfo: FetchStatus? = null
    var cb = callback

    Clash.fetchAndValid(
      context.processingDir,
      source,
      force,
      ageSecretKey?.takeIf { it.isNotBlank() },
    ) {
      if (it.action == FetchStatus.Action.SubscriptionInfo) {
        subscriptionInfo = it
        return@fetchAndValid
      }

      try {
        cb?.updateStatus(it)
      } catch (e: Exception) {
        cb = null

        Logger.w("Report fetch status: $e", e)
      }
    }

    return subscriptionInfo
  }

  suspend fun delete(context: Context, uuid: Uuid) {
    withContext(NonCancellable) {
      profileLock.withLock {
        ImportedDao().remove(uuid)
        PendingDao().remove(uuid)

        val pending = context.pendingDir.resolve(uuid.toString())
        val imported = context.importedDir.resolve(uuid.toString())

        pending.deleteRecursively()
        imported.deleteRecursively()

        context.sendProfileChanged(uuid)
      }
    }
  }

  suspend fun release(context: Context, uuid: Uuid): Boolean {
    return withContext(NonCancellable) {
      profileLock.withLock {
        PendingDao().remove(uuid)

        context.pendingDir.resolve(uuid.toString()).deleteRecursively()
      }
    }
  }

  suspend fun active(context: Context, uuid: Uuid) {
    withContext(NonCancellable) {
      profileLock.withLock {
        if (ImportedDao().exists(uuid)) {
          val store = ServiceStore(context)

          store.activeProfile = uuid

          context.sendProfileChanged(uuid)
        }
      }
    }
  }

  private fun Pending.enforceFieldValid() {
    val scheme = source.toUri().scheme?.lowercase(Locale.getDefault())

    when {
      name.isBlank() -> throw IllegalArgumentException("Empty name")

      source.isEmpty() && type != Profile.Type.File -> throw IllegalArgumentException("Invalid url")

      source.isNotEmpty() && scheme != "https" && scheme != "http" && scheme != "content" ->
        throw IllegalArgumentException("Unsupported url $source")

      interval != 0L && interval.milliseconds.inWholeMinutes < 15 ->
        throw IllegalArgumentException("Invalid interval")
    }
  }
}
