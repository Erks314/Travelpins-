package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE categoryId = :categoryId ORDER BY name ASC")
    fun observeByCategory(categoryId: Long): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE categoryId IS NULL ORDER BY name ASC")
    fun observeUncategorized(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE id = :placeId")
    fun observeById(placeId: Long): Flow<Place?>

    @Query("SELECT * FROM places WHERE id = :placeId")
    suspend fun getPlaceById(placeId: Long): Place?

    @Query("SELECT * FROM places WHERE sourceListId = :listId ORDER BY importedAt ASC")
    suspend fun getPlacesByListId(listId: String?): List<Place>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(places: List<Place>): List<Long>

    @Query("UPDATE places SET categoryId = :categoryId WHERE id = :placeId")
    suspend fun assignCategory(placeId: Long, categoryId: Long?)

    @Delete
    suspend fun delete(place: Place)

    @Query("UPDATE places SET detailsFetchedAt = NULL")
    suspend fun resetAllDetailsFetched()

    @Query("UPDATE places SET detailsFetchedAt = NULL WHERE id = :placeId")
    suspend fun clearDetailsFetched(placeId: Long)

    @Query("DELETE FROM places")
    suspend fun deleteAll()

    @Query(
        "UPDATE places SET rating = :rating, reviewCount = :reviewCount, " +
            "description = :description, websiteUrl = :websiteUrl, types = :types, " +
            "detailsFetchedAt = :detailsFetchedAt WHERE id = :placeId"
    )
    suspend fun updateDetails(
        placeId: Long,
        rating: Double?,
        reviewCount: Int?,
        description: String?,
        websiteUrl: String?,
        types: String?,
        detailsFetchedAt: Long
    )

    @Query("UPDATE places SET note = :note WHERE id = :placeId")
    suspend fun updateNote(placeId: Long, note: String?)
},
