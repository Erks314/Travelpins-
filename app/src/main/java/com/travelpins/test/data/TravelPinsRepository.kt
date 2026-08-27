package com.travelpins.test.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()
    private val placePhotoDao = db.placePhotoDao()
    private val placeReviewDao = db.placeReviewDao()

    val places: Flow<List<Place>> = placeDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()

    fun placesInCategory(categoryId: Long): Flow<List<Place>> =
        placeDao.observeByCategory(categoryId)

    val uncategorizedPlaces: Flow<List<Place>> =
        placeDao.observeUncategorized()

    fun observePlaceById(placeId: Long): Flow<Place?> =
        placeDao.observeById(placeId)

    fun observePhotosByPlace(placeId: Long): Flow<List<PlacePhoto>> =
        placePhotoDao.observeByPlace(placeId)

    fun observeReviewsByPlace(placeId: Long): Flow<List<PlaceReview>> =
        placeReviewDao.observeByPlace(placeId)

    suspend fun saveImportedPlaces(
        places: List<Place>
    ): Int {

        val newPlaces = places.filter { place ->

            val placeId = place.placeId

            if (placeId.isNullOrBlank()) {
                true
            } else {
                placeDao.findByPlaceId(placeId) == null
            }
        }

        if (newPlaces.isEmpty()) {
            return 0
        }

        val inserted =
            placeDao.insertAll(newPlaces)

        return inserted.count { it != -1L }
    }

    suspend fun savePlaceDetails(
        placeId: Long,
        rating: Double?,
        reviewCount: Int?,
        description: String?,
        websiteUrl: String?,
        types: String?
    ) {
        placeDao.updateDetails(
            placeId = placeId,
            rating = rating,
            reviewCount = reviewCount,
            description = description,
            websiteUrl = websiteUrl,
            types = types,
            detailsFetchedAt = System.currentTimeMillis()
        )
    }

    suspend fun savePhotos(
        placeId: Long,
        photos: List<PlacePhoto>
    ): Int {
        if (photos.isEmpty()) return 0
        val inserted = placePhotoDao.insertAll(photos)
        return inserted.count { it != -1L }
    }

    suspend fun saveReviews(
        placeId: Long,
        reviews: List<PlaceReview>
    ): Int {
        if (reviews.isEmpty()) return 0
        val inserted = placeReviewDao.insertAll(reviews)
        return inserted.count { it != -1L }
    }

    suspend fun deletePlacePhotos(placeId: Long) {
        placePhotoDao.deleteByPlace(placeId)
    }

    suspend fun deletePlaceReviews(placeId: Long) {
        placeReviewDao.deleteByPlace(placeId)
    }

    suspend fun getPlaceById(placeId: Long): Place? =
        placeDao.findById(placeId)

    suspend fun getPhotosByPlace(placeId: Long): List<PlacePhoto> =
        placePhotoDao.getByPlace(placeId)

    suspend fun getReviewsByPlace(placeId: Long): List<PlaceReview> =
        placeReviewDao.getByPlace(placeId)

    suspend fun countPhotosByPlace(placeId: Long): Int =
        placePhotoDao.countByPlace(placeId)

    suspend fun countReviewsByPlace(placeId: Long): Int =
        placeReviewDao.countByPlace(placeId)

    suspend fun createCategory(
        name: String,
        colorArgb: Int,
        iconKey: String
    ): Long =
        categoryDao.insert(
            Category(
                name = name,
                colorArgb = colorArgb,
                iconKey = iconKey
            )
        )

    suspend fun assignPlaceToCategory(
        placeId: Long,
        categoryId: Long?
    ) =
        placeDao.assignCategory(
            placeId,
            categoryId
        )

    suspend fun deletePlace(
        place: Place
    ) =
        placeDao.delete(place)

    suspend fun deleteCategory(
        category: Category
    ) =
        categoryDao.delete(category)
}
