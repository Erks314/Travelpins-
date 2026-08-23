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

    val uncategorizedPlaces: Flow<List<Place>> = placeDao.observeUncategorized()

    suspend fun saveImportedPlaces(places: List<Place>): Int {
        val inserted = placeDao.insertAll(places)
        return inserted.size
    }

    suspend fun createCategory(name: String, colorArgb: Int, iconKey: String): Long =
        categoryDao.insert(Category(name = name, colorArgb = colorArgb, iconKey = iconKey))

    suspend fun assignPlaceToCategory(placeId: Long, categoryId: Long?) =
        placeDao.assignCategory(placeId, categoryId)

    suspend fun deletePlace(place: Place) = placeDao.delete(place)

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
}
