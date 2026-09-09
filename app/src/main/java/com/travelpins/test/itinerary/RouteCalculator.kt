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
            return@withContext Result.Error("API key non configurata. Aggiungi MAPS_API_KEY in local.properties o passa -PMAPS_API_KEY=...")
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
                val errorBody = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: ""
                return@withContext Result.Error(friendlyError(responseCode, errorBody))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            val route = parseResponse(response, places)
            return@withContext Result.Success(route)
        } catch (e: Exception) {
            Result.Error("Impossibile calcolare l'itinerario. Controlla la connessione internet.")
        }
    }

    private fun friendlyError(code: Int, body: String): String {
        return when {
            code == 403 && body.contains("API_KEY_SERVICE_BLOCKED") ->
                "Routes API non abilitata per questa API key.\n\n" +
                "Come risolvere:\n" +
                "1. Apri console.cloud.google.com\n" +
                "2. Seleziona il progetto della tua API key\n" +
                "3. Vai su API e servizi → Libreria\n" +
                "4. Cerca \"Routes API\" e premi ABILITA\n" +
                "5. Riprova il calcolo"
            code == 403 && body.contains("API_KEY_INVALID") ->
                "API key non valida. Controlla il valore di MAPS_API_KEY."
            code == 403 ->
                "Accesso negato (403). Verifica che la API key abbia le restrizioni corrette e che Routes API sia abilitata."
            code == 400 ->
                "Richiesta non valida (400). Uno o più luoghi hanno coordinate non valide."
            code == 429 ->
                "Troppe richieste (429). Attendi qualche minuto e riprova."
            else ->
                "Errore API (codice $code). Riprova più tardi."
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
        val latLng = JSONObject()
        latLng.put("latitude", place.latitude)
        latLng.put("longitude", place.longitude)
        val location = JSONObject()
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
        return duration.replace("s", "").toIntOrNull() ?: 0
    }
}
