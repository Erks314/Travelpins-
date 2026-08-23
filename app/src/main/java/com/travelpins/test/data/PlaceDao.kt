package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE categoryId = :categoryId ORDER BY name ASC")
    fun observeByCategory(categoryId: Long): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE categoryId IS NULL ORDER BY importedAt DESC")
    fun observeUncategorized(): Flow<List<Place>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(places: List<Place>): List<Long>

    @Update
    suspend fun update(place: Place)

    @Query("UPDATE places SET categoryId = :categoryId WHERE id = :placeId")
    suspend fun assignCategory(placeId: Long, categoryId: Long?)

    @Delete
    suspend fun delete(place: Place)

    @Query("SELECT COUNT(*) FROM places WHERE sourceListId = :sourceListId")
    suspend fun countForList(sourceListId: String): Int
}
