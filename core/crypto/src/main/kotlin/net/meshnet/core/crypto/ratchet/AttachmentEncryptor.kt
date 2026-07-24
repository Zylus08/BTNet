package net.meshnet.core.crypto.ratchet

import net.meshnet.core.crypto.CryptoProvider
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts and decrypts file attachments securely.
 * 
 * 1. A random 32-byte File Key is generated.
 * 2. This key is sent to the recipient via a 1:1 Double Ratchet message or Group Sender Key.
 * 3. The file is split into chunks.
 * 4. Each chunk is encrypted using AES-256-GCM. 
 *    To prevent chunk reordering attacks, the AAD includes the File ID and Chunk Index.
 * 5. Forward Secrecy: The File Key is ratcheted (using HMAC) after every chunk, so 
 *    a compromised chunk key doesn't compromise the whole file. (Alternatively, the 
 *    File Key is just a base key, and chunk keys are derived via HKDF(FileKey, ChunkIndex)).
 *    We use the HKDF approach here for parallel processing (non-sequential chunk delivery in DTN).
 */
@Singleton
class AttachmentEncryptor @Inject constructor(
    private val cryptoProvider: CryptoProvider,
) {
    /**
     * Generates a new random base key for a file transfer.
     */
    fun generateFileBaseKey(): ByteArray {
        return cryptoProvider.secureRandomBytes(32)
    }

    /**
     * Encrypts a single chunk of a file.
     * Since chunks may arrive out of order over the mesh, we derive a unique key
     * for each chunk based on its index.
     * 
     * @param plaintext The raw file chunk data (e.g., 4KB)
     * @param fileBaseKey The 32-byte base key for this file transfer
     * @param fileId A unique ID for the file (used as salt/info)
     * @param chunkIndex The 0-based index of this chunk
     */
    fun encryptChunk(
        plaintext: ByteArray,
        fileBaseKey: ByteArray,
        fileId: String,
        chunkIndex: Int
    ): EncryptedChunk {
        val chunkKey = deriveChunkKey(fileBaseKey, fileId, chunkIndex)
        
        // AAD = fileId + chunkIndex to bind the ciphertext to its exact position in this exact file
        val aad = fileId.toByteArray() + chunkIndex.toString().toByteArray()
        
        val result = cryptoProvider.encrypt(plaintext, chunkKey, aad)
        
        // Wipe the derived chunk key from memory immediately
        wipe(chunkKey)
        
        return EncryptedChunk(
            ciphertext = result.ciphertext,
            nonce = result.nonce
        )
    }

    /**
     * Decrypts a single chunk of a file.
     */
    fun decryptChunk(
        encryptedChunk: EncryptedChunk,
        fileBaseKey: ByteArray,
        fileId: String,
        chunkIndex: Int
    ): ByteArray {
        val chunkKey = deriveChunkKey(fileBaseKey, fileId, chunkIndex)
        val aad = fileId.toByteArray() + chunkIndex.toString().toByteArray()
        
        return try {
            cryptoProvider.decrypt(encryptedChunk.ciphertext, chunkKey, aad)
        } finally {
            wipe(chunkKey)
        }
    }

    private fun deriveChunkKey(baseKey: ByteArray, fileId: String, chunkIndex: Int): ByteArray {
        // HKDF allows deriving independent keys without sequential dependence.
        // This is crucial because MeshNet DTN might deliver chunk 5 before chunk 1.
        val info = "MeshNetAttachmentChunk_$chunkIndex".toByteArray()
        val salt = fileId.toByteArray() // Use File ID as salt
        return cryptoProvider.hkdf(
            inputKeyMaterial = baseKey,
            salt = salt,
            info = info,
            outputLength = 32
        )
    }

    private fun wipe(key: ByteArray) {
        for (i in key.indices) {
            key[i] = 0
        }
    }
}

data class EncryptedChunk(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
)
