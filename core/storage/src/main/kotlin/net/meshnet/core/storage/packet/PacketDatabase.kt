package net.meshnet.core.storage.packet

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PacketEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PacketDatabase : RoomDatabase() {
    abstract fun packetDao(): PacketDao

    companion object {
        const val NAME = "meshnet_packets.db"
    }
}
