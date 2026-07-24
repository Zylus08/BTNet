package net.meshnet.core.storage.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.protocol.MeshPacket
import net.meshnet.core.protocol.PacketType
import net.meshnet.core.storage.dao.MessageDao
import net.meshnet.core.storage.entity.ConversationEntity
import net.meshnet.core.storage.entity.MessageEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Messages and Conversations.
 * Exposes Room DB flows to the UI, and listens to the EventBus to automatically
 * persist incoming messages from the mesh network.
 */
@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val eventBus: EventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Automatically save incoming messages
        scope.launch {
            eventBus.on<MeshEvent.PacketReceived>().collect { event ->
                if (event.packet.type == PacketType.MESSAGE) {
                    handleIncomingMessage(event.packet, event.from.id.toHex())
                }
            }
        }
        
        // Listen for delivery ACKs to update status
        scope.launch {
            eventBus.on<MeshEvent.DeliveryAcknowledged>().collect { event ->
                messageDao.updateDeliveryStatus(event.packetId.toHex(), STATUS_DELIVERED)
            }
        }
    }

    /** Returns a reactive flow of conversations for the Inbox UI. */
    fun getConversations(): Flow<List<ConversationEntity>> {
        return messageDao.getAllConversations()
    }

    /** Returns a reactive flow of messages for a specific chat UI. */
    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    /** Saves a message sent by the local user. */
    suspend fun saveOutgoingMessage(packetId: ByteArray, conversationId: String, text: String) {
        val entity = MessageEntity(
            id = packetId.toHex(),
            conversationId = conversationId,
            senderId = "me", // Local identity
            textContent = text,
            attachmentId = null,
            timestamp = System.currentTimeMillis(),
            isRead = true,
            isFromMe = true,
            deliveryStatus = STATUS_SENDING
        )
        messageDao.insertMessageAndUpdateConversation(entity, unreadIncrement = 0)
    }

    private suspend fun handleIncomingMessage(packet: MeshPacket, senderId: String) {
        // Assuming packet payload is UTF-8 text for standard messages
        val text = String(packet.payload)
        
        val entity = MessageEntity(
            id = packet.id.toHex(),
            conversationId = senderId, // For 1:1, conversation ID is peer ID
            senderId = senderId,
            textContent = text,
            attachmentId = null, // Parsing attachment metadata would go here
            timestamp = System.currentTimeMillis(),
            isRead = false,
            isFromMe = false,
            deliveryStatus = STATUS_DELIVERED
        )

        // Make sure conversation exists
        messageDao.insertConversation(
            ConversationEntity(
                id = senderId,
                name = null, // Will be resolved by PeerRepository if alias exists
                isGroup = false,
                lastMessageText = text,
                lastMessageTimestamp = entity.timestamp,
                unreadCount = 1
            )
        )

        messageDao.insertMessageAndUpdateConversation(entity, unreadIncrement = 1)
        eventBus.emit(MeshEvent.MessageStored(entity.id, senderId))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val STATUS_SENDING = 0
        const val STATUS_SENT = 1
        const val STATUS_DELIVERED = 2
        const val STATUS_FAILED = 3
    }
}
