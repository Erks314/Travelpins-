package com.travelpins.test.sync

import org.json.JSONObject

/** Versione dello schema JSON supportata da questa build. */
const val SUPPORTED_SCHEMA_VERSION = 1

/**
 * Radice del documento travelpins_sync.json.
 *
 * - [syncFileId] è l'identità LOGICA del documento: generata una sola volta
 *   (al primo write che inizializza il file) e poi immutabile. Può essere null
 *   quando il file proviene da una versione precedente: in tal caso viene
 *   aggiunto al primo write riuscito. NON vive nelle prefs locali.
 * - [revision] è il timestamp dell'ultima scrittura riuscita.
 * - [deviceId] è l'identità del dispositivo che ha effettuato l'ultima scrittura.
 */
data class SyncRoot(
    val schemaVersion: Int,
    val revision: Long,
    val deviceId: String,
    val syncFileId: String?,
    val categories: Map<String, CategoryRecord>,
    val lists: Map<String, ListRecord>
)

data class CategoryRecord(
    val uuid: String,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val sortOrder: Int,
    val updatedAt: Long,
    val updatedBy: String,
    val deleted: Boolean
)

data class ListRecord(
    val name: String?,
    val places: Map<String, PlaceRecord>
)

/**
 * Dati collaborativi di un luogo. La chiave nella mappa [ListRecord.places]
 * è la canonical key (sourceListId|name|lat|lng con Double.toString()).
 * I campi name/latitude/longitude sono informativi: Drive NON è fonte di
 * verità per l'esistenza né per l'anagrafica dei luoghi.
 */
data class PlaceRecord(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val categoryUuid: String?,
    val note: String?,
    val updatedAt: Long,
    val updatedBy: String,
    val deleted: Boolean
)

/**
 * Serializzazione/deserializzazione del documento con org.json (già presente
 * nel progetto). Il parse restituisce null su JSON illeggibile, schema non
 * supportato o struttura invalida: in tal caso il chiamante DEVE abortire
 * senza scrivere nulla sul Drive e senza toccare Room.
 */
object SyncJson {

    fun parse(raw: String): SyncRoot? {
        return try {
            val root = JSONObject(raw)

            val schemaVersion = root.optInt("schemaVersion", -1)
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) return null

            val categories = mutableMapOf<String, CategoryRecord>()
            val categoriesJson = root.optJSONObject("categories") ?: JSONObject()
            for (uuid in categoriesJson.keys()) {
                val c = categoriesJson.optJSONObject(uuid) ?: continue
                val recordUuid = c.optString("uuid", uuid)
                if (recordUuid.isBlank()) return null
                categories[uuid] = CategoryRecord(
                    uuid = recordUuid,
                    name = c.optString("name", ""),
                    colorArgb = c.optInt("colorArgb", 0),
                    iconKey = c.optString("iconKey", "place"),
                    sortOrder = c.optInt("sortOrder", 0),
                    updatedAt = c.optLong("updatedAt", 0L),
                    updatedBy = c.optString("updatedBy", ""),
                    deleted = c.optBoolean("deleted", false)
                )
            }

            val lists = mutableMapOf<String, ListRecord>()
            val listsJson = root.optJSONObject("lists") ?: JSONObject()
            for (listId in listsJson.keys()) {
                val l = listsJson.optJSONObject(listId) ?: continue
                val places = mutableMapOf<String, PlaceRecord>()
                val placesJson = l.optJSONObject("places") ?: JSONObject()
                for (key in placesJson.keys()) {
                    val p = placesJson.optJSONObject(key) ?: continue
                    places[key] = PlaceRecord(
                        name = p.optString("name", ""),
                        latitude = p.optDouble("latitude", 0.0),
                        longitude = p.optDouble("longitude", 0.0),
                        categoryUuid = p.optString("categoryUuid", "").takeIf { it.isNotBlank() },
                        note = p.optString("note", "").takeIf { it.isNotBlank() },
                        updatedAt = p.optLong("updatedAt", 0L),
                        updatedBy = p.optString("updatedBy", ""),
                        deleted = p.optBoolean("deleted", false)
                    )
                }
                lists[listId] = ListRecord(name = l.optString("name", "").takeIf { it.isNotBlank() }, places = places)
            }

            SyncRoot(
                schemaVersion = schemaVersion,
                revision = root.optLong("revision", 0L),
                deviceId = root.optString("deviceId", ""),
                syncFileId = root.optString("syncFileId", "").takeIf { it.isNotBlank() },
                categories = categories,
                lists = lists
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serialize(root: SyncRoot): String {
        val out = JSONObject()
        out.put("schemaVersion", root.schemaVersion)
        out.put("revision", root.revision)
        out.put("deviceId", root.deviceId)
        if (root.syncFileId != null) out.put("syncFileId", root.syncFileId)

        val categoriesJson = JSONObject()
        for ((uuid, c) in root.categories) {
            val cj = JSONObject()
            cj.put("uuid", c.uuid)
            cj.put("name", c.name)
            cj.put("colorArgb", c.colorArgb)
            cj.put("iconKey", c.iconKey)
            cj.put("sortOrder", c.sortOrder)
            cj.put("updatedAt", c.updatedAt)
            cj.put("updatedBy", c.updatedBy)
            cj.put("deleted", c.deleted)
            categoriesJson.put(uuid, cj)
        }
        out.put("categories", categoriesJson)

        val listsJson = JSONObject()
        for ((listId, l) in root.lists) {
            val lj = JSONObject()
            if (l.name != null) lj.put("name", l.name)
            val placesJson = JSONObject()
            for ((key, p) in l.places) {
                val pj = JSONObject()
                pj.put("name", p.name)
                pj.put("latitude", p.latitude)
                pj.put("longitude", p.longitude)
                if (p.categoryUuid != null) pj.put("categoryUuid", p.categoryUuid)
                if (p.note != null) pj.put("note", p.note)
                pj.put("updatedAt", p.updatedAt)
                pj.put("updatedBy", p.updatedBy)
                pj.put("deleted", p.deleted)
                placesJson.put(key, pj)
            }
            lj.put("places", placesJson)
            listsJson.put(listId, lj)
        }
        out.put("lists", listsJson)

        return out.toString(2)
    }
}
