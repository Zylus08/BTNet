package net.meshnet.core.storage.trust

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CorroborationEntity::class, ReporterHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrustDatabase : RoomDatabase() {
    abstract fun trustDao(): TrustDao
    companion object { const val NAME = "meshnet_trust.db" }
}
