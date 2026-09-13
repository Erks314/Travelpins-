package com.travelpins.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<Category>>

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    // ===== Query aggiunte per la sincronizzazione Drive =====

    /** Ricerca una categoria per uuid globale (usata dal merge in import). */
    @Query("SELECT * FROM categories WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Category?

    /** Categorie prive di uuid: target del backfill eseguito una sola volta. */
    @Query("SELECT * FROM categories WHERE uuid = ''")
    suspend fun getAllWithEmptyUuid(): List<Category>

    /** Assegna un uuid a una categoria esistente (backfill). */
    @Query("UPDATE categories SET uuid = :uuid WHERE id = :id")
    suspend fun updateUuid(id: Long, uuid: String)

    /** Elimina una categoria per uuid (apply di un tombstone remoto). */
    @Query("DELETE FROM categories WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)
}
