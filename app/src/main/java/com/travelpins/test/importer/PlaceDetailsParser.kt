package com.travelpins.test.importer

import org.json.JSONArray
import org.json.JSONObject

data class PhotoDto(val key: String, val url: String, val width: Int?, val height: Int?)
data class ReviewDto(
    val authorName: String,
    val authorPhotoUrl: String?,
    val rating: Int?,
    val timeText: String?,
    val reviewText: String?
)

data class PlaceDetails(
    val name: String?,
    val rating: Double?,
    val reviewCount: Int?,
    val websiteUrl: String?,
    val types: List<String>,
    val description: String?,
    val photos: List<PhotoDto>,
    val reviews: List<ReviewDto>
)

object PlaceDetailsParser {

    // 🔥 Aggiunto expectedName per usare il nome già salvato nel DB come fallback sicuro
    fun parse(rawJson: String, expectedName: String? = null): PlaceDetails? {
        return try {
            val root = JSONArray(rawJson)
            
            // Google Maps JSON: i dati principali sono quasi sempre dentro root[6]
            val entityArray = root.optJSONArray(6) ?: root
            
            val name = expectedName ?: extractName(entityArray)
            val rating = extractNumberBetween(entityArray, 1.0, 5.0)
            val reviewCount = extractReviewCount(entityArray)
            val websiteUrl = extractUrl(entityArray)
            val description = extractLongestString(entityArray, 50)
            val types = extractTypes(entityArray)
            
            val photos = extractPhotos(root)
            val reviews = extractReviews(root)

            PlaceDetails(name, rating, reviewCount, websiteUrl, types, description, photos, reviews)
        } catch (t: Throwable) {
            null
        }
    }

    private fun extractName(entityArray: JSONArray): String? {
        // Prova gli indici comuni per il nome
        val idx11 = entityArray.optString(11)
        if (!idx11.isNullOrBlank() && !idx11.startsWith("0a") && !idx11.contains("zqKXas") && idx11.length < 100) return idx11
        
        val idx10 = entityArray.optString(10)
        if (!idx10.isNullOrBlank() && !idx10.startsWith("0a") && idx10.length < 100) return idx10

        // Fallback: cerca la prima stringa ragionevole
        for (i in 0 until entityArray.length()) {
            val s = entityArray.optString(i)
            if (s != null && s.length in 3..60 && s[0].isUpperCase() && !s.contains("http") && !s.contains("0a") && !s.contains(",")) {
                return s
            }
        }
        return null
    }

    // 🔍 Cerca un numero decimale tra 1.0 e 5.0 (Il Rating!)
    private fun extractNumberBetween(node: Any?, min: Double, max: Double): Double? {
        if (node is Number) {
            val d = node.toDouble()
            if (d >= min && d <= max) return d
        }
        if (node is JSONArray) {
            for (i in 0 until node.length()) {
                val res = extractNumberBetween(node.opt(i), min, max)
                if (res != null) return res
            }
        }
        return null
    }

    // 🔍 Cerca un intero valido (esclude dimensioni foto come 1024, 768 e timestamp)
    private fun extractReviewCount(node: Any?): Int? {
        if (node is Int) {
            if (node > 5 && node < 10000000 && 
                node != 1024 && node != 768 && node != 1080 && node != 608 && node != 2048 && node != 800 && node != 600) {
                return node
            }
        }
        if (node is JSONArray) {
            for (i in 0 until node.length()) {
                val res = extractReviewCount(node.opt(i))
                if (res != null) return res
            }
        }
        return null
    }

    // 🔍 Cerca un URL valido (esclude domini Google)
    private fun extractUrl(node: Any?): String? {
        if (node is String) {
            if (node.startsWith("http") && 
                !node.contains("google.com") && 
                !node.contains("gstatic.com") && 
                !node.contains("ggpht.com") && 
                !node.contains("googleusercontent.com") &&
                !node.contains("schema.org")) {
                return node
            }
        }
        if (node is JSONArray) {
            for (i in 0 until node.length()) {
                val res = extractUrl(node.opt(i))
                if (res != null) return res
            }
        }
        return null
    }

    // 🔍 Cerca la stringa più lunga (La Descrizione!)
    private fun extractLongestString(node: Any?, minLen: Int): String? {
        var best: String? = null
        if (node is String) {
            if (node.length >= minLen && !node.startsWith("http") && !node.contains("0ahUKE") && !node.contains("zqKXas") && node.contains(" ")) {
                best = node
            }
        }
        if (node is JSONArray) {
            for (i in 0 until node.length()) {
                val res = extractLongestString(node.opt(i), minLen)
                if (res != null && (best == null || res.length > best.length)) {
                    best = res
                }
            }
        }
        return best
    }

