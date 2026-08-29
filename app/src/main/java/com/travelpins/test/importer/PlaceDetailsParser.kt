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
            
            // Estrai direttamente tutti i dati con ricerche profonde
            val name = findPlaceName(rootArray)
            val rating = findRating(rootArray)
            val reviewCount = findReviewCount(rootArray)
            val websiteUrl = findWebsite(rootArray)
            val types = findTypes(rootArray)
            val description = findDescription(rootArray)
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
    
    // Cerca il nome del luogo (stringa corta, non URL, non token)
    private fun findPlaceName(root: JSONArray): String {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 10) return null
            
            if (node is String && node.length in 4..80 && 
                !node.startsWith("http") && !node.startsWith("0x") &&
                !node.contains("google") && !node.contains("0ahUKE") &&
                !node.contains("\\u") && !node.startsWith("CIH")) {
                // Probabile nome del luogo
                return node
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val result = walk(node.opt(i), depth + 1)
                    if (result != null) return result
                }
            }
            return null
        }
        
        // Cerca nei primi 10 elementi dell'array root
        for (i in 0 until minOf(root.length(), 10)) {
            val result = walk(root.opt(i), 0)
            if (result != null) return result
        }
        return ""
    }
    
    // Cerca il rating (Double tra 1.0 e 5.0)
    private fun findRating(root: JSONArray): Double? {
        fun walk(node: Any?, depth: Int): Double? {
            if (node == null || depth > 15) return null
            
            if (node is Double && node >= 1.0 && node <= 5.0) {
                return node
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val result = walk(node.opt(i), depth + 1)
                    if (result != null) return result
                }
            }
            return null
        }
        return walk(root, 0)
    }
    
    // Cerca il conteggio recensioni (Int > 1)
    private fun findReviewCount(root: JSONArray): Int? {
        fun walk(node: Any?, depth: Int): Int? {
            if (node == null || depth > 15) return null
            
            if (node is Int && node > 1 && node < 10000000) {
                return node
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val result = walk(node.opt(i), depth + 1)
                    if (result != null) return result
                }
            }
            return null
        }
        return walk(root, 0)
    }
    
    // Cerca il sito web
    private fun findWebsite(root: JSONArray): String? {
        fun walk(node: Any?): String? {
            if (node == null) return null
            
            if (node is String && (node.startsWith("http://") || node.startsWith("https://")) &&
                !node.contains("google.com") && !node.contains("googleusercontent") &&
                !node.contains("gstatic.com")) {
                return node
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val result = walk(node.opt(i))
                    if (result != null) return result
                }
            }
            return null
        }
        return walk(root)
    }
    
    // Cerca i tipi (array di stringhe corte)
    private fun findTypes(root: JSONArray): List<String> {
        fun walk(node: Any?, depth: Int): List<String>? {
            if (node == null || depth > 10) return null
            
            if (node is JSONArray && node.length() in 1..5) {
                val candidates = mutableListOf<String>()
                var allValid = true
                
                for (i in 0 until node.length()) {
                    val item = node.opt(i)
                    if (item is String && item.length in 3..30 && !item.contains("http")) {
                        candidates.add(item)
                    } else {
                        allValid = false
                        break
                    }
                }
                
                if (allValid && candidates.isNotEmpty()) {
                    return candidates
                }
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val result = walk(node.opt(i), depth + 1)
                    if (result != null) return result
                }
            }
            return null
        }
        return walk(root, 0) ?: emptyList()
    }
    
    // Cerca la descrizione (stringa lunga)
    private fun findDescription(root: JSONArray): String? {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 10) return null
            
            if (node is String && node.length in 100..2000 &&
                !node.contains("http") && !node.contains("0x") &&
                !node.contains("\\u") && !node.contains("google") &&
                !node.startsWith("0ahUKE") && !node.startsWith("CIH")) {
                return node
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val result = walk(node.opt(i), depth + 1)
                    if (result != null) return result
                }
            }
            return null
        }
        return walk(root, 0)
    }
    
    // Estrai foto con URL puliti
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
            }
        }
        
        walk(root)
        return photos
    }
    
    // Estrai recensioni
    private fun extractReviews(root: JSONArray): List<ReviewDto> {
        val reviews = mutableListOf<ReviewDto>()
        
        fun walk(node: Any?, depth: Int) {
            if (node == null || depth > 15 || reviews.size >= 5) return
            
            if (node is JSONArray && node.length() >= 5) {
                var rating: Int? = null
                var text: String? = null
                var author: String? = null
                
                for (i in 0 until node.length()) {
                    val item = node.opt(i)
                    if (item is Int && item in 1..5 && rating == null) {
                        rating = item
                    }
                    if (item is String && item.length in 30..2000 && 
                        !item.contains("http") && !item.contains("google") &&
                        text == null) {
                        text = item
                    }
                    if (item is String && item.length in 3..40 && 
                        !item.contains("http") && !item.contains("0x") &&
                        !item.contains("google") && author == null && text != null) {
                        author = item
                    }
                }
                
                if (rating != null && text != null && author != null) {
                    reviews.add(ReviewDto(
                        authorName = author,
                        authorPhotoUrl = null,
                        rating = rating,
                        timeText = null,
                        reviewText = text
                    ))
                }
            }
            
            if (node is JSONArray) {
                for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
            }
        }
        
        walk(root, 0)
        return reviews
    }
}
