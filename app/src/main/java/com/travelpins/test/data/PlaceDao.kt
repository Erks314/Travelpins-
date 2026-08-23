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
    fun getAll(): Flow<List<Place>>

    @Insert
    suspend fun insert(place: Place): Long

    @Insert
    suspend fun insertAll(places: List<Place>)

    @Update
    suspend fun update(place: Place)

    @Delete
    suspend fun delete(place: Place)

    @Query("DELETE FROM places")
    suspend fun deleteAll()
}
