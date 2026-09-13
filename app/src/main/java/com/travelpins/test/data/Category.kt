package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Identificatore stabile e globale per la sincronizzazione Drive.
    // - Generato in Kotlin (UUID.randomUUID()) da TravelPinsRepository.createCategory().
    // - Assegnato dal backfill (DriveSyncManager.start()) alle categorie esistenti.
    // - Imposto esplicitamente quando una categoria viene importata dal file Drive.
    // NON è l'id Room (che è locale al dispositivo): è la chiave con cui le
    // categorie vengono riconosciute tra dispositivi diversi.
    // Stesso uuid = stessa categoria. Uuid diversi = categorie diverse, sempre.
    val uuid: String = "",

    val name: String,
    val colorArgb: Int,
    val iconKey: String = "place",
    val sortOrder: Int = 0
)
