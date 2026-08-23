package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY name ASC")
    fun observeAll(): Flow<List<Place>>

    @Insert
    suspend fun insertAll(places: List<Place>): List<Long>

    @Insert
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Delete
    suspend fun delete(place: Place)

    @Query("DELETE FROM places")
    suspend fun deleteAll()

    @Query("SELECT * FROM places WHERE sourceListId = :sourceListId ORDER BY name ASC")
    suspend fun getBySourceListId(sourceListId: String): List<Place>
}
