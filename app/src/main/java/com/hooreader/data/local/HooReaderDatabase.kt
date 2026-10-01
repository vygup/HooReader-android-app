package com.hooreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ReadingPositionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(BookConverters::class)
abstract class HooReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingPositionDao(): ReadingPositionDao

    companion object {
        @Volatile
        private var instance: HooReaderDatabase? = null

        fun getInstance(context: Context): HooReaderDatabase = instance ?: synchronized(this) {
            instance ?: builder(context).build().also { instance = it }
        }

        fun builder(context: Context): Builder<HooReaderDatabase> = Room.databaseBuilder(
            context.applicationContext,
            HooReaderDatabase::class.java,
            "hooreader.db",
        ).addCallback(ValidationCallback)
    }

    object ValidationCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            addValidation(db, "chapters", "NEW.`index` < 0 OR NEW.blockCount < 0")
            addValidation(
                db,
                "reading_positions",
                """NEW.chapterIndex < 0 OR NEW.blockIndex < 0 OR NEW.characterOffset < 0
                    OR NEW.progressPercent < 0 OR NEW.progressPercent > 100 OR NEW.updatedAt < 0""",
            )
        }

        private fun addValidation(db: SupportSQLiteDatabase, table: String, invalid: String) {
            for (operation in listOf("INSERT", "UPDATE")) {
                db.execSQL(
                    """CREATE TRIGGER validate_${table}_$operation BEFORE $operation ON $table
                        WHEN $invalid BEGIN SELECT RAISE(ABORT, 'Invalid logical coordinates'); END""",
                )
            }
        }
    }
}
