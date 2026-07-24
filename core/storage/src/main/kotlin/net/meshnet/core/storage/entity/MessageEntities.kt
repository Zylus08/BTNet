package net.meshnet.core.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId"),
        Index("timestamp")
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String, // Packet ID
    val conversationId: String, // Group ID or Peer ID
    val senderId: String,
    val textContent: String?,
    val attachmentId: String?,
    val timestamp: Long,
    val isRead: Boolean,
    val isFromMe: Boolean,
    val deliveryStatus: Int // 0=Sending, 1=Sent, 2=Delivered, 3=Failed
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String, // Group ID or Peer ID
    val name: String?,
    val isGroup: Boolean,
    val lastMessageText: String?,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)
