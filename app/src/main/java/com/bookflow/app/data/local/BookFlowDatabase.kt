package com.bookflow.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bookflow.app.data.local.dao.AnnotationDao
import com.bookflow.app.data.local.dao.BookDao
import com.bookflow.app.data.local.dao.BookmarkDao
import com.bookflow.app.data.local.dao.CollectionDao
import com.bookflow.app.data.local.entity.AnnotationEntity
import com.bookflow.app.data.local.entity.BookCollectionCrossRef
import com.bookflow.app.data.local.entity.BookEntity
import com.bookflow.app.data.local.entity.BookmarkEntity
import com.bookflow.app.data.local.entity.CollectionEntity
import com.bookflow.app.notes.data.NoteElementEntity
import com.bookflow.app.notes.data.NoteEntity
import com.bookflow.app.notes.data.NoteFolderEntity
import com.bookflow.app.notes.data.NoteFtsEntity
import com.bookflow.app.notes.data.NotePageEntity
import com.bookflow.app.notes.data.NotesDao

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        AnnotationEntity::class,
        BookmarkEntity::class,
        NoteFolderEntity::class,
        NoteEntity::class,
        NotePageEntity::class,
        NoteElementEntity::class,
        NoteFtsEntity::class
    ],
    // Version 3 is the first Play Store release schema (exported to app/schemas).
    // Any future change must bump the version and add a migration (auto or in MIGRATIONS).
    version = 4,
    exportSchema = true,
    // 4: Notes (folders, notes, pages, elements, full-text index) — new tables only.
    autoMigrations = [AutoMigration(from = 3, to = 4)]
)
abstract class BookFlowDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun notesDao(): NotesDao

    companion object {
        @Volatile
        private var INSTANCE: BookFlowDatabase? = null

        /** Schema migrations, oldest first. Empty until the schema changes after the first release. */
        private val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun getInstance(context: Context): BookFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookFlowDatabase::class.java,
                    "bookflow_database.db"
                )
                    .addMigrations(*MIGRATIONS)
                    // Never wipe user libraries and annotations on upgrade; only an app downgrade may reset
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
