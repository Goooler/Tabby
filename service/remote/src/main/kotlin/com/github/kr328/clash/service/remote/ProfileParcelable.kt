package com.github.kr328.clash.service.remote

import android.os.Parcelable
import com.github.kr328.clash.core.model.Profile
import kotlin.uuid.Uuid
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProfileParcelable(
  val uuid: Uuid,
  val name: String,
  val type: String,
  val source: String,
  val active: Boolean,
  val interval: Long,
  val upload: Long,
  val download: Long,
  val total: Long,
  val expire: Long,
  val updatedAt: Long,
  val imported: Boolean,
  val pending: Boolean,
  val ageSecretKey: String?,
) : Parcelable

fun Profile.toParcelable(): ProfileParcelable =
  ProfileParcelable(
    uuid,
    name,
    type.name,
    source,
    active,
    interval,
    upload,
    download,
    total,
    expire,
    updatedAt,
    imported,
    pending,
    ageSecretKey,
  )

fun ProfileParcelable.toProfile(): Profile =
  Profile(
    uuid,
    name,
    Profile.Type.valueOf(type),
    source,
    active,
    interval,
    upload,
    download,
    total,
    expire,
    updatedAt,
    imported,
    pending,
    ageSecretKey,
  )
