package com.github.kr328.clash.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private const val DATABASE_NAME = "profiles"

fun openDatabase(): Database =
  Room.databaseBuilder<Database>(name = "${documentDirectory()}/$DATABASE_NAME")
    .setDriver(BundledSQLiteDriver())
    .build()

actual fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String = memScoped {
  val error = alloc<ObjCObjectVar<NSError?>>()
  val directory =
    NSFileManager.defaultManager.URLForDirectory(
      NSDocumentDirectory,
      NSUserDomainMask,
      null,
      false,
      error.ptr,
    )
  requireNotNull(directory?.path)
}
