package com.github.kr328.clash.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.BetaInteropApi
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

@OptIn(kotlin.time.ExperimentalTime::class)
actual fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
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
