package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.core.database.ImportedDao
import com.github.kr328.clash.core.database.Pending
import com.github.kr328.clash.core.database.PendingDao
import com.github.kr328.clash.core.database.initializeDatabase
import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.remote.ProfileParcelable
import com.github.kr328.clash.service.remote.toParcelable
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.directoryLastModified
import com.github.kr328.clash.service.util.generateProfileUUID
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import java.io.FileNotFoundException
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileManager(private val context: Context) :
  IProfileManager, CoroutineScope by CoroutineScope(Dispatchers.IO) {
  private val store = ServiceStore(context)

  init {
    launch {
      initializeDatabase(context)

      ProfileReceiver.rescheduleAll(context)
    }
  }

  override suspend fun create(
    type: String,
    name: String,
    source: String,
    ageSecretKey: String?,
  ): Uuid {
    val uuid = generateProfileUUID()
    val pending =
      Pending(
        uuid = uuid,
        name = name,
        type = Profile.Type.valueOf(type),
        source = source,
        interval = 0,
        upload = 0,
        total = 0,
        download = 0,
        expire = 0,
        ageSecretKey = ageSecretKey,
      )

    PendingDao().insert(pending)

    context.pendingDir.resolve(uuid.toString()).apply {
      deleteRecursively()
      mkdirs()

      resolve("config.yaml").createNewFile()
      resolve("providers").mkdir()
    }

    return uuid
  }

  override suspend fun clone(uuid: Uuid): Uuid {
    val newUUID = generateProfileUUID()

    val imported =
      ImportedDao().queryByUUID(uuid) ?: throw FileNotFoundException("profile $uuid not found")

    val pending =
      Pending(
        uuid = newUUID,
        name = imported.name,
        type = Profile.Type.File,
        source = imported.source,
        interval = imported.interval,
        upload = imported.upload,
        total = imported.total,
        download = imported.download,
        expire = imported.expire,
        ageSecretKey = imported.ageSecretKey,
      )

    cloneImportedFiles(uuid, newUUID)

    PendingDao().insert(pending)

    return newUUID
  }

  override suspend fun patch(
    uuid: Uuid,
    name: String,
    source: String,
    interval: Long,
    ageSecretKey: String?,
  ) {
    val pending = PendingDao().queryByUUID(uuid)

    if (pending == null) {
      val imported =
        ImportedDao().queryByUUID(uuid) ?: throw FileNotFoundException("profile $uuid not found")

      cloneImportedFiles(uuid)

      PendingDao()
        .insert(
          Pending(
            uuid = imported.uuid,
            name = name,
            type = imported.type,
            source = source,
            interval = interval,
            upload = 0,
            total = 0,
            download = 0,
            expire = 0,
            ageSecretKey = ageSecretKey,
          )
        )
    } else {
      val newPending =
        pending.copy(
          name = name,
          source = source,
          interval = interval,
          upload = 0,
          total = 0,
          download = 0,
          expire = 0,
          ageSecretKey = ageSecretKey,
        )

      PendingDao().update(newPending)
    }
  }

  override suspend fun update(uuid: Uuid) {
    scheduleUpdate(uuid, true)
  }

  override suspend fun commit(uuid: Uuid, callback: IFetchObserver?) {
    ProfileProcessor.apply(context, uuid, callback)

    scheduleUpdate(uuid, false)
  }

  override suspend fun release(uuid: Uuid) {
    ProfileProcessor.release(context, uuid)
  }

  override suspend fun delete(uuid: Uuid) {
    ImportedDao().queryByUUID(uuid)?.also { ProfileReceiver.cancelNext(context, it) }

    ProfileProcessor.delete(context, uuid)
  }

  override suspend fun queryByUUID(uuid: Uuid): ProfileParcelable? {
    return resolveProfile(uuid)?.toParcelable()
  }

  override suspend fun queryAll(): List<ProfileParcelable> {
    val uuids =
      withContext(Dispatchers.IO) {
        (ImportedDao().queryAllUUIDs() + PendingDao().queryAllUUIDs()).distinct()
      }

    return uuids.mapNotNull { resolveProfile(it)?.toParcelable() }
  }

  override suspend fun queryActive(): ProfileParcelable? {
    val active = store.activeProfile ?: return null

    return if (ImportedDao().exists(active)) {
      resolveProfile(active)?.toParcelable()
    } else {
      null
    }
  }

  override suspend fun setActive(profile: ProfileParcelable) {
    ProfileProcessor.active(context, profile.uuid)
  }

  private suspend fun resolveProfile(uuid: Uuid): Profile? {
    val imported = ImportedDao().queryByUUID(uuid)
    val pending = PendingDao().queryByUUID(uuid)

    val active = store.activeProfile
    val name = pending?.name ?: imported?.name ?: return null
    val type = pending?.type ?: imported?.type ?: return null
    val source = pending?.source ?: imported?.source ?: return null
    val interval = pending?.interval ?: imported?.interval ?: return null
    val upload = pending?.upload ?: imported?.upload ?: return null
    val download = pending?.download ?: imported?.download ?: return null
    val total = pending?.total ?: imported?.total ?: return null
    val expire = pending?.expire ?: imported?.expire ?: return null

    return Profile(
      uuid,
      name,
      type,
      source,
      active != null && imported?.uuid == active,
      interval,
      upload,
      download,
      total,
      expire,
      resolveUpdatedAt(uuid),
      imported != null,
      pending != null,
      ageSecretKey = if (pending != null) pending.ageSecretKey else imported?.ageSecretKey,
    )
  }

  private fun resolveUpdatedAt(uuid: Uuid): Long {
    return context.pendingDir.resolve(uuid.toString()).directoryLastModified
      ?: context.importedDir.resolve(uuid.toString()).directoryLastModified
      ?: -1
  }

  private fun cloneImportedFiles(source: Uuid, target: Uuid = source) {
    val s = context.importedDir.resolve(source.toString())
    val t = context.pendingDir.resolve(target.toString())

    if (!s.exists()) throw FileNotFoundException("profile $source not found")

    t.deleteRecursively()

    s.copyRecursively(t)
  }

  private suspend fun scheduleUpdate(uuid: Uuid, startImmediately: Boolean) {
    val imported = ImportedDao().queryByUUID(uuid) ?: return

    if (startImmediately) {
      ProfileReceiver.schedule(context, imported)
    } else {
      ProfileReceiver.scheduleNext(context, imported)
    }
  }
}
