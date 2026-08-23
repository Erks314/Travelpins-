package com.travelpins.test.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)

    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()

    val places: Flow<List<Place>> = placeDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()

    suspend fun saveImportedPlaces(places: List<Place>): Int {
        if (places.isEmpty()) return 0

        placeDao.insertAll(places)
        return places.size
    }

    suspend fun insertPlace(place: Place): Long {
        return placeDao.insert(place)
    }

    suspend fun updatePlace(place: Place) {
        placeDao.update(place)
    }

    suspend fun deletePlace(place: Place) {
        placeDao.delete(place)
    }

    suspend fun deleteAllPlaces() {
        placeDao.deleteAll()
    }

    suspend fun insertCategory(category: Category): Long {
        return categoryDao.insert(category)
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.update(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.delete(category)
    }

    suspend fun getPlacesBySourceListId(sourceListId: String): List<Place> {
        return placeDao.getBySourceListId(sourceListId)
    }
}
