package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceListDao {

    @Query("SELECT * FROM source_lists WHERE id = :id")
    suspend fun getById(id: String): SourceList?

    @Query("SELECT * FROM source_lists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SourceList>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: SourceList)

    @Query("UPDATE source_lists SET placeCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStats(id: String, count: Int, updatedAt: Long)
}
