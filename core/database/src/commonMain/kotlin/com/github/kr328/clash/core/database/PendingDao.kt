package com.github.kr328.clash.service.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlin.uuid.Uuid

@Dao
interface PendingDao {
  @Query("SELECT * FROM pending WHERE uuid = :uuid") suspend fun queryByUUID(uuid: Uuid): Pending?

  @Query("DELETE FROM pending WHERE uuid = :uuid") suspend fun remove(uuid: Uuid)

  @Query("SELECT EXISTS(SELECT 1 FROM pending WHERE uuid = :uuid)")
  suspend fun exists(uuid: Uuid): Boolean

  @Query("SELECT uuid FROM pending ORDER BY createdAt") suspend fun queryAllUUIDs(): List<Uuid>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(pending: Pending)

  @Update(onConflict = OnConflictStrategy.REPLACE) suspend fun update(pending: Pending)
}
