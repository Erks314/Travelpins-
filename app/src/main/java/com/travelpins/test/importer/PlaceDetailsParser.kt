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
data class ReviewDto(
    val authorName: String?, 
    val authorPhotoUrl: String?, 
    val rating: Int?, 
    val timeText: String?, 
    val reviewText: String?
)

object PlaceDetailsParser {

    fun parse(rawJson: String): PlaceDetails? {
        return try {
            val rootArray = JSONArray(rawJson)
            
            var placeDataArray: JSONArray? = null
            for (i in 0 until minOf(rootArray.length(), 20)) {
                val item = rootArray.opt(i)
                if (item is JSONArray && item.length() > 15) {
                    if (containsPlaceData(item)) {
                        placeDataArray = item
                        break
                    }
                }
            }
            
            if (placeDataArray == null) return null
            
            val name = extractString(placeDataArray, "name")
            val rating = extractDoubleInRange(placeDataArray, 1.0, 5.0)
            val reviewCount = extractIntInRange(placeDataArray, 1, 1000000)
            val websiteUrl = extractWebsite(placeDataArray)
            val types = extractTypes(placeDataArray)
            val description = extractDescription(placeDataArray)
            
            val photos = extractPhotos(rootArray)
            val reviews = extractReviews(rootArray)
            
            PlaceDetails(
                name = name,
                rating = rating,
                reviewCount = reviewCount,
                description = description,
                websiteUrl = websiteUrl,
                types = types,
                photos = photos.take(10),
                reviews = reviews.take(5)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun containsPlaceData(array: JSONArray): Boolean {
        var hasName = false
        var hasRating = false
        
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is String && item.length > 3 && item.length < 100 && 
                !item.startsWith("http") && !item.startsWith("0x") &&
                !item.contains("google") && !item.contains("0ahUKE")) {
                hasName = true
            }
            if (item is Double && item >= 1.0 && item <= 5.0) {
                hasRating = true
            }
        }
        
        return hasName && hasRating
    }
    
    private fun extractPhotos(root: JSONArray): List<PhotoDto> {
        val photos = mutableListOf<PhotoDto>()
        val seen = mutableSetOf<String>()
        
        fun walk(node: Any?) {
            if (node == null || photos.size >= 20) return
            
            if (node is String && node.contains("lh3.googleusercontent.com")) {
                var cleanUrl = node
                val escapeIdx = cleanUrl.indexOf("\\u003d")
                if (escapeIdx != -1) {
                    cleanUrl = cleanUrl.substring(0, escapeIdx)
                } else {
                    val eqIdx = cleanUrl.indexOf('=')
                    if (eqIdx != -1 && eqIdx > 30) cleanUrl = cleanUrl.substring(0, eqIdx)
                }
                val qIdx = cleanUrl.indexOf('?')
                if (qIdx != -1) cleanUrl = cleanUrl.substring(0, qIdx)
                
                if (!seen.contains(cleanUrl)) {
                    seen.add(cleanUrl)
                    photos.add(PhotoDto(
                        key = "p${photos.size}",
                        url = cleanUrl + "=w1200-h800-k-no",
                        width = 1200,
                        height = 800
                    ))
                }
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) walk(node.opt(i))
            } else if (node is JSONObject) {
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()))
            }
        }
        
        walk(root)
        return photos
    }
    
    private fun extractReviews(root: JSONArray): List<ReviewDto> {
        val reviews = mutableListOf<ReviewDto>()
        
        fun walk(node: Any?, depth: Int) {
            if (node == null || depth > 15 || reviews.size >= 5) return
            
            if (node is JSONArray) {
                if (node.length() >= 5 && reviews.size < 5) {
                    var rating: Int? = null
                    var text: String? = null
                    var author: String? = null
                    
                    for (i in 0 until node.length()) {
                        val item = node.opt(i)
                        if (item is Int && item in 1..5 && rating == null) {
                            rating = item
                        }
                        if (item is String && item.length > 30 && item.length < 2000 && 
                            !item.contains("http") && !item.contains("google") &&
                            text == null) {
                            text = item
                        }
                        if (item is String && item.length > 3 && item.length < 40 && 
                            !item.contains("http") && !item.contains("0x") &&
                            !item.contains("google") && author == null && text != null) {
                            author = item
                        }
                    }
                    
                    if (rating != null && text != null && author != null) {
                        val hasReviewStructure = text.length > 30 && author.length < 40
                        if (hasReviewStructure) {
                            reviews.add(ReviewDto(
                                authorName = author,
                                authorPhotoUrl = null,
                                rating = rating,
                                timeText = null,
                                reviewText = text
                            ))
                        }
                    }
                }
                
                for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
            } else if (node is JSONObject) {
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1)
            }
        }
        
        walk(root, 0)
        return reviews
    }
    
    private fun extractString(array: JSONArray, type: String): String {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is String && item.length > 3 && item.length < 100 && 
                !item.startsWith("http") && !item.startsWith("0x") &&
                !item.contains("google") && !item.contains("0ahUKE") &&
                !item.contains("\\u")) {
                return item
            }
        }
        return ""
    }
    
    private fun extractDoubleInRange(array: JSONArray, min: Double, max: Double): Double? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is Double && item >= min && item <= max) return item
            if (item is JSONArray) {
                val nested = extractDoubleInRange(item, min, max)
                if (nested != null) return nested
            }
        }
        return null
    }
    
    private fun extractIntInRange(array: JSONArray, min: Int, max: Int): Int? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is Int && item >= min && item <= max) return item
            if (item is JSONArray) {
                val nested = extractIntInRange(item, min, max)
                if (nested != null) return nested
            }
        }
        return null
    }
    
    private fun extractWebsite(array: JSONArray): String? {
        fun walk(node: Any?): String? {
            if (node == null) return null
            if (node is String && (node.startsWith("http://") || node.startsWith("https://")) &&
                !node.contains("google.com") && !node.contains("googleusercontent") &&
                !node.contains("gstatic.com")) {
                return node
            }
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val r = walk(node.opt(i))
                    if (r != null) return r
                }
            } else if (node is JSONObject) {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val r = walk(node.opt(keys.next()))
                    if (r != null) return r
                }
            }
            return null
        }
        return walk(array)
    }
    
    private fun extractTypes(array: JSONArray): List<String> {
        val types = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray) {
                if (item.length() in 1..5) {
                    var allShortStrings = true
                    val candidates = mutableListOf<String>()
                    for (j in 0 until item.length()) {
                        val sub = item.opt(j)
                        if (sub is String && sub.length > 2 && sub.length < 30 && 
                            !sub.contains("http")) {
                            candidates.add(sub)
                        } else {
                            allShortStrings = false
                        }
                    }
                    if (allShortStrings && candidates.isNotEmpty() && types.isEmpty()) {
                        types.addAll(candidates)
                    }
                }
                if (types.isEmpty()) {
                    types.addAll(extractTypes(item))
                }
            }
            if (types.isNotEmpty()) break
        }
        return types
    }
    
    private fun extractDescription(array: JSONArray): String? {
        fun walk(node: Any?): String? {
            if (node == null) return null
            if (node is String && node.length > 100 && node.length < 2000 &&
                !node.contains("http") && !node.contains("0x") &&
                !node.contains("\\u") && !node.contains("google")) {
                if (!node.startsWith("0ahUKE") && !node.startsWith("CIH")) {
                    return node
                }
            }
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val r = walk(node.opt(i))
                    if (r != null) return r
                }
            }
            return null
        }
        return walk(array)
    }
}
