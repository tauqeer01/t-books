package com.bookflow.app.data.repository

import com.bookflow.app.data.local.dao.CollectionDao
import com.bookflow.app.data.local.entity.CollectionEntity
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.repository.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CollectionRepositoryImpl(
    private val collectionDao: CollectionDao
) : CollectionRepository {

    override fun getAllCollections(): Flow<List<BookCollection>> {
        return collectionDao.getAllCollections().map { list ->
            list.map { entity -> entity.toDomain() }
        }
    }

    override fun getCollectionById(id: String): Flow<BookCollection?> {
        return collectionDao.getCollectionById(id).map { it?.toDomain() }
    }

    override suspend fun insertCollection(collection: BookCollection) = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(CollectionEntity.fromDomain(collection))
    }

    override suspend fun deleteCollection(id: String) = withContext(Dispatchers.IO) {
        collectionDao.removeCollection(id)
    }

    override suspend fun preloadDefaultCollections() = withContext(Dispatchers.IO) {
        // Managed by BookRepositoryImpl preload
    }
}
