package com.travelpins.test.importer

import com.travelpins.test.data.Place
import org.json.JSONArray
import org.json.JSONObject

object PlaceJsonParser {

    fun parse(json: String): List<Place> {
        val result = mutableListOf<Place>()

        try {
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                result.add(
                    Place(
                        name = obj.optString("name"),
                        address = obj.optString("address").ifBlank { null },
                        latitude = if (obj.has("latitude"))
                            obj.optDouble("latitude")
                        else null,
                        longitude = if (obj.has("longitude"))
                            obj.optDouble("longitude")
                        else null,
                        googleMapsUrl = obj.optString("url").ifBlank { null }
                    )
                )
            }
        } catch (_: Exception) {
            // JSON non valido: restituisce lista vuota
        }

        return result
    }
}
