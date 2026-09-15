package com.github.kr328.clash.service.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import kotlin.uuid.Uuid

@Entity(
  tableName = "selections",
  foreignKeys =
    [
      ForeignKey(
        entity = Imported::class,
        childColumns = ["uuid"],
        parentColumns = ["uuid"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
      )
    ],
  primaryKeys = ["uuid", "proxy"],
)
data class Selection(val uuid: Uuid, val proxy: String, val selected: String)
