package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceReviewDao {

    @Query("SELECT * FROM place_reviews WHERE placeId = :placeId ORDER BY position ASC")
    fun observeByPlace(placeId: Long): Flow<List<PlaceReview>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reviews: List<PlaceReview>): List<Long>

    @Delete
    suspend fun deleteAll(reviews: List<PlaceReview>)
}
