package com.tuopackage // <-- Ricorda di adattare il package

import org.json.JSONArray
import android.util.Log

object PlaceDetailsParser {

    data class ParsedDetails(
        val name: String?,
        val rating: Double?,
        val reviewCount: Int?,
        val websiteUrl: String?,
        val types: List<String>,
        val description: String?,
        val photos: List<PhotoDto>,
        val reviews: List<ReviewDto>
    )

    data class PhotoDto(val key: String, val url: String, val width: Int, val height: Int)
    data class ReviewDto(val authorName: String, val authorPhotoUrl: String?, val rating: Int, val timeText: String, val reviewText: String)

    fun parse(rawJson: String, fallbackName: String?): ParsedDetails? {
        try {
            val cleanJson = if (rawJson.startsWith(")]}'")) rawJson.substring(4).trim() else rawJson
            val root = JSONArray(cleanJson)

            // 1. Trova l'array principale dei dati (solitamente all'indice 6)
            var mainArray: JSONArray? = null
            for (i in 0 until root.length()) {
                val candidate = root.optJSONArray(i) ?: continue
                // L'array principale ha il Place ID all'indice 4 (inizia con ChIJ) 
                // oppure ha Rating (12) e ReviewCount (13)
                if (candidate.optString(4).startsWith("ChIJ") || 
                    (candidate.optDouble(12) > 0.0 && candidate.optDouble(12) <= 5.0 && candidate.optInt(13) > 0)) {
                    mainArray = candidate
                    break
                }
            }

            if (mainArray == null) {
                Log.w("PlaceDetailsParser", "Array principale non trovato")
                return null
            }

            // 2. Estrazione chirurgica dei campi base
            val name = mainArray.optString(2).takeIf { it.isNotBlank() } ?: fallbackName
            val rating = mainArray.optDouble(12).takeIf { it > 0.0 && it <= 5.0 }
            val reviewCount = mainArray.optInt(13).takeIf { it > 0 }
            
            val rawWebsite = mainArray.optString(15)
            val websiteUrl = extractFullUrl(rawWebsite, cleanJson)

            // 3. Estrazione Tipi (INDICI 18, 19, 20...)
            // I tipi si trovano subito dopo le coordinate (Lat=16, Lng=17)
            val types = mutableListOf<String>()
            for (i in 18 until minOf(mainArray.length(), 30)) {
                val item = mainArray.opt(i)
                if (item is String && item.length in 2..50) {
                    // Filtra ID, URL e stringhe tecniche
                    if (!item.startsWith("ChIJ") && !item.startsWith("CIHM") && !item.contains("http") && !item.matches(Regex("\\d+"))) {
                        types.add(item)
                    }
                } else if (item is JSONArray || item is Int || item is Double) {
                    break // Finita la lista dei tipi, iniziano gli orari o altri array
                }
            }

            // 4. Descrizione, Foto e Recensioni
            val description = extractDescription(cleanJson)
            val photos = extractPhotos(root)
            val reviews = extractReviews(root)

            return ParsedDetails(
                name = name,
                rating = rating,
                reviewCount = reviewCount,
                websiteUrl = websiteUrl,
                types = types,
                description = description,
                photos = photos,
                reviews = reviews
            )

        } catch (e: Exception) {
            Log.e("PlaceDetailsParser", "Errore parsing", e)
            return null
        }
    }

    private fun extractFullUrl(domain: String, fullJson: String): String? {
        if (domain.isBlank() || domain == "null") return null
        if (domain.startsWith("http")) return domain
        
        val cleanDomain = domain.replace("www.", "").replace("\"", "")
        val blacklist = listOf("google.com", "gstatic.com", "ggpht.com", "googleapis.com")
        if (blacklist.any { cleanDomain.contains(it) }) return null

        val regex = Regex("https?://[^\"]*${Regex.escape(cleanDomain)}[^\"]*")
        val match = regex.find(fullJson)
        return match?.value ?: "https://$cleanDomain"
    }

    private fun extractDescription(json: String): String? {
        val regex = Regex("\"([^\"]{80,1000})\"")
        val matches = regex.findAll(json)
        for (match in matches) {
            val text = match.groupValues[1]
            if (!text.contains("http") && !text.contains("null") && !text.contains("{") && 
                !text.contains("ChIJ") && !text.contains("CIHM") && !text.contains("CIABI") &&
                !text.matches(Regex(".*\\d{5,}.*")) && 
                text.split(" ").size > 10 &&
                !text.contains("Solitamente") && !text.contains("photos:")) {
                return text
            }
        }
        return null
    }

    private fun extractPhotos(root: JSONArray): List<PhotoDto> {
        val photos = mutableListOf<PhotoDto>()
        val seenKeys = mutableSetOf<String>()
        
        fun scanArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                when (val item = arr.opt(i)) {
                    is JSONArray -> scanArray(item)
                    is String -> {
                        if ((item.startsWith("CIHM") || item.startsWith("CIABI") || item.startsWith("CgkI")) && item.length > 20 && !seenKeys.contains(item)) {
                            val w = arr.optInt(i + 2, 0)
                            val h = arr.optInt(i + 3, 0)
                            if (w > 100 && h > 100) {
                                seenKeys.add(item)
                                photos.add(PhotoDto(item, "https://lh3.googleusercontent.com/p/$item=w1024-h768-n", w, h))
                            }
                        }
                    }
                }
            }
        }
        scanArray(root)
        return photos.take(20)
    }

    private fun extractReviews(root: JSONArray): List<ReviewDto> {
        val reviews = mutableListOf<ReviewDto>()
        fun scanArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONArray(i) ?: continue
                if (item.length() > 15) {
                    val author = item.optString(3)
                    val rating = item.optInt(6, 0) 
                    val text = item.optString(10) 
                    
                    if (author.isNotBlank() && author.length < 100 && rating in 1..5 && text.length > 10) {
                        reviews.add(ReviewDto(author, null, rating, item.optString(2), text))
                    }
                }
                scanArray(item)
            }
        }
        scanArray(root)
        return reviews.take(10)
    }
}
