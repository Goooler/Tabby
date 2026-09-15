package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.database.ImportedDao
import com.github.kr328.clash.core.database.PendingDao
import kotlin.uuid.Uuid

suspend fun generateProfileUUID(): Uuid {
  var result = Uuid.random()

  while (ImportedDao().exists(result) || PendingDao().exists(result)) {
    result = Uuid.random()
  }

  return result
}
