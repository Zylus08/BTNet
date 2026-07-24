package net.meshnet.core.crypto.ratchet

/**
 * Persists Double Ratchet session state securely.
 */
interface SessionStore {
    
    /**
     * Loads the active session for the given [peerId].
     */
    suspend fun loadSession(peerId: ByteArray): RatchetState?
    
    /**
     * Saves the [state] for the given [peerId].
     */
    suspend fun saveSession(peerId: ByteArray, state: RatchetState)
    
    /**
     * Permanently deletes the session for [peerId].
     */
    suspend fun deleteSession(peerId: ByteArray)
}
