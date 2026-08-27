package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceReviewDao {

    @Query(
        "SELECT * FROM place_reviews " +
                "WHERE placeId = :placeId " +
                "ORDER BY position ASC"
    )
    fun observeByPlace(
        placeId: Long
    ): Flow<List<PlaceReview>>

    @Query(
        "SELECT * FROM place_reviews " +
                "WHERE placeId = :placeId " +
                "ORDER BY position ASC"
    )
    suspend fun getByPlace(
        placeId: Long
    ): List<PlaceReview>

    @Query(
        "SELECT COUNT(*) FROM place_reviews " +
                "WHERE placeId = :placeId"
    )
    suspend fun countByPlace(
        placeId: Long
    ): Int

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertAll(
        reviews: List<PlaceReview>
    ): List<Long>

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insert(
        review: PlaceReview
    ): Long

    @Query(
        "DELETE FROM place_reviews " +
                "WHERE placeId = :placeId"
    )
    suspend fun deleteByPlace(
        placeId: Long
    )

    @Delete
    suspend fun delete(
        review: PlaceReview
    )
}
