package com.github.kr328.clash.service.remote

import com.github.kr328.kaidl.BinderInterface
import kotlin.uuid.Uuid

@BinderInterface
interface IProfileManager {
  suspend fun create(
    type: String,
    name: String,
    source: String = "",
    ageSecretKey: String? = null,
  ): Uuid

  suspend fun clone(uuid: Uuid): Uuid

  suspend fun commit(uuid: Uuid, callback: IFetchObserver? = null)

  suspend fun release(uuid: Uuid)

  suspend fun delete(uuid: Uuid)

  suspend fun patch(
    uuid: Uuid,
    name: String,
    source: String,
    interval: Long,
    ageSecretKey: String? = null,
  )

  suspend fun update(uuid: Uuid)

  suspend fun queryByUUID(uuid: Uuid): ProfileParcelable?

  suspend fun queryAll(): List<ProfileParcelable>

  suspend fun queryActive(): ProfileParcelable?

  suspend fun setActive(profile: ProfileParcelable)
}
