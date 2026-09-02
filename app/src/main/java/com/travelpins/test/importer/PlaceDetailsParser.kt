package com.travelpins.test.importer // <-- Assicurati che il package combaci col tuo Bridge

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

            // 1. NOME: Usiamo il fallbackName (il nome estratto dalla lista). 
            // È già corretto al 100% e ci evita di leggere token tecnici dal JSON.
            val name = fallbackName

            // 2. RATING E RECENSIONI: Scansione ricorsiva (non più legata all'indice 12/13)
            val (rating, reviewCount) = findRatingAndReviews(root)

            // 3. SITO WEB: Cerca il primo URL valido non-Google nel JSON
            val websiteUrl = extractWebsite(cleanJson)

            // 4. TIPI: Estrazione euristica basata su parole chiave
            val types = extractTypes(root)

            // 5. Descrizione, Foto e Recensioni
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

    /**
     * Scansiona tutto il JSON cercando un Double (1.0-5.0) vicino a un Int > 0.
     * Questo pattern è la firma inconfondibile di [Rating, Recensioni] su Google Maps.
     */
    private fun findRatingAndReviews(root: JSONArray): Pair<Double?, Int?> {
        var rating: Double? = null
        var reviews: Int? = null

        fun scan(arr: JSONArray) {
            if (rating != null) return // Appena trova il primo blocco valido, si ferma
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                if (item is JSONArray) {
                    scan(item)
                } else if (item is Double && item >= 1.0 && item <= 5.0) {
                    // Cerca il numero di recensioni nelle vicinanze (prima o dopo)
                    for (offset in 1..4) {
                        val neighbor = arr.opt(i + offset)
                        if (neighbor is Int && neighbor > 0 && neighbor < 1000000) {
                            rating = item
                            reviews = neighbor
                            return
                        }
                    }
                    for (offset in 1..4) {
                        val neighbor = arr.opt(i - offset)
                        if (neighbor is Int && neighbor > 0 && neighbor < 1000000) {
                            rating = item
                            reviews = neighbor
                            return
                        }
                    }
                }
            }
        }
        scan(root)
        return Pair(rating, reviews)
    }

    /**
     * Cerca URL nel JSON escludendo i domini di Google.
     */
    private fun extractWebsite(json: String): String? {
        val regex = Regex("https?://[^\"]*")
        val matches = regex.findAll(json)
        val blacklist = listOf(
            "google.com", "gstatic.com", "ggpht.com", "googleapis.com", 
            "googleusercontent.com", "maps.app.goo.gl", "schema.org", 
            "example.com", "w3.org", "google.it"
        )
        for (match in matches) {
            val url = match.value
            if (blacklist.none { url.contains(it, ignoreCase = true) }) {
                return url
            }
        }
        return null
    }

    /**
     * Cerca stringhe brevi che corrispondono a tipici "Tipi" di luoghi.
     */
    private fun extractTypes(root: JSONArray): List<String> {
        val types = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        
        val typeKeywords = listOf(
            "turistica", "storico", "ristorante", "hotel", "castello", "parco", 
            "museo", "chiesa", "cattedrale", "abbazia", "pub", "bar", "cafe", 
            "spiaggia", "scogliera", "punto panoramico", "attrazione", "monumento"
        )

        fun scan(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                if (item is JSONArray) {
                    scan(item)
                } else if (item is String && item.length in 3..50 && !seen.contains(item)) {
                    if (!item.startsWith("ChIJ") && !item.startsWith("CIHM") && !item.contains("http") && !item.matches(Regex("\\d+"))) {
                        if (typeKeywords.any { item.contains(it, ignoreCase = true) } || item.split(" ").size <= 4) {
                            seen.add(item)
                            types.add(item)
                        }
                    }
                }
            }
        }
        scan(root)
        return types.take(5)
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
                        if ((item.startsWith("CIHM") || item.startsWith("CIABI") || item.startsWith("CgkI") || item.startsWith("CAIS")) && item.length > 20 && !seenKeys.contains(item)) {
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
