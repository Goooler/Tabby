package com.github.kr328.clash.glue.remote

import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.remote.toParcelable
import com.github.kr328.clash.service.remote.toProfile
import kotlin.uuid.Uuid

class ProfileClient(private val remote: IProfileManager) {
  suspend fun create(
    type: Profile.Type,
    name: String,
    source: String = "",
    ageSecretKey: String? = null,
  ): Uuid = remote.create(type.name, name, source, ageSecretKey)

  suspend fun clone(uuid: Uuid): Uuid = remote.clone(uuid)

  suspend fun commit(uuid: Uuid, callback: IFetchObserver? = null) = remote.commit(uuid, callback)

  suspend fun release(uuid: Uuid) = remote.release(uuid)

  suspend fun delete(uuid: Uuid) = remote.delete(uuid)

  suspend fun patch(
    uuid: Uuid,
    name: String,
    source: String,
    interval: Long,
    ageSecretKey: String? = null,
  ) = remote.patch(uuid, name, source, interval, ageSecretKey)

  suspend fun update(uuid: Uuid) = remote.update(uuid)

  suspend fun queryByUUID(uuid: Uuid): Profile? = remote.queryByUUID(uuid)?.toProfile()

  suspend fun queryAll(): List<Profile> = remote.queryAll().map { it.toProfile() }

  suspend fun queryActive(): Profile? = remote.queryActive()?.toProfile()

  suspend fun setActive(profile: Profile) = remote.setActive(profile.toParcelable())
}
