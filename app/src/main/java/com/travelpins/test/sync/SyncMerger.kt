package com.travelpins.test.sync

import com.travelpins.test.data.Category
import com.travelpins.test.data.Place

/** Età massima di un tombstone nel documento prima del cleanup (30 giorni). */
private const val TOMBSTONE_TTL_MS = 30L * 24L * 60L * 60L * 1000L

/**
 * Canonical key di un luogo: identità stabile condivisa tra Room e Drive.
 * Usa Double.toString() per serializzare le coordinate in modo deterministico
 * e bit-esatto, coerente con la semantica di Triple(name, lat, lng) usata da
 * syncListPlaces(). Nessun arrotondamento arbitrario.
 */
fun canonicalPlaceKey(sourceListId: String, name: String, latitude: Double, longitude: Double): String {
    return "$sourceListId|${escapeName(name.trim())}|${latitude.toString()}|${longitude.toString()}"
}

fun canonicalPlaceKey(place: Place): String? {
    val listId = place.sourceListId ?: return null
    return canonicalPlaceKey(listId, place.name, place.latitude, place.longitude)
}

/** Evita ambiguità del separatore '|' nei nomi. */
private fun escapeName(name: String): String = name.replace("|", "¦")

/** Istantanea dello stato locale usata dal merge. */
data class LocalSnapshot(
    val categories: List<Category>,
    val places: List<Place>,
    val placeClocks: Map<String, Long>,
    val categoryClocks: Map<String, Long>,
    val pendingPlaceTombstones: Map<String, Long>,
    val pendingCategoryTombstones: Map<String, Long>,
    val lastSyncTs: Long
)

/** Azione da applicare al Room per i dati collaborativi di un luogo. */
data class PlaceCollabAction(
    val placeId: Long,
    val categoryUuid: String?,
    val note: String?
)

/** Risultato del merge: documento da scrivere + azioni da applicare al Room. */
data class MergeOutput(
    val newRoot: SyncRoot,
    val categoryUpserts: List<Category>,
    val categoryDeletes: List<String>,
    val placeCollab: List<PlaceCollabAction>
)

/**
 * Merge bidirezionale per elemento con Last-Write-Wins.
 *
 * Regole implementate:
 * - Category identity = uuid; uuid diversi = categorie diverse, mai merge automatico.
 * - Place identity = canonical key; Drive non crea né elimina luoghi in Room.
 * - Stati A-E del piano (attivo/assente+tombstone/ricomparsa/nuovo/locale-piu-recente).
 * - INVARIANTE: se un luogo esiste in Room, nel documento scritto deleted DEVE essere false.
 * - I pending tombstone locali vincono solo se più recenti del record remoto attivo (LWW).
 * - syncFileId NON viene generato qui: il merge lo propaga immutato (può restare null;
 *   sarà il manager a generarlo al primo write).
 */
object SyncMerger {

