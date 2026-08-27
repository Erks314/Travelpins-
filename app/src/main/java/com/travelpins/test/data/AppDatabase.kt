package com.travelpins.test.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Place::class, Category::class, PlacePhoto::class, PlaceReview::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun placeDao(): PlaceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun placePhotoDao(): PlacePhotoDao
    abstract fun placeReviewDao(): PlaceReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "travelpins.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }

        // Migration dalla versione 3 alla 4:
        // - Aggiunge colonne nullable a places
        // - Crea tabella place_photos
        // - Crea tabella place_reviews
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Aggiungi colonne nullable a places
                database.execSQL("ALTER TABLE places ADD COLUMN rating REAL")
                database.execSQL("ALTER TABLE places ADD COLUMN reviewCount INTEGER")
                database.execSQL("ALTER TABLE places ADD COLUMN description TEXT")
                database.execSQL("ALTER TABLE places ADD COLUMN websiteUrl TEXT")
                database.execSQL("ALTER TABLE places ADD COLUMN types TEXT")
                database.execSQL("ALTER TABLE places ADD COLUMN detailsFetchedAt INTEGER")

                // Crea tabella place_photos
                database.execSQL("""
                    CREATE TABLE place_photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        placeId INTEGER NOT NULL,
                        photoKey TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        position INTEGER NOT NULL,
                        FOREIGN KEY(placeId) REFERENCES places(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_place_photos_placeId ON place_photos(placeId)")
                database.execSQL("CREATE UNIQUE INDEX index_place_photos_placeId_photoKey ON place_photos(placeId, photoKey)")

                // Crea tabella place_reviews
                database.execSQL("""
                    CREATE TABLE place_reviews (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        placeId INTEGER NOT NULL,
                        authorName TEXT,
                        authorPhotoUrl TEXT,
                        rating INTEGER,
                        timeText TEXT,
                        reviewText TEXT,
                        position INTEGER NOT NULL,
                        FOREIGN KEY(placeId) REFERENCES places(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_place_reviews_placeId ON place_reviews(placeId)")
            }
        }
    }
}
