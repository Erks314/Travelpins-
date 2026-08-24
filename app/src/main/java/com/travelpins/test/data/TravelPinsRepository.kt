package com.travelpins.test.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()

    val places: Flow<List<Place>> = placeDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()

    fun placesInCategory(categoryId: Long): Flow<List<Place>> =
        placeDao.observeByCategory(categoryId)

    val uncategorizedPlaces: Flow<List<Place>> =
        placeDao.observeUncategorized()

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
