package net.meshnet.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.meshnet.core.storage.attachment.AttachmentDatabase
import net.meshnet.core.storage.attachment.AttachmentDao
import net.meshnet.core.storage.bloom.SeenPacketBloomFilter
import net.meshnet.core.storage.packet.PacketDatabase
import net.meshnet.core.storage.packet.PacketDao
import net.meshnet.core.storage.peer.PeerDatabase
import net.meshnet.core.storage.peer.PeerDao
import net.meshnet.core.storage.report.ReportDatabase
import net.meshnet.core.storage.report.ReportDao
import net.meshnet.core.storage.routing.RoutingCacheDatabase
import net.meshnet.core.storage.routing.RoutingCacheDao
import net.meshnet.core.storage.trust.TrustDatabase
import net.meshnet.core.storage.trust.TrustDao
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    // ── Peer DB ───────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePeerDatabase(@ApplicationContext context: Context): PeerDatabase =
        Room.databaseBuilder(context, PeerDatabase::class.java, PeerDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun providePeerDao(db: PeerDatabase): PeerDao = db.peerDao()

    // ── Packet DB ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePacketDatabase(@ApplicationContext context: Context): PacketDatabase =
        Room.databaseBuilder(context, PacketDatabase::class.java, PacketDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun providePacketDao(db: PacketDatabase): PacketDao = db.packetDao()

    // ── Report DB ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideReportDatabase(@ApplicationContext context: Context): ReportDatabase =
        Room.databaseBuilder(context, ReportDatabase::class.java, ReportDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideReportDao(db: ReportDatabase): ReportDao = db.reportDao()

    // ── Trust DB ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideTrustDatabase(@ApplicationContext context: Context): TrustDatabase =
        Room.databaseBuilder(context, TrustDatabase::class.java, TrustDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideTrustDao(db: TrustDatabase): TrustDao = db.trustDao()

    // ── Attachment DB ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAttachmentDatabase(@ApplicationContext context: Context): AttachmentDatabase =
        Room.databaseBuilder(context, AttachmentDatabase::class.java, AttachmentDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideAttachmentDao(db: AttachmentDatabase): AttachmentDao = db.attachmentDao()

    // ── RoutingCache DB ───────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideRoutingCacheDatabase(@ApplicationContext context: Context): RoutingCacheDatabase =
        Room.databaseBuilder(context, RoutingCacheDatabase::class.java, RoutingCacheDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideRoutingCacheDao(db: RoutingCacheDatabase): RoutingCacheDao = db.routingCacheDao()

    // ── Bloom Filter ──────────────────────────────────────────────────────────

    @Provides
    @Named("bloom_filter_file")
    fun provideBloomFilterFile(@ApplicationContext context: Context): File =
        File(context.filesDir, "seen_packets.bloom")

    @Provides
    @Singleton
    fun provideSeenPacketBloomFilter(
        @Named("bloom_filter_file") file: File,
    ): SeenPacketBloomFilter = SeenPacketBloomFilter(file)
}
