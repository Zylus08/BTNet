package net.meshnet.core.storage.report

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReportEntity::class], version = 1, exportSchema = true)
abstract class ReportDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    companion object { const val NAME = "meshnet_reports.db" }
}
