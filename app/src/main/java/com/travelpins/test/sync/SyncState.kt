package com.travelpins.test.sync

import android.content.Context
import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class SyncStatus { DISCONNECTED, CONNECTED, SYNCING, ERROR }

/** Metadati di un file candidato selezionato col picker, in attesa di conferma UI. */
data class SyncCandidate(
    val uri: Uri,
    val documentId: String?,
    val fileName: String?,
    val syncFileId: String?,
    val revision: Long,
    val lastDeviceId: String,
    val listCount: Int,
    val categoryCount: Int
)

/**
 * Stato persistente della sincronizzazione, su SharedPreferences dedicate
 * ("travelpins_sync_prefs"), separate da "travelpins_prefs" del repository.
 *
 * Regole approvate:
 * - il syncFileId NON è salvato qui: vive solo dentro il documento;
 * - documentId (tecnico SAF/Drive) e syncFileId (logico) restano concetti distint;
 * - i pending tombstone sopravvivono a kill/restart e vengono puliti solo
 *   dopo una scrittura Drive realmente riuscita (gestito dal manager).
 */
class SyncState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("travelpins_sync_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(SyncStatus.DISCONNECTED)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _candidate = MutableStateFlow<SyncCandidate?>(null)
    val candidate: StateFlow<SyncCandidate?> = _candidate.asStateFlow()

    var lastError: String? = null
        private set

    fun setStatus(status: SyncStatus, error: String? = null) {
        lastError = error
        _status.value = status
    }

    fun setCandidate(candidate: SyncCandidate?) {
        _candidate.value = candidate
    }

    // ===== Riferimento al file Drive =====

    var syncUri: Uri?
        get() = prefs.getString("sync_uri", null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString("sync_uri", value?.toString()).apply()

    var documentId: String?
        get() = prefs.getString("sync_document_id", null)
        set(value) = prefs.edit().putString("sync_document_id", value).apply()

    var fileName: String?
        get() = prefs.getString("sync_file_name", null)
        set(value) = prefs.edit().putString("sync_file_name", value).apply()

    var mimeType: String?
        get() = prefs.getString("sync_mime", null)
        set(value) = prefs.edit().putString("sync_mime", value).apply()

    /** Identità del dispositivo, generata una volta e persistita. */
    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", generated).apply()
            return generated
        }

    var lastSyncTs: Long
        get() = prefs.getLong("last_sync_ts", 0L)
        set(value) = prefs.edit().putLong("last_sync_ts", value).apply()

    var lastSyncRevision: Long
        get() = prefs.getLong("last_sync_revision", 0L)
        set(value) = prefs.edit().putLong("last_sync_revision", value).apply()

    /** Backup locale dell'ultima versione remota VALIDA letta (prima del merge). */
    var lastValidRemoteJson: String?
        get() = prefs.getString("last_valid_remote_json", null)
        set(value) = prefs.edit().putString("last_valid_remote_json", value).apply()

    // ===== Clock collaborativi (solo modifiche utente) =====

    fun getPlaceClocks(): Map<String, Long> = readLongMap("collab_clock_places")
    fun setPlaceClock(key: String, ts: Long) = writeLongMapEntry("collab_clock_places", key, ts)

    fun getCategoryClocks(): Map<String, Long> = readLongMap("collab_clock_categories")
    fun setCategoryClock(uuid: String, ts: Long) = writeLongMapEntry("collab_clock_categories", uuid, ts)

    // ===== Pending tombstone (persistenti) =====

    fun getPendingPlaceTombstones(): Map<String, Long> = readLongMap("pending_place_tombstones")
    fun setPendingPlaceTombstone(key: String, ts: Long) = writeLongMapEntry("pending_place_tombstones", key, ts)
    fun removePendingPlaceTombstone(key: String) = removeLongMapEntry("pending_place_tombstones", key)
    fun clearPendingPlaceTombstones() = prefs.edit().remove("pending_place_tombstones").apply()

    fun getPendingCategoryTombstones(): Map<String, Long> = readLongMap("pending_category_tombstones")
    fun setPendingCategoryTombstone(uuid: String, ts: Long) = writeLongMapEntry("pending_category_tombstones", uuid, ts)
    fun removePendingCategoryTombstone(uuid: String) = removeLongMapEntry("pending_category_tombstones", uuid)
    fun clearPendingCategoryTombstones() = prefs.edit().remove("pending_category_tombstones").apply()

    // ===== Utility mappe Long in prefs =====

    private fun readLongMap(key: String): Map<String, Long> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val out = mutableMapOf<String, Long>()
            for (k in json.keys()) out[k] = json.optLong(k, 0L)
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeLongMapEntry(key: String, entryKey: String, ts: Long) {
        val map = readLongMap(key).toMutableMap()
        map[entryKey] = ts
        prefs.edit().putString(key, JSONObject(map as Map<*, *>).toString()).apply()
    }

    private fun removeLongMapEntry(key: String, entryKey: String) {
        val map = readLongMap(key).toMutableMap()
        map.remove(entryKey)
        if (map.isEmpty()) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, JSONObject(map as Map<*, *>).toString()).apply()
    }
}
