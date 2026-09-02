package com.tuopackage // <-- RICORDA: Adatta questo al tuo package reale!

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

class TravelPinsJsBridge(
    private val onPoiSaved: (PlaceData) -> Unit,
    private val onCitySkipped: (String) -> Unit
) {

    companion object {
        private const val TAG = "TravelPinsBridge"
    }

    /**
     * Questo è il metodo che il tuo codice JavaScript chiama tramite window.AndroidBridge.receivePlaceDetails(...)
     */
    @JavascriptInterface
    fun receivePlaceDetails(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            
            // 1. Estrazione Dati Base
            val name = json.optString("name", "Sconosciuto")
            val rating = if (json.has("rating") && !json.isNull("rating")) json.getDouble("rating") else null
            val reviewCount = if (json.has("reviewCount") && !json.isNull("reviewCount")) json.getInt("reviewCount") else null
            val websiteUrl = json.optString("websiteUrl", null).takeIf { it != "null" }
            val description = json.optString("description", null).takeIf { it != "null" }
            
            // 2. Parsing Tipi
            val typesArray = json.optJSONArray("types")
            val types = mutableListOf<String>()
            if (typesArray != null) {
                for (i in 0 until typesArray.length()) {
                    types.add(typesArray.getString(i))
                }
            }

            // 3. Parsing Foto
            val photosArray = json.optJSONArray("photos")
            val photos = mutableListOf<PhotoData>()
            if (photosArray != null) {
                for (i in 0 until photosArray.length()) {
                    val p = photosArray.getJSONObject(i)
                    photos.add(PhotoData(p.getString("url"), p.optInt("width", 0), p.optInt("height", 0)))
                }
            }

            // 4. Parsing Recensioni
            val reviewsArray = json.optJSONArray("reviews")
            val reviews = mutableListOf<ReviewData>()
            if (reviewsArray != null) {
                for (i in 0 until reviewsArray.length()) {
                    val r = reviewsArray.getJSONObject(i)
                    reviews.add(ReviewData(r.getString("author"), r.optInt("rating", 5), r.getString("text")))
                }
            }

            // ==========================================
            // 🛡️ FILTRO ANTI-CITTÀ / REGIONI
            // ==========================================
            // Google Maps NON assegna Rating e Recensioni alle Città o alle Contee.
            // Se mancano entrambi, è quasi certamente un'area geografica e non un POI.
            val isCityOrRegion = (rating == null && reviewCount == null)
            
            // Filtro di sicurezza extra: controlla se i "Tipi" contengono solo nomi di nazioni o contee
            val genericKeywords = listOf("irlanda", "regno unito", "italia", "co. ", "county ", "nordirlanda")
            val hasGenericTypes = types.isNotEmpty() && types.any { type ->
                genericKeywords.any { kw -> type.startsWith(kw, ignoreCase = true) || type.equals(kw.trim(), ignoreCase = true) }
            }

            if (isCityOrRegion || (rating == null && hasGenericTypes)) {
                Log.w(TAG, "🏙️ Filtro Anti-Città: Scartato '$name' (è una città/regione, non un POI)")
                onCitySkipped.invoke(name)
                return // Blocca il salvataggio
            }

            // ==========================================
            // ✅ SALVATAGGIO POI VALIDO
            // ==========================================
            Log.d(TAG, "✅ POI Valido: $name (Rating: $rating, Recensioni: $reviewCount, Tipi: $types)")
            
            val placeData = PlaceData(
                name = name,
                rating = rating,
                reviewCount = reviewCount,
                websiteUrl = websiteUrl,
                types = types,
                description = description,
                photos = photos,
                reviews = reviews
            )
            
            onPoiSaved.invoke(placeData)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel parsing dei dati dal JS", e)
        }
    }

    // ==========================================
    // 📦 Data Classes per il passaggio dati
    // ==========================================
    data class PlaceData(
        val name: String,
        val rating: Double?,
        val reviewCount: Int?,
        val websiteUrl: String?,
        val types: List<String>,
        val description: String?,
        val photos: List<PhotoData>,
        val reviews: List<ReviewData>
    )

    data class PhotoData(val url: String, val width: Int, val height: Int)
    data class ReviewData(val author: String, val rating: Int, val text: String)
}
