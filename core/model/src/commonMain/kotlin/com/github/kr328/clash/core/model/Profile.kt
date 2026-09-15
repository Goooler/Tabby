package com.github.kr328.clash.core.model

import android.os.Parcelable
import kotlin.uuid.Uuid
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Profile(
  val uuid: Uuid,
  val name: String,
  val type: Type,
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
  val ageSecretKey: String? = null,
) : Parcelable {
  enum class Type {
    File,
    Url,
    External,
  }
}