    // 🔍 Cerca un array contenente multiple stringhe brevi (I Tipi!)
    private fun extractTypes(entityArray: JSONArray): List<String> {
        val types = mutableListOf<String>()
        fun findTypesArray(node: Any?) {
            if (node is JSONArray) {
                var stringCount = 0
                for (i in 0 until node.length()) {
                    val s = node.optString(i)
                    if (s != null && s.length in 3..30 && !s.contains("http") && !s.contains("0a")) {
                        stringCount++
                    }
                }
                if (stringCount >= 2) {
                    for (i in 0 until node.length()) {
                        val s = node.optString(i)
                        if (s != null && s.length in 3..30) types.add(s)
                    }
                }
                for (i in 0 until node.length()) findTypesArray(node.opt(i))
            }
        }
        findTypesArray(entityArray)
        return types.distinct()
    }

    // 📸 Foto (Mantenuto il tuo walker ricorsivo che funziona perfettamente)
    private fun extractPhotos(root: JSONArray): List<PhotoDto> {
        val photos = mutableListOf<PhotoDto>()
        val seen = mutableSetOf<String>()

        fun walk(node: Any?) {
            if (node == null || photos.size >= 20) return
            if (node is String && node.contains("lh3.googleusercontent.com")) {
                var url = node

                if (url.contains("/a/") || url.contains("/a-/") || url.contains("ACg8oc")) return
                if (url.contains("streetview") || url.contains("cb_client") || url.contains("maps_api_")) return
                if (url.contains("=w") && url.contains("-h") && Regex("=w\\d+-h\\d+").containsMatchIn(url)) return
                if (url.contains("=s") && Regex("=s\\d+").containsMatchIn(url)) return

                val escapeIdx = url.indexOf("\\u003d")
                if (escapeIdx != -1) {
                    url = url.substring(0, escapeIdx)
                } else {
                    val eqIdx = url.indexOf('=')
                    if (eqIdx != -1 && eqIdx > 30) {
                        url = url.substring(0, eqIdx)
                    }
                }
                val qIdx = url.indexOf('?')
                if (qIdx != -1) url = url.substring(0, qIdx)

                url = "$url=w1080-h608-p-k-no"

                if (!seen.contains(url)) {
                    seen.add(url)
                    photos.add(PhotoDto(
                        key = "p${photos.size}",
                        url = url,
                        width = null,
                        height = null
                    ))
                }
            }
            if (node is JSONArray) {
                for (i in 0 until node.length()) walk(node.opt(i))
            }
            if (node is JSONObject) {
                val keys = node.keys()
                while (keys.hasNext()) {
                    walk(node.opt(keys.next()))
                }
            }
        }
        walk(root)
        return photos
    }

    // 🔍 Cerca l'array delle recensioni analizzando la "forma" dei sotto-array
    private fun extractReviews(root: JSONArray): List<ReviewDto> {
        val reviews = mutableListOf<ReviewDto>()
        
        fun findReviewsArray(node: Any?) {
            if (node is JSONArray) {
                var reviewLikeCount = 0
                for (i in 0 until node.length()) {
                    val sub = node.optJSONArray(i)
                    if (sub != null && sub.length() > 5) {
                        val author = sub.optString(1)
                        val rating = try { sub.optInt(3) } catch (_: Throwable) { -1 }
                        if (author != null && author.length > 2 && rating in 1..5) {
                            reviewLikeCount++
                        }
                    }
                }
                if (reviewLikeCount >= 3) {
                    for (i in 0 until minOf(node.length(), 10)) {
                        val sub = node.optJSONArray(i) ?: continue
                        val authorName = sub.optString(1) ?: continue
                        val authorPhotoUrl = sub.optString(2)
                        val rating = try { sub.optInt(3) } catch (_: Throwable) { null }
                        val timeText = sub.optString(4)
                        val reviewText = try {
                            val textArr = sub.optJSONArray(15)
                            textArr?.optString(0)
                        } catch (_: Throwable) { null }
                        reviews.add(ReviewDto(authorName, authorPhotoUrl, rating, timeText, reviewText))
                    }
                    return
                }
                for (i in 0 until node.length()) findReviewsArray(node.opt(i))
            }
        }
        findReviewsArray(root)
        return reviews
    }
}
