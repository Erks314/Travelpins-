package com.travelpins.test.data

class TravelPinsRepository(
    private val placeDao: PlaceDao,
    private val categoryDao: CategoryDao
) {
    val places = placeDao.getAll()
    val categories = categoryDao.getAll()

    suspend fun insertPlace(place: Place) =
        placeDao.insert(place)

    suspend fun insertPlaces(places: List<Place>) =
        placeDao.insertAll(places)

    suspend fun updatePlace(place: Place) =
        placeDao.update(place)

    suspend fun deletePlace(place: Place) =
        placeDao.delete(place)

    suspend fun deleteAllPlaces() =
        placeDao.deleteAll()

    suspend fun insertCategory(category: Category) =
        categoryDao.insert(category)

    suspend fun updateCategory(category: Category) =
        categoryDao.update(category)

    suspend fun deleteCategory(category: Category) =
        categoryDao.delete(category)
}