    fun merge(
        local: LocalSnapshot,
        remote: SyncRoot,
        deviceId: String,
        now: Long
    ): MergeOutput {
        val categoryUpserts = mutableListOf<Category>()
        val categoryDeletes = mutableListOf<String>()
        val placeCollab = mutableListOf<PlaceCollabAction>()

        // ================= CATEGORIE =================
        val outCategories = mutableMapOf<String, CategoryRecord>()
        val localByUuid = local.categories.associateBy { it.uuid }
        val allCategoryUuids = (remote.categories.keys + localByUuid.keys).toSet()

        for (uuid in allCategoryUuids) {
            val localCat = localByUuid[uuid]
            val remoteRec = remote.categories[uuid]
            val localClock = local.categoryClocks[uuid] ?: 0L
            val pendingDeleteTs = local.pendingCategoryTombstones[uuid]

            when {
                // Tombstone locale pendente: la delete locale deve propagarsi.
                pendingDeleteTs != null -> {
                    val remoteTs = remoteRec?.updatedAt ?: 0L
                    if (pendingDeleteTs >= remoteTs) {
                        // La delete locale vince: scriviamo tombstone.
                        outCategories[uuid] = CategoryRecord(
                            uuid = uuid,
                            name = remoteRec?.name ?: localCat?.name ?: "",
                            colorArgb = remoteRec?.colorArgb ?: localCat?.colorArgb ?: 0,
                            iconKey = remoteRec?.iconKey ?: localCat?.iconKey ?: "place",
                            sortOrder = remoteRec?.sortOrder ?: localCat?.sortOrder ?: 0,
                            updatedAt = pendingDeleteTs,
                            updatedBy = deviceId,
                            deleted = true
                        )
                        if (localCat != null) categoryDeletes.add(uuid)
                    } else {
                        // Modifica remota più recente della nostra delete: LWW remoto.
                        if (remoteRec != null) {
                            outCategories[uuid] = remoteRec
                            if (localCat != null && !remoteRec.deleted) {
                                categoryUpserts.add(remoteRec.toCategory(localCat.id))
                            } else if (localCat != null && remoteRec.deleted) {
                                categoryDeletes.add(uuid)
                            }
                        }
                    }
                }

                // Tombstone remoto.
                remoteRec != null && remoteRec.deleted -> {
                    if (localCat != null) {
                        if (localClock > remoteRec.updatedAt) {
                            // Locale modificato dopo il tombstone remoto: vince locale.
                            outCategories[uuid] = localCat.toRecord(localClockOr(localClock, local.lastSyncTs), deviceId, deleted = false)
                        } else {
                            // Tombstone remoto vince: elimina localmente.
                            outCategories[uuid] = remoteRec
                            categoryDeletes.add(uuid)
                        }
                    } else {
                        // Nessuna categoria locale: il tombstone resta (cleanup dopo).
                        outCategories[uuid] = remoteRec
                    }
                }

                // Record remoto attivo.
                remoteRec != null -> {
                    if (localCat != null) {
                        if (remoteRec.updatedAt >= localClock) {
                            // Vince remoto: aggiorna Room.
                            outCategories[uuid] = remoteRec
                            categoryUpserts.add(remoteRec.toCategory(localCat.id))
                        } else {
                            // Vince locale: propaga i valori locali.
                            outCategories[uuid] = localCat.toRecord(localClock, deviceId, deleted = false)
                        }
                    } else {
                        // Categoria remota sconosciuta localmente: crea in Room con uuid remoto.
                        outCategories[uuid] = remoteRec
                        categoryUpserts.add(remoteRec.toCategory(0L))
                    }
                }

                // Solo locale: non ancora nel documento.
                localCat != null -> {
                    val ts = localClockOr(localClock, local.lastSyncTs)
                    outCategories[uuid] = localCat.toRecord(ts, deviceId, deleted = false)
                }
            }
        }

        // ================= LUOGHI (per lista) =================
        val outLists = mutableMapOf<String, ListRecord>()
        val localPlacesByList = local.places.groupBy { it.sourceListId ?: "" }
        val allListIds = (remote.lists.keys + localPlacesByList.keys.filter { it.isNotBlank() }).toSet()

        for (listId in allListIds) {
            val remoteList = remote.lists[listId]
            val localListPlaces = localPlacesByList[listId] ?: emptyList()
            val localByKey = localListPlaces.mapNotNull { p -> canonicalPlaceKey(p)?.let { it to p } }.toMap()

            val outPlaces = mutableMapOf<String, PlaceRecord>()
            val allKeys = (remoteList?.places?.keys ?: emptySet()) + localByKey.keys +
                local.pendingPlaceTombstones.keys.filter { it.startsWith("$listId|") }

            for (key in allKeys) {
                val localPlace = localByKey[key]
                val remoteRec = remoteList?.places?.get(key)
                val localClock = local.placeClocks[key] ?: 0L
                val pendingDeleteTs = local.pendingPlaceTombstones[key]

                when {
                    // Luogo presente in Room: INVARIANTE deleted=false.
                    localPlace != null -> {
                        when {
                            // Stato C: ricomparsa dopo tombstone remoto.
                            remoteRec != null && remoteRec.deleted -> {
                                outPlaces[key] = remoteRec.copy(deleted = false, updatedAt = maxOf(remoteRec.updatedAt, localClock, now))
                                if (localClock <= remoteRec.updatedAt) {
                                    placeCollab.add(PlaceCollabAction(localPlace.id, remoteRec.categoryUuid, remoteRec.note))
                                }
                            }
                            // Stato A/E: record remoto attivo.
                            remoteRec != null -> {
                                if (remoteRec.updatedAt >= localClock) {
                                    outPlaces[key] = remoteRec
                                    placeCollab.add(PlaceCollabAction(localPlace.id, remoteRec.categoryUuid, remoteRec.note))
                                } else {
                                    outPlaces[key] = localPlace.toRecord(localClock, deviceId)
                                }
                            }
                            // Stato D: solo locale.
                            else -> {
                                outPlaces[key] = localPlace.toRecord(localClockOr(localClock, local.lastSyncTs), deviceId)
                            }
                        }
                    }

                    // Luogo assente da Room.
                    pendingDeleteTs != null -> {
                        val remoteTs = remoteRec?.updatedAt ?: 0L
                        if (pendingDeleteTs >= remoteTs) {
                            outPlaces[key] = (remoteRec ?: remoteRecFromKey(key)).copy(
                                updatedAt = pendingDeleteTs,
                                updatedBy = deviceId,
                                deleted = true
                            )
                        } else {
                            if (remoteRec != null) outPlaces[key] = remoteRec
                        }
                    }

                    remoteRec != null -> {
                        // Stato B o record attivo senza luogo locale: Drive non crea luoghi.
                        outPlaces[key] = remoteRec
                    }
                }
            }

            val listName = remoteList?.name ?: localListPlaces.firstOrNull()?.sourceListName
            outLists[listId] = ListRecord(name = listName, places = outPlaces)
        }

        // ================= CLEANUP TOMBSTONE =================
        val cutoff = now - TOMBSTONE_TTL_MS
        for ((uuid, rec) in outCategories.toMap()) {
            if (rec.deleted && rec.updatedAt < cutoff) outCategories.remove(uuid)
        }
        for ((listId, list) in outLists.toMap()) {
            val cleaned = list.places.filterValues { !(it.deleted && it.updatedAt < cutoff) }
            if (cleaned.isEmpty() && list.name == null) outLists.remove(listId)
            else outLists[listId] = list.copy(places = cleaned)
        }

        val newRoot = SyncRoot(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            revision = now,
            deviceId = deviceId,
            syncFileId = remote.syncFileId, // immutato; il manager lo genera se null al write
            categories = outCategories,
            lists = outLists
        )

        return MergeOutput(
            newRoot = newRoot,
            categoryUpserts = categoryUpserts,
            categoryDeletes = categoryDeletes,
            placeCollab = placeCollab
        )
    }

