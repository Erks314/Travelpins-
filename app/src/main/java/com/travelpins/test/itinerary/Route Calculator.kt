package com.travelpins.test.itinerary

import com.travelpins.test.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calcola il percorso usando Google Routes API.
 * Una sola richiesta per itinerario completo (origin + destination + intermediates).
 */
object RouteCalculator {
    private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    sealed class Result {
        data class Success(val route: ItineraryRoute) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun compute(places: List<ItineraryPlace>): Result = withContext(Dispatchers.IO) {
        if (places.size < 2) {
            return@withContext Result.Error("Servono almeno 2 luoghi per calcolare un itinerario")
        }

        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.Error("API key non configurata")
        }

        try {
            val url = URL(ROUTES_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", apiKey)
            connection.setRequestProperty(
                "X-Goog-FieldMask",
                "routes.duration,routes.distanceMeters,routes.legs,routes.polyline.encodedPolyline"
            )

            val jsonBody = buildRequestBody(places)
            val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorMessage = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).readText()
                } else {
                    "HTTP $responseCode"
                }
                return@withContext Result.Error("Errore API: $errorMessage")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            val route = parseResponse(response, places)
            return@withContext Result.Success(route)
        } catch (e: Exception) {
            Result.Error("Errore di connessione: ${e.message}")
        }
    }

    private fun buildRequestBody(places: List<ItineraryPlace>): JSONObject {
        val origin = places.first()
        val destination = places.last()
        val intermediates = places.drop(1).dropLast(1)

        val body = JSONObject()
        body.put("origin", buildLocation(origin))
        body.put("destination", buildLocation(destination))
        
        if (intermediates.isNotEmpty()) {
            val intermediatesArray = JSONArray()
            intermediates.forEach { place ->
                intermediatesArray.put(buildLocation(place))
            }
            body.put("intermediates", intermediatesArray)
        }

        body.put("travelMode", "DRIVE")
        body.put("routingPreference", "TRAFFIC_UNAWARE")
        body.put("computeAlternativeRoutes", false)

        return body
    }

    private fun buildLocation(place: ItineraryPlace): JSONObject {
        val location = JSONObject()
        val latLng = JSONObject()
        latLng.put("latitude", place.latitude)
        latLng.put("longitude", place.longitude)
        location.put("latLng", latLng)
        
        val wrapper = JSONObject()
        wrapper.put("location", location)
        return wrapper
    }

    private fun parseResponse(response: String, places: List<ItineraryPlace>): ItineraryRoute {
        val json = JSONObject(response)
        val routes = json.optJSONArray("routes") ?: throw Exception("Nessun percorso trovato")
        
        if (routes.length() == 0) {
            throw Exception("Nessun percorso disponibile")
        }

        val route = routes.getJSONObject(0)
        val totalDistance = route.optInt("distanceMeters", 0)
        val totalDuration = parseDuration(route.optString("duration", "0s"))
        val polyline = route.optJSONObject("polyline")?.optString("encodedPolyline")

        val legs = mutableListOf<ItineraryLeg>()
        val legsArray = route.optJSONArray("legs")
        
        if (legsArray != null && legsArray.length() > 0) {
            for (i in 0 until legsArray.length()) {
                val legJson = legsArray.getJSONObject(i)
                val legDistance = legJson.optInt("distanceMeters", 0)
                val legDuration = parseDuration(legJson.optString("duration", "0s"))
                
                val startPlace = if (i < places.size) places[i] else places.first()
                val endPlace = if (i + 1 < places.size) places[i + 1] else places.last()
                
                legs.add(
                    ItineraryLeg(
                        distanceMeters = legDistance,
                        durationSeconds = legDuration,
                        startPlace = startPlace,
                        endPlace = endPlace
                    )
                )
            }
        }

        return ItineraryRoute(
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration,
            legs = legs,
            polylineEncoded = polyline
        )
    }

    private fun parseDuration(duration: String): Int {
        // Formato: "1234s" -> 1234
        return duration.replace("s", "").toIntOrNull() ?: 0
    }
}
