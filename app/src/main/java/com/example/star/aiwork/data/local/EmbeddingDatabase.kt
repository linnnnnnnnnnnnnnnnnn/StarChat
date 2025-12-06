package com.example.star.aiwork.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.star.aiwork.data.local.converter.Converters
import com.example.star.aiwork.data.local.dao.EmbeddingDao
import com.example.star.aiwork.data.local.dao.GlobalEmbeddingDao
import com.example.star.aiwork.data.local.dao.SessionEmbeddingDao
import com.example.star.aiwork.domain.model.embedding.EmbeddingEntity
import com.example.star.aiwork.domain.model.embedding.GlobalEmbeddingEntity
import com.example.star.aiwork.domain.model.embedding.SessionEmbeddingEntity

@Database(
    entities = [EmbeddingEntity::class, SessionEmbeddingEntity::class, GlobalEmbeddingEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EmbeddingDatabase : RoomDatabase() {
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun sessionEmbeddingDao(): SessionEmbeddingDao
    abstract fun globalEmbeddingDao(): GlobalEmbeddingDao
}

