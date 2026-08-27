package com.travelpins.test.importer

import com.travelpins.test.data.Place
import org.json.JSONArray

object PlaceJsonParser {

    fun parse(
        json: String,
        sourceListId: String?,
        sourceListName: String?
    ): List<Place> {
        val result = mutableListOf<Place>()

        try {
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                if (!obj.has("lat") || !obj.has("lng")) {
                    continue
                }

                result.add(
                    Place(
                        name = obj.optString("name"),
                        address = obj.optString("address").ifBlank { null },
                        latitude = obj.optDouble("lat"),
                        longitude = obj.optDouble("lng"),
                        placeId = obj.optString("placeId").ifBlank { null },
                        mapsUrl = obj.optString("mapsUrl").ifBlank { null },
                        mapsPlaceRef = obj.optString("hexPair").ifBlank { null },
                        sourceListId = sourceListId,
                        sourceListName = sourceListName
                    )
                )
            }
        } catch (_: Exception) {
            // JSON non valido: restituisce lista vuota
        }

        return result
    }
}
