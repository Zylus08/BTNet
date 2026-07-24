package net.meshnet.core.crypto.ratchet

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.meshnet.core.crypto.CryptoProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages group sessions, including membership tracking and Sender Key distribution.
 */
@Singleton
class GroupSessionManager @Inject constructor(
    private val senderKeyManager: SenderKeyManager,
    private val cryptoProvider: CryptoProvider,
) {
    // Group ID -> List of Member IDs
    private val groupMembers = ConcurrentHashMap<String, MutableSet<String>>()
    
    // Group ID -> Our Sender Key Record
    private val ourSenderKeys = ConcurrentHashMap<String, SenderKeyRecord>()
    
    // Group ID -> (Sender ID -> Their Sender Key State)
    private val theirSenderKeys = ConcurrentHashMap<String, ConcurrentHashMap<String, SenderKeyState>>()

    private val mutex = Mutex()

    /**
     * Initializes a group session for us.
     */
    suspend fun initializeGroup(groupId: String, members: List<String>) = mutex.withLock {
        groupMembers[groupId] = members.toMutableSet()
        ourSenderKeys[groupId] = senderKeyManager.generateSenderKey()
        theirSenderKeys[groupId] = ConcurrentHashMap()
    }

    /**
     * Returns our current Sender Key Record for the group, which must be 
     * distributed to all members securely (via 1:1 Double Ratchet).
     */
    suspend fun getOurSenderKeyToDistribute(groupId: String): SenderKeyRecord? {
        return ourSenderKeys[groupId]
    }

    /**
     * Stores a Sender Key received from another member of the group.
     */
    suspend fun storeTheirSenderKey(groupId: String, senderId: String, record: SenderKeyRecord) = mutex.withLock {
        val group = theirSenderKeys.getOrPut(groupId) { ConcurrentHashMap() }
        group[senderId] = SenderKeyState(senderId.toByteArray(), record) // Assuming ASCII String for ID in stub
    }

    /**
     * Removes a member from the group.
     * This requires rotating OUR Sender Key and distributing the new one to the remaining members.
     */
    suspend fun removeMember(groupId: String, memberId: String): SenderKeyRecord? = mutex.withLock {
        groupMembers[groupId]?.remove(memberId)
        theirSenderKeys[groupId]?.remove(memberId)
        
        // Rotate our key
        val newKey = senderKeyManager.generateSenderKey()
        ourSenderKeys[groupId] = newKey
        return newKey
    }

    /**
     * Encrypts a message for the group using our Sender Key.
     */
    suspend fun encryptMessage(groupId: String, plaintext: ByteArray): GroupCiphertext? = mutex.withLock {
        val record = ourSenderKeys[groupId] ?: return null
        val ciphertext = senderKeyManager.encryptGroupMessage(plaintext, record)
        
        // Update our state with the ratcheted key
        ourSenderKeys[groupId] = ciphertext.nextSenderKeyRecord
        
        return ciphertext
    }

    /**
     * Decrypts a message received from a group member.
     */
    suspend fun decryptMessage(groupId: String, senderId: String, ciphertext: GroupCiphertext): ByteArray? = mutex.withLock {
        val groupKeys = theirSenderKeys[groupId] ?: return null
        val senderState = groupKeys[senderId] ?: return null
        
        return senderKeyManager.decryptGroupMessage(ciphertext, senderState)
    }
}
