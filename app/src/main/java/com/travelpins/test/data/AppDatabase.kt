package com.travelpins.test.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Place::class, Category::class, PlacePhoto::class, PlaceReview::class, SourceList::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun placeDao(): PlaceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun placePhotoDao(): PlacePhotoDao
    abstract fun placeReviewDao(): PlaceReviewDao
    abstract fun sourceListDao(): SourceListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 6 -> 7: aggiunge la colonna uuid alla tabella categories.
         *
         * La colonna nasce vuota (''): gli UUID reali vengono generati in Kotlin
         * (UUID.randomUUID()) dal backfill eseguito dentro DriveSyncManager.start(),
         * prima che possa partire qualsiasi sincronizzazione. SQLite non dispone di
         * una funzione random_uuid() portabile, quindi il backfill NON è in SQL.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "travelpins.db"
                )
                    .addMigrations(MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
