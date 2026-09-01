suspend fun syncListPlaces(
    listId: String,
    listName: String?,
    sourceUrl: String?,
    incoming: List<Place>
): SyncResult {
    val existing = placeDao.getPlacesByListId(listId)
    fun key(p: Place) = Triple(p.name, p.latitude, p.longitude)

    val existingByKey = existing.associateBy(::key)
    val incomingKeys = incoming.map(::key).toSet()

    // 1) Luoghi rimossi da Google -> elimina completamente
    val removedPlaces = existing.filter { key(it) !in incomingKeys }
    for (p in removedPlaces) deletePlaceCompletely(p)

    // 2) Luoghi ancora presenti -> sovrascrivi dati base e ri-arricchisci
    var updated = 0
    for (inc in incoming) {
        val ex = existingByKey[key(inc)]
        if (ex != null) {
            placeDao.updateBaseInfo(ex.id, inc.address, inc.mapsUrl, inc.placeId, inc.mapsPlaceRef)
            placeDao.clearDetailsFetched(ex.id)
            updated++
        }
    }

    // 3) Luoghi nuovi -> inserisci SOLO quelli che non esistono già
    val newPlaces = incoming.filter { key(it) !in existingByKey }
    val inserted = placeDao.insertAll(newPlaces)
    val added = inserted.count { it != -1L }

    // 4) Aggiorna intestazione lista
    val now = System.currentTimeMillis()
    val prev = sourceListDao.getById(listId)
    sourceListDao.upsert(
        SourceList(
            id = listId,
            name = listName ?: prev?.name,
            sourceUrl = sourceUrl ?: prev?.sourceUrl,
            coverUrl = prev?.coverUrl,
            createdAt = prev?.createdAt ?: now,
            updatedAt = now,
            placeCount = 0
        )
    )
    val total = placeDao.getPlacesByListId(listId).size
    sourceListDao.updateStats(listId, total, now)

    return SyncResult(added, removedPlaces.size, updated)
}
