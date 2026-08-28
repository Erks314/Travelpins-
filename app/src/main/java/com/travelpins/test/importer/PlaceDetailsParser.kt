package com.travelpins.test.importer

import org.json.JSONArray
import org.json.JSONObject

data class PlaceDetails(
    val name: String,
    val rating: Double?,
    val reviewCount: Int?,
    val description: String?,
    val websiteUrl: String?,
    val types: List<String>,
    val photos: List<PhotoDto>,
    val reviews: List<ReviewDto>
)

data class PhotoDto(val key: String, val url: String, val width: Int?, val height: Int?)
data class ReviewDto(val authorName: String?, val authorPhotoUrl: String?, val rating: Int?, val timeText: String?, val reviewText: String?)

object PlaceDetailsParser {

    fun parse(rawJson: String): PlaceDetails? {
        return try {
            val obj = JSONObject(rawJson)
            
            val photos = mutableListOf<PhotoDto>()
            val photosArray = obj.optJSONArray("photos")
            if (photosArray != null) {
                for (i in 0 until photosArray.length()) {
                    val url = photosArray.getString(i)
                    photos.add(PhotoDto(key = "p$i", url = url, width = null, height = null))
                }
            }
            
            PlaceDetails(
                name = obj.optString("name").takeIf { it.isNotBlank() } ?: "",
                rating = if (obj.isNull("rating")) null else obj.optDouble("rating"),
                reviewCount = if (obj.isNull("reviewCount")) null else obj.optInt("reviewCount"),
                description = obj.optString("description").takeIf { it.isNotBlank() },
                websiteUrl = obj.optString("websiteUrl").takeIf { it.isNotBlank() },
                types = emptyList(),
                photos = photos,
                reviews = emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }
}
