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

    @Query(
        "SELECT * FROM places " +
                "ORDER BY importedAt DESC"
    )
    fun observeAll(): Flow<List<Place>>

    @Query(
        "SELECT * FROM places " +
                "ORDER BY importedAt DESC"
    )
    suspend fun observeAllOnce(): List<Place>

    @Query(
        "SELECT * FROM places " +
                "WHERE categoryId = :categoryId " +
                "ORDER BY name ASC"
    )
    fun observeByCategory(
        categoryId: Long
    ): Flow<List<Place>>

    @Query(
        "SELECT * FROM places " +
                "WHERE categoryId IS NULL " +
                "ORDER BY importedAt DESC"
    )
    fun observeUncategorized(): Flow<List<Place>>

    @Query(
        "SELECT * FROM places " +
                "WHERE placeId = :placeId " +
                "LIMIT 1"
    )
    suspend fun findByPlaceId(
        placeId: String
    ): Place?

    @Query(
        "SELECT * FROM places " +
                "WHERE placeId = :placeId " +
                "AND sourceListId = :sourceListId " +
                "LIMIT 1"
    )
    suspend fun findByPlaceIdInList(
        placeId: String,
        sourceListId: String
    ): Place?

    @Query(
        "SELECT * FROM places " +
                "WHERE id = :placeId " +
                "LIMIT 1"
    )
    suspend fun findById(
        placeId: Long
    ): Place?

    @Query(
        "SELECT * FROM places " +
                "WHERE id = :placeId " +
                "LIMIT 1"
    )
    fun observeById(
        placeId: Long
    ): Flow<Place?>

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertAll(
        places: List<Place>
    ): List<Long>

    @Update
    suspend fun update(
        place: Place
    )

    @Query(
        "UPDATE places " +
                "SET categoryId = :categoryId " +
                "WHERE id = :placeId"
    )
    suspend fun assignCategory(
        placeId: Long,
        categoryId: Long?
    )

    @Query(
        "UPDATE places " +
                "SET rating = :rating, " +
                "reviewCount = :reviewCount, " +
                "description = :description, " +
                "websiteUrl = :websiteUrl, " +
                "types = :types, " +
                "detailsFetchedAt = :detailsFetchedAt " +
                "WHERE id = :placeId"
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

    @Delete
    suspend fun delete(
        place: Place
    )

    @Query(
        "DELETE FROM places " +
                "WHERE id = :placeId"
    )
    suspend fun deleteById(
        placeId: Long
    )

    @Query(
        "SELECT COUNT(*) FROM places " +
                "WHERE sourceListId = :sourceListId"
    )
    suspend fun countForList(
        sourceListId: String
    ): Int
}
