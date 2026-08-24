package com.travelpins.test.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()

    val places: Flow<List<Place>> =
        placeDao.observeAll()

    val categories: Flow<List<Category>> =
        categoryDao.observeAll()

    fun placesInCategory(
        categoryId: Long
    ): Flow<List<Place>> =
        placeDao.observeByCategory(categoryId)

    val uncategorizedPlaces: Flow<List<Place>> =
        placeDao.observeUncategorized()

    suspend fun saveImportedPlaces(
        places: List<Place>
    ): Int {

        var savedCount = 0

        for (place in places) {

            if (!place.placeId.isNullOrBlank()) {

                val existing =
                    placeDao.findByPlaceId(
                        place.placeId
                    )

                if (existing != null) {
                    continue
                }
            }

            val result =
                placeDao.insertAll(
                    listOf(place)
                )

            if (
                result.isNotEmpty() &&
                result.first() != -1L
            ) {
                savedCount++
            }
        }

        return savedCount
    }

    suspend fun cleanDuplicatePlaces(): Int {

        val allPlaces =
            placeDao.observeAllOnce()

        val groups =
            allPlaces
                .filter {
                    !it.placeId.isNullOrBlank()
                }
                .groupBy {
                    it.placeId
                }

        var deletedCount = 0

        groups.values.forEach { duplicates ->

            if (duplicates.size <= 1) {
                return@forEach
            }

            val keep =
                duplicates
                    .sortedWith(
                        compareByDescending<Place> {
                            it.categoryId != null
                        }.thenByDescending {
                            it.importedAt
                        }
                    )
                    .first()

            duplicates
                .filter {
                    it.id != keep.id
                }
                .forEach { duplicate ->

                    placeDao.deleteById(
                        duplicate.id
                    )

                    deletedCount++
                }
        }

        return deletedCount
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
