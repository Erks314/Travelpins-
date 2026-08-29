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
            
            // Trova l'array principale dei dati del luogo
            // Tipicamente è l'elemento [6] dell'array root, ma potrebbe essere in altre posizioni
            val placeDataArray = findPlaceDataArray(rootArray)
            
            if (placeDataArray == null) {
                // Fallback: cerca i dati in modo più ampio
                return parseFallback(rootArray)
            }
            
            // Estrai dalle posizioni specifiche
            val name = extractNameFromPosition(placeDataArray)
            val rating = extractRatingFromPosition(placeDataArray)
            val reviewCount = extractReviewCountFromPosition(placeDataArray)
            val websiteUrl = extractWebsiteFromPosition(placeDataArray)
            val types = extractTypesFromPosition(placeDataArray)
            val description = extractDescriptionFromPosition(placeDataArray)
            
            // Estrai foto e recensioni da tutto l'array
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
    
    // Trova l'array principale con i dati del luogo
    private fun findPlaceDataArray(root: JSONArray): JSONArray? {
        // Cerca nei primi 10 elementi
        for (i in 0 until minOf(root.length(), 10)) {
            val item = root.opt(i)
            if (item is JSONArray && item.length() >= 8) {
                // Verifica che contenga almeno un nome valido (non token)
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is String && sub.length in 4..100 && !isToken(sub) && 
                        !sub.contains("http") && !sub.startsWith("0x")) {
                        return item
                    }
                }
            }
        }
        return null
    }
    
    // Estrai nome dalla posizione specifica (tipicamente [10])
    private fun extractNameFromPosition(array: JSONArray): String {
        // Cerca una stringa valida tra gli indici 8-15
        for (i in 8 until minOf(array.length(), 20)) {
            val item = array.opt(i)
            if (item is String && item.length in 4..100 && !isToken(item) &&
                !item.contains("http") && !item.startsWith("0x") &&
                !item.contains(",")) { // Escludi indirizzi
                return item
            }
        }
        // Fallback: cerca in tutto l'array
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is String && item.length in 4..80 && !isToken(item) &&
                !item.contains("http") && !item.startsWith("0x") &&
                !item.contains(",") && !item.contains("/")) {
                return item
            }
        }
        return ""
    }
    
    // Estrai rating dalla posizione specifica (tipicamente [4][7])
    private fun extractRatingFromPosition(array: JSONArray): Double? {
        // Cerca un sotto-array che contiene il rating
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() >= 5) {
                // Cerca Double tra 1.0 e 5.0 in questo sotto-array
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is Double && sub >= 1.0 && sub <= 5.0) {
                        return sub
                    }
                }
            }
        }
        return null
    }
    
    // Estrai conteggio recensioni dalla posizione specifica (tipicamente [4][8])
    private fun extractReviewCountFromPosition(array: JSONArray): Int? {
        // Cerca un sotto-array che contiene il conteggio
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() >= 5) {
                // Cerca Int ragionevole (> 1, < 10M) in questo sotto-array
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is Int && sub > 1 && sub < 10000000) {
                        // Verifica che non sia una dimensione immagine
                        if (sub != 1024 && sub != 768 && sub != 512 && sub != 256) {
                            return sub
                        }
                    }
                }
            }
        }
        return null
    }
    
    // Estrai sito web dalla posizione specifica (tipicamente [6][0])
    private fun extractWebsiteFromPosition(array: JSONArray): String? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray) {
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is String && (sub.startsWith("http://") || sub.startsWith("https://")) &&
                        !sub.contains("google.com") && !sub.contains("googleusercontent") &&
                        !sub.contains("gstatic.com")) {
                        return sub
                    }
                }
            }
        }
        return null
    }
    
    // Estrai tipi dalla posizione specifica (tipicamente [12])
    private fun extractTypesFromPosition(array: JSONArray): List<String> {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() in 1..5) {
                val candidates = mutableListOf<String>()
                var allValid = true
                
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is String && sub.length in 3..40 && !sub.contains("http") && !isToken(sub)) {
                        candidates.add(sub)
                    } else {
                        allValid = false
                        break
                    }
                }
                
                if (allValid && candidates.isNotEmpty()) {
                    return candidates
                }
            }
        }
        return emptyList()
    }
    
    // Estrai descrizione (cerca testo lungo valido)
    private fun extractDescriptionFromPosition(array: JSONArray): String? {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 10) return null
            
            if (node is String && node.length in 100..2000 &&
                !node.contains("http") && !node.contains("0x") &&
                !node.contains("\\u") && !node.contains("google") &&
                !isToken(node)) {
                val letterCount = node.count { it.isLetter() || it.isWhitespace() }
                if (letterCount > node.length * 0.5) {
                    return node
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
        return walk(array, 0)
    }
    
    // Fallback per JSON con struttura diversa
    private fun parseFallback(root: JSONArray): PlaceDetails? {
        val name = findPlaceNameFallback(root)
        val rating = findRatingFallback(root)
        val reviewCount = findReviewCountFallback(root)
        val websiteUrl = findWebsiteFallback(root)
        val types = findTypesFallback(root)
        val description = findDescriptionFallback(root)
        val photos = extractPhotos(root)
        val reviews = extractReviews(root)
        
        return PlaceDetails(
            name = name,
            rating = rating,
            reviewCount = reviewCount,
            description = description,
            websiteUrl = websiteUrl,
            types = types,
            photos = photos.take(10),
            reviews = reviews.take(5)
        )
    }
    
    private fun findPlaceNameFallback(root: JSONArray): String {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 8) return null
            if (node is String && node.length in 4..80 && !isToken(node) &&
                !node.contains("http") && !node.startsWith("0x") &&
                !node.contains(",") && !node.contains("/")) {
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
        return walk(root, 0) ?: ""
    }
    
    private fun findRatingFallback(root: JSONArray): Double? {
        fun walk(node: Any?, depth: Int): Double? {
            if (node == null || depth > 12) return null
            if (node is Double && node >= 1.0 && node <= 5.0) return node
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
    
    private fun findReviewCountFallback(root: JSONArray): Int? {
        fun walk(node: Any?, depth: Int): Int? {
            if (node == null || depth > 12) return null
            if (node is Int && node > 1 && node < 10000000 && 
                node != 1024 && node != 768 && node != 512 && node != 256) {
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
    
    private fun findWebsiteFallback(root: JSONArray): String? {
        fun walk(node: Any?): String? {
            if (node == null) return null
            if (node is String && (node.startsWith("http://") || node.startsWith("https://")) &&
                !node.contains("google.com") && !node.contains("googleusercontent")) {
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
    
    private fun findTypesFallback(root: JSONArray): List<String> {
        fun walk(node: Any?, depth: Int): List<String>? {
            if (node == null || depth > 8) return null
            if (node is JSONArray && node.length() in 1..5) {
                val candidates = mutableListOf<String>()
                var allValid = true
                for (i in 0 until node.length()) {
                    val item = node.opt(i)
                    if (item is String && item.length in 3..40 && !item.contains("http") && !isToken(item)) {
                        candidates.add(item)
                    } else {
                        allValid = false
                        break
                    }
                }
                if (allValid && candidates.isNotEmpty()) return candidates
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
    
    private fun findDescriptionFallback(root: JSONArray): String? {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 10) return null
            if (node is String && node.length in 100..2000 &&
                !node.contains("http") && !node.contains("0x") &&
                !node.contains("\\u") && !isToken(node)) {
                val letterCount = node.count { it.isLetter() || it.isWhitespace() }
                if (letterCount > node.length * 0.5) return node
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
    
    // Verifica se una stringa è un token
    private fun isToken(str: String): Boolean {
        return str.startsWith("0ahUKE") || 
               str.startsWith("CIH") || 
               str.startsWith("kyeS") ||
               str.startsWith("KimS") ||
               str.startsWith("BiSS") ||
               str.startsWith("rSWS") ||
               str.startsWith("fB6S") ||
               str.contains("AAAA") ||
               str.matches(Regex("^[A-Za-z0-9_-]{20,}$"))
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
            
            if (node is JSONArray && node.length() >= 8) {
                var rating: Int? = null
                var text: String? = null
                var author: String? = null
                
                for (i in 0 until node.length()) {
                    val item = node.opt(i)
                    
                    if (item is Int && item in 1..5 && rating == null) {
                        rating = item
                    }
                    
                    if (item is String && item.length in 50..2000 && 
                        !item.contains("http") && !item.contains("google") &&
                        !isToken(item) && text == null) {
                        val letterCount = item.count { it.isLetter() || it.isWhitespace() }
                        if (letterCount > item.length * 0.5) {
                            text = item
                        }
                    }
                    
                    if (item is String && item.length in 3..40 && 
                        !item.contains("http") && !item.contains("0x") &&
                        !item.contains("google") && !isToken(item) && 
                        author == null && text != null) {
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
