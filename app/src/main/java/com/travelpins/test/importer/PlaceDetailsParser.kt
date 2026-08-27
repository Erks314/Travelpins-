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
            val array = JSONArray(rawJson)
            var rating: Double? = null
            var reviewCount: Int? = null
            var description: String? = null
            var websiteUrl: String? = null
            val types = mutableListOf<String>()
            val photos = mutableListOf<PhotoDto>()
            val reviews = mutableListOf<ReviewDto>()
            
            fun walk(node: Any?) {
                if (node == null) return
                if (node is JSONArray) {
                    for (i in 0 until node.length()) {
                        val item = node.opt(i)
                        if (item is String && item.contains("lh3.googleusercontent.com")) {
                            if (photos.none { it.url == item }) {
                                photos.add(PhotoDto(key = "p${photos.size}", url = item, width = null, height = null))
                            }
                        }
                        if (item is Double && item >= 1.0 && item <= 5.0 && rating == null) rating = item
                        if (item is Int && item > 10 && reviewCount == null) reviewCount = item
                        if (item is String && (item.startsWith("http://") || item.startsWith("https://")) && !item.contains("google.com") && websiteUrl == null) {
                            websiteUrl = item
                        }
                        if (item is String && item.length > 50 && !item.contains("http") && !item.contains("lh3")) {
                            if (description == null) {
                                description = item
                            } else if (reviews.size < 5) {
                                reviews.add(ReviewDto(authorName = "Utente Google", authorPhotoUrl = null, rating = 5, timeText = "", reviewText = item.take(500)))
                            }
                        }
                        walk(item)
                    }
                } else if (node is JSONObject) {
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
            }
            walk(array)
            PlaceDetails(name = "", rating = rating, reviewCount = reviewCount, description = description, websiteUrl = websiteUrl, types = types, photos = photos.distinctBy { it.url }.take(10), reviews = reviews)
        } catch (e: Exception) {
            null
        }
    }
}
