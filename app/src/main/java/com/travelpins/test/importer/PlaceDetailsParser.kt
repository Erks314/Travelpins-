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
            
            // Trova l'array principale dei dati del luogo (quello con ~20 elementi)
            val placeDataArray = findPlaceDataArray(rootArray) ?: return null
            
            // Estrai dati dalle posizioni specifiche
            val name = extractName(placeDataArray)
            val rating = extractRating(placeDataArray)
            val reviewCount = extractReviewCount(placeDataArray)
            val websiteUrl = extractWebsite(placeDataArray)
            val types = extractTypes(placeDataArray)
            val description = extractDescription(placeDataArray)
            
            // Estrai foto da tutto l'array
            val photos = extractPhotos(rootArray)
            
            // Estrai recensioni
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
        for (i in 0 until minOf(root.length(), 10)) {
            val item = root.opt(i)
            if (item is JSONArray && item.length() >= 15) {
                // Verifica che contenga la struttura attesa
                if (isValidPlaceDataArray(item)) {
                    return item
                }
            }
        }
        return null
    }
    
    // Verifica se un array ha la struttura dei dati del luogo
    private fun isValidPlaceDataArray(array: JSONArray): Boolean {
        // Deve avere: nome, coordinate, tipi, indirizzo
        var hasName = false
        var hasCoords = false
        var hasTypes = false
        
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            
            // Nome: stringa non-token
            if (item is String && item.length in 4..100 && !isToken(item)) {
                hasName = true
            }
            
            // Coordinate: array con numeri
            if (item is JSONArray && item.length() >= 2) {
                val first = item.opt(0)
                val second = item.opt(1)
                if (first is Double && second is Double && 
                    kotlin.math.abs(first) <= 90 && kotlin.math.abs(second) <= 180) {
                    hasCoords = true
                }
            }
            
            // Tipi: array di stringhe corte
            if (item is JSONArray && item.length() in 1..5) {
                var allShortStrings = true
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub !is String || sub.length !in 3..30 || sub.contains("http")) {
                        allShortStrings = false
                        break
                    }
                }
                if (allShortStrings) hasTypes = true
            }
        }
        
        return hasName && hasCoords
    }
    
    // Verifica se una stringa è un token (non dati reali)
    private fun isToken(str: String): Boolean {
        return str.startsWith("0ahUKE") || 
               str.startsWith("CIH") || 
               str.startsWith("kyeS") ||
               str.contains("AAAA") ||
               str.matches(Regex("^[A-Za-z0-9_-]{20,}$"))
    }
    
    // Estrai nome dal posto corretto (indice ~13)
    private fun extractName(array: JSONArray): String {
        for (i in 10 until minOf(array.length(), 20)) {
            val item = array.opt(i)
            if (item is String && item.length in 4..100 && !isToken(item) &&
                !item.contains("http") && !item.startsWith("0x")) {
                return item
            }
        }
        return ""
    }
    
    // Estrai rating dall'array specifico (indice ~5)
    private fun extractRating(array: JSONArray): Double? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() >= 10) {
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
    
    // Estrai conteggio recensioni (stesso array del rating)
    private fun extractReviewCount(array: JSONArray): Int? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() >= 10) {
                // Cerca Int > 10 in questo sotto-array
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is Int && sub > 10 && sub < 10000000) {
                        return sub
                    }
                }
            }
        }
        return null
    }
    
    // Estrai sito web dall'array specifico (indice ~7)
    private fun extractWebsite(array: JSONArray): String? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray) {
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is String && (sub.startsWith("http://") || sub.startsWith("https://")) &&
                        !sub.contains("google.com") && !sub.contains("googleusercontent")) {
                        return sub
                    }
                }
            }
        }
        return null
    }
    
    // Estrai tipi dall'array specifico (indice ~15)
    private fun extractTypes(array: JSONArray): List<String> {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() in 1..5) {
                val candidates = mutableListOf<String>()
                var allValid = true
                
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is String && sub.length in 3..30 && !sub.contains("http") && !isToken(sub)) {
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
    private fun extractDescription(array: JSONArray): String? {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 8) return null
            
            if (node is String && node.length in 100..2000 &&
                !node.contains("http") && !node.contains("0x") &&
                !node.contains("\\u") && !node.contains("google") &&
                !isToken(node)) {
                // Verifica che sia testo leggibile (non solo caratteri speciali)
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
    
    // Estrai recensioni (cerca strutture con testo lungo e rating)
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
