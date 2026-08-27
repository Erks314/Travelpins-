package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlacePhotoDao {

    @Query(
        "SELECT * FROM place_photos " +
                "WHERE placeId = :placeId " +
                "ORDER BY position ASC"
    )
    fun observeByPlace(
        placeId: Long
    ): Flow<List<PlacePhoto>>

    @Query(
        "SELECT * FROM place_photos " +
                "WHERE placeId = :placeId " +
                "ORDER BY position ASC"
    )
    suspend fun getByPlace(
        placeId: Long
    ): List<PlacePhoto>

    @Query(
        "SELECT COUNT(*) FROM place_photos " +
                "WHERE placeId = :placeId"
    )
    suspend fun countByPlace(
        placeId: Long
    ): Int

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertAll(
        photos: List<PlacePhoto>
    ): List<Long>

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insert(
        photo: PlacePhoto
    ): Long

    @Query(
        "DELETE FROM place_photos " +
                "WHERE placeId = :placeId"
    )
    suspend fun deleteByPlace(
        placeId: Long
    )

    @Delete
    suspend fun delete(
        photo: PlacePhoto
    )
}
