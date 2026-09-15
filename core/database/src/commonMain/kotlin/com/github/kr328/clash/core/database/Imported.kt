package com.github.kr328.clash.service.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.github.kr328.clash.core.model.Profile
import kotlin.uuid.Uuid

@Entity(tableName = "imported")
data class Imported(
  @PrimaryKey val uuid: Uuid,
  val name: String,
  val type: Profile.Type,
  val source: String,
  val interval: Long,
  val upload: Long,
  val download: Long,
  val total: Long,
  val expire: Long,
  val createdAt: Long,
  val ageSecretKey: String? = null,
)
