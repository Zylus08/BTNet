package net.meshnet.core.storage.peer

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PeerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PeerDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao

    companion object {
        const val NAME = "meshnet_peers.db"
    }
}
