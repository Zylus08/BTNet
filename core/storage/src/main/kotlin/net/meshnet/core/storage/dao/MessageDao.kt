package net.meshnet.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.meshnet.core.storage.entity.ConversationEntity
import net.meshnet.core.storage.entity.MessageEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: Int)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Transaction
    suspend fun insertMessageAndUpdateConversation(message: MessageEntity, unreadIncrement: Int) {
        insertMessage(message)
        updateConversationLatestMessage(
            message.conversationId,
            message.textContent ?: "Attachment",
            message.timestamp,
            unreadIncrement
        )
    }

    @Query("""
        UPDATE conversations 
        SET lastMessageText = :text, 
            lastMessageTimestamp = :timestamp,
            unreadCount = unreadCount + :unreadIncrement
        WHERE id = :conversationId
    """)
    suspend fun updateConversationLatestMessage(
        conversationId: String,
        text: String,
        timestamp: Long,
        unreadIncrement: Int
    )
}
