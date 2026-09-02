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

    fun parse(rawJson: String): PlaceDetails? {
        return try {
            val root = JSONArray(rawJson)
            val name = extractName(root)
            val rating = extractRating(root)
            val reviewCount = extractReviewCount(root)
            val websiteUrl = extractWebsite(root)
            val types = extractTypes(root)
            val description = extractDescription(root)
            val photos = extractPhotos(root)
            val reviews = extractReviews(root)
            PlaceDetails(name, rating, reviewCount, websiteUrl, types, description, photos, reviews)
        } catch (t: Throwable) {
            null
        }
    }

    private fun extractName(root: JSONArray): String? {
        return try {
            val arr = root.optJSONArray(6)
            arr?.optString(1)
        } catch (t: Throwable) { null }
    }

    private fun extractRating(root: JSONArray): Double? {
        return try {
            val arr = root.optJSONArray(4)
            val sub = arr?.optJSONArray(7)
            val raw = sub?.opt(0)
            when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }
        } catch (t: Throwable) { null }
    }

    private fun extractReviewCount(root: JSONArray): Int? {
        return try {
            val arr = root.optJSONArray(4)
            val sub = arr?.optJSONArray(8)
            val raw = sub?.opt(0)
            when (raw) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull()
                else -> null
            }
        } catch (t: Throwable) { null }
    }

    private fun extractWebsite(root: JSONArray): String? {
        return try {
            val arr = root.optJSONArray(17)
            arr?.optString(1)
        } catch (t: Throwable) { null }
    }

    private fun extractTypes(root: JSONArray): List<String> {
        val types = mutableListOf<String>()
        try {
            val arr = root.optJSONArray(4)
            val sub = arr?.optJSONArray(3)
            if (sub != null) {
                for (i in 0 until sub.length()) {
                    val t = sub.optString(i)
                    if (!t.isNullOrBlank()) types.add(t)
                }
            }
        } catch (_: Throwable) { }
        return types
    }

    private fun extractDescription(root: JSONArray): String? {
        return try {
            val arr = root.optJSONArray(32)
            val sub = arr?.optJSONArray(1)
            val sub2 = sub?.optJSONArray(0)
            val sub3 = sub2?.optJSONArray(0)
            val sub4 = sub3?.optJSONArray(2)
            sub4?.optString(0)
        } catch (t: Throwable) { null }
    }

    // 🔥 VERSIONE AGGIORNATA CON FILTRO ANTI-SPAZZATURA
    private fun extractPhotos(root: JSONArray): List<PhotoDto> {
        val photos = mutableListOf<PhotoDto>()
        val seen = mutableSetOf<String>()

        fun walk(node: Any?) {
            if (node == null || photos.size >= 20) return
            if (node is String && node.contains("lh3.googleusercontent.com")) {
                var url = node

                // 🚫 FILTRO: Scarta avatar utenti
                if (url.contains("/a/") || url.contains("/a-/") || url.contains("ACg8oc")) return

                // 🚫 FILTRO: Scarta Street View e tile mappa
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

                // ✅ Forza alta risoluzione
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

    private fun extractReviews(root: JSONArray): List<ReviewDto> {
        val reviews = mutableListOf<ReviewDto>()
        try {
            val arr = root.optJSONArray(51)
            if (arr != null) {
                for (i in 0 until minOf(arr.length(), 10)) {
                    val reviewArr = arr.optJSONArray(i) ?: continue
                    val authorName = reviewArr.optString(1) ?: continue
                    val authorPhotoUrl = reviewArr.optString(2)
                    val rating = try { reviewArr.optInt(3) } catch (_: Throwable) { null }
                    val timeText = reviewArr.optString(4)
                    val reviewText = try {
                        val textArr = reviewArr.optJSONArray(15)
                        textArr?.optString(0)
                    } catch (_: Throwable) { null }
                    reviews.add(ReviewDto(authorName, authorPhotoUrl, rating, timeText, reviewText))
                }
            }
        } catch (_: Throwable) { }
        return reviews
    }
}
