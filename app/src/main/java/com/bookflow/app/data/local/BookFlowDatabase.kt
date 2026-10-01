package com.bookflow.app.data.local

import android.content.Context
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

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        AnnotationEntity::class,
        BookmarkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BookFlowDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: BookFlowDatabase? = null

        fun getInstance(context: Context): BookFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookFlowDatabase::class.java,
                    "bookflow_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
