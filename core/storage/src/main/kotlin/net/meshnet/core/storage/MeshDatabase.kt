package net.meshnet.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import net.meshnet.core.storage.dao.MessageDao
import net.meshnet.core.storage.entity.AttachmentEntity
import net.meshnet.core.storage.entity.ConversationEntity
import net.meshnet.core.storage.entity.MessageEntity
import net.meshnet.core.storage.entity.PeerEntity
import net.meshnet.core.storage.entity.ReportEntity
import net.meshnet.core.storage.entity.TrustEntity

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        PeerEntity::class,
        TrustEntity::class,
        ReportEntity::class,
        AttachmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeshDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    
    // abstract fun peerDao(): PeerDao
    // abstract fun reportDao(): ReportDao
    
    companion object {
        const val DATABASE_NAME = "meshnet.db"
    }
}