    private fun localClockOr(clock: Long, fallback: Long): Long = if (clock > 0) clock else fallback

    /** Ricostruisce un record minimale da una canonical key (per tombstone senza record remoto). */
    private fun remoteRecFromKey(key: String): PlaceRecord {
        val parts = key.split("|")
        return PlaceRecord(
            name = if (parts.size > 1) parts[1].replace("¦", "|") else "",
            latitude = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
            longitude = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
            categoryUuid = null,
            note = null,
            updatedAt = 0L,
            updatedBy = "",
            deleted = true
        )
    }

    private fun Category.toRecord(ts: Long, by: String, deleted: Boolean): CategoryRecord =
        CategoryRecord(
            uuid = uuid,
            name = name,
            colorArgb = colorArgb,
            iconKey = iconKey,
            sortOrder = sortOrder,
            updatedAt = ts,
            updatedBy = by,
            deleted = deleted
        )

    private fun CategoryRecord.toCategory(localId: Long): Category =
        Category(
            id = localId,
            uuid = uuid,
            name = name,
            colorArgb = colorArgb,
            iconKey = iconKey,
            sortOrder = sortOrder
        )

    private fun Place.toRecord(ts: Long, by: String): PlaceRecord {
        return PlaceRecord(
            name = name,
            latitude = latitude,
            longitude = longitude,
            categoryUuid = null, // risolto dal manager tramite id->uuid al momento del write
            note = note,
            updatedAt = ts,
            updatedBy = by,
            deleted = false
        )
    }
}
