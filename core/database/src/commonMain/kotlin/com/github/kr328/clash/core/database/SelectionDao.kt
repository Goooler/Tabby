package com.github.kr328.clash.service.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlin.uuid.Uuid

@Dao
interface SelectionDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setSelected(selection: Selection)

  @Query("DELETE FROM selections WHERE uuid = :uuid AND proxy = :proxy")
  suspend fun removeSelected(uuid: Uuid, proxy: String)

  @Query("SELECT * FROM selections WHERE uuid = :uuid")
  suspend fun querySelections(uuid: Uuid): List<Selection>

  @Query("DELETE FROM selections WHERE uuid = :uuid AND proxy in (:proxies)")
  suspend fun removeSelections(uuid: Uuid, proxies: List<String>)
}
