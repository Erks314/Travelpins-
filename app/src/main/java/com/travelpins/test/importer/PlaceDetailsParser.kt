package com.travelpins.test.importer

import org.json.JSONObject

data class PlaceDetails(
    val name: String,
    val ref: String?,
    val rating: Double?,
    val reviewCount: Int?,
    val description: String?,
    val websiteUrl: String?,
    val types: List<String>,
    val photos: List<PhotoDto>,
    val reviews: List<ReviewDto>
)

data class PhotoDto(
    val key: String,
    val url: String,
    val width: Int?,
    val height: Int?
)

data class ReviewDto(
    val authorName: String?,
    val authorPhotoUrl: String?,
    val rating: Int?,
    val timeText: String?,
    val reviewText: String?
)

object PlaceDetailsParser {

    fun parse(json: String): PlaceDetails? {
        return try {
            val obj = JSONObject(json)

            val name = obj.optString("name", "")

            val ref = obj.optString("ref", "")
                .takeIf { it.isNotBlank() }

            val rating = if (obj.isNull("rating")) null
            else obj.optDouble("rating", Double.NaN)
                .takeIf { !it.isNaN() }

            val reviewCount = if (obj.isNull("reviewCount")) null
            else obj.optInt("reviewCount", -1)
                .takeIf { it >= 0 }

            val description = obj.optString("description", "")
                .takeIf { it.isNotBlank() }

            val websiteUrl = obj.optString("websiteUrl", "")
                .takeIf { it.isNotBlank() }

            val types = mutableListOf<String>()
            val typesArray = obj.optJSONArray("types")
            if (typesArray != null) {
                for (i in 0 until typesArray.length()) {
                    val t = typesArray.optString(i, "")
                    if (t.isNotBlank()) types.add(t)
                }
            }

            val photos = mutableListOf<PhotoDto>()
            val photosArray = obj.optJSONArray("photos")
            if (photosArray != null) {
                for (i in 0 until photosArray.length()) {
                    val p = photosArray.optJSONObject(i) ?: continue
                    val key = p.optString("key", "")
                    val url = p.optString("url", "")
                    if (key.isBlank() || url.isBlank()) continue
                    photos.add(
                        PhotoDto(
                            key = key,
                            url = url,
                            width = if (p.isNull("w")) null else p.optInt("w"),
                            height = if (p.isNull("h")) null else p.optInt("h")
                        )
                    )
                }
            }

            val reviews = mutableListOf<ReviewDto>()
            val reviewsArray = obj.optJSONArray("reviews")
            if (reviewsArray != null) {
                for (i in 0 until reviewsArray.length()) {
                    val r = reviewsArray.optJSONObject(i) ?: continue
                    val text = r.optString("text", "")
                    if (text.isBlank()) continue
                    reviews.add(
                        ReviewDto(
                            authorName = r.optString("author", "")
                                .takeIf { it.isNotBlank() },
                            authorPhotoUrl = r.optString("photo", "")
                                .takeIf { it.isNotBlank() },
                            rating = if (r.isNull("rating")) null
                            else r.optInt("rating", -1).takeIf { it in 1..5 },
                            timeText = r.optString("time", "")
                                .takeIf { it.isNotBlank() },
                            reviewText = text
                        )
                    )
                }
            }

            PlaceDetails(
                name = name,
                ref = ref,
                rating = rating,
                reviewCount = reviewCount,
                description = description,
                websiteUrl = websiteUrl,
                types = types,
                photos = photos,
                reviews = reviews
            )
        } catch (_: Exception) {
            null
        }
    }
}
