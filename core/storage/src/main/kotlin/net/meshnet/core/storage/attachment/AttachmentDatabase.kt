package net.meshnet.core.storage.attachment

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AttachmentEntity::class], version = 1, exportSchema = true)
abstract class AttachmentDatabase : RoomDatabase() {
    abstract fun attachmentDao(): AttachmentDao
    companion object { const val NAME = "meshnet_attachments.db" }
}
