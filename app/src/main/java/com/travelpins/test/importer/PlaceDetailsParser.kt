package com.travelpins.test.importer

import org.json.JSONArray

data class PlaceDetails(
    val name: String,
    val rating: Double?,
    val reviewCount: Int?,
    val description: String?,
    val websiteUrl: String?,
    val types: List<String>,
    val photos: List<PhotoDto>,
    val reviews: List<ReviewDto>  // Sempre vuoto - non ci servono
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
            val root = JSONArray(rawJson)
            
            // Trova l'array principale con i dati del luogo
            val placeData = findPlaceDataArray(root)
            
            val name = if (placeData != null) extractName(placeData) else ""
            val rating = if (placeData != null) extractRating(placeData) else findRatingDeep(root)
            val reviewCount = if (placeData != null) extractReviewCount(placeData) else null
            val websiteUrl = if (placeData != null) extractWebsite(placeData) else null
            val types = if (placeData != null) extractTypes(placeData) else emptyList()
            val description = findDescriptionDeep(root)
            val photos = extractPhotos(root)
            
            PlaceDetails(
                name = name,
                rating = rating,
                reviewCount = reviewCount,
                description = description,
                websiteUrl = websiteUrl,
                types = types,
                photos = photos.take(10),
                reviews = emptyList()  // Non estraiamo recensioni singole
            )
        } catch (e: Exception) {
            null
        }
    }
    
    // Trova l'array principale (quello con nome, rating, sito, tipi)
    private fun findPlaceDataArray(root: JSONArray): JSONArray? {
        for (i in 0 until minOf(root.length(), 10)) {
            val item = root.opt(i)
            if (item is JSONArray && item.length() >= 8) {
                // Deve contenere almeno un nome valido
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is String && sub.length in 4..100 && !isToken(sub) && 
                        !sub.contains("http") && !sub.startsWith("0x") &&
                        !sub.contains(",")) {
                        return item
                    }
                }
            }
        }
        return null
    }
    
    // Nome: stringa breve senza virgole (non indirizzo)
    private fun extractName(array: JSONArray): String {
        for (i in 8 until minOf(array.length(), 20)) {
            val item = array.opt(i)
            if (item is String && item.length in 4..100 && !isToken(item) &&
                !item.contains("http") && !item.startsWith("0x") &&
                !item.contains(",")) {
                return item
            }
        }
        return ""
    }
    
    // Rating: Double tra 1.0 e 5.0 dentro un sotto-array
    private fun extractRating(array: JSONArray): Double? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray) {
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
    
    // Review count: Int > 10 dentro lo stesso sotto-array del rating
    private fun extractReviewCount(array: JSONArray): Int? {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray) {
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    if (sub is Int && sub > 10 && sub < 10000000) {
                        // Escludi dimensioni standard di immagini/video
                        if (sub !in setOf(1024, 768, 512, 256, 120, 180)) {
                            return sub
                        }
                    }
                }
            }
        }
        return null
    }
    
    // Website: primo URL http/https non-Google
    private fun extractWebsite(array: JSONArray): String? {
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
    
    // Tipi: array di 1-5 stringhe corte, SENZA virgole e SENZA spazi (non indirizzi!)
    private fun extractTypes(array: JSONArray): List<String> {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONArray && item.length() in 1..5) {
                val candidates = mutableListOf<String>()
                var allValid = true
                
                for (j in 0 until item.length()) {
                    val sub = item.opt(j)
                    // Deve essere una stringa corta, senza virgole (non indirizzo)
                    if (sub is String && sub.length in 3..40 && 
                        !sub.contains("http") && !isToken(sub) &&
                        !sub.contains(",") && 
                        sub.count { it == ' ' } <= 3) {  // max 3 spazi (tipi tipo "Attrazione turistica")
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
    
    // Descrizione: stringa lunga leggibile in profondità
    private fun findDescriptionDeep(root: JSONArray): String? {
        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 12) return null
            
            if (node is String && node.length in 100..2000 &&
                !node.contains("http") && !node.contains("0x") &&
                !node.contains("\\u") && !node.contains("google") &&
                !isToken(node)) {
                // Conta lettere+spazi vs caratteri totali
                val readable = node.count { it.isLetter() || it.isWhitespace() || it == '.' || it == ',' }
                if (readable > node.length * 0.6) {
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
        return walk(root, 0)
    }
    
    // Rating fallback (ricerca profonda)
    private fun findRatingDeep(root: JSONArray): Double? {
        fun walk(node: Any?, depth: Int): Double? {
            if (node == null || depth > 12) return null
            if (node is Double && node >= 1.0 && node <= 5.0) return node
            if (node is JSONArray) {
                for (i in 0 until node.length()) {
                    val r = walk(node.opt(i), depth + 1)
                    if (r != null) return r
                }
            }
            return null
        }
        return walk(root, 0)
    }
    
    // Token: stringhe che sono identificatori, non dati reali
    private fun isToken(str: String): Boolean {
        return str.startsWith("0ahUKE") || 
               str.startsWith("CIH") || 
               str.startsWith("CAI") ||
               str.length > 20 && str.matches(Regex("^[A-Za-z0-9_-]+$"))
    }
    
    // Foto: URL pulite SENZA parametri dimensione (li aggiunge la UI)
    private fun extractPhotos(root: JSONArray): List<PhotoDto> {
        val photos = mutableListOf<PhotoDto>()
        val seen = mutableSetOf<String>()
        
        fun walk(node: Any?) {
            if (node == null || photos.size >= 20) return
            
            if (node is String && node.contains("lh3.googleusercontent.com")) {
                var url = node
                
                // Rimuovi tutto dopo \u003d o = (parametri già presenti)
                val escapeIdx = url.indexOf("\\u003d")
                if (escapeIdx != -1) {
                    url = url.substring(0, escapeIdx)
                } else {
                    val eqIdx = url.indexOf('=')
                    if (eqIdx != -1 && eqIdx > 30) {
                        url = url.substring(0, eqIdx)
                    }
                }
                
                // Rimuovi query string
                val qIdx = url.indexOf('?')
                if (qIdx != -1) url = url.substring(0, qIdx)
                
                if (!seen.contains(url)) {
                    seen.add(url)
                    photos.add(PhotoDto(
                        key = "p${photos.size}",
                        url = url,  // URL base pulita
                        width = null,
                        height = null
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
}
