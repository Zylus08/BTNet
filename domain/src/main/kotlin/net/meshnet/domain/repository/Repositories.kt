package net.meshnet.domain.repository

import kotlinx.coroutines.flow.Flow
import net.meshnet.domain.model.CommunityReport
import net.meshnet.domain.model.Message

/**
 * Repository interfaces — defined in domain, implemented in data.
 * Hilt binds implementations at the data layer (Phase 3+).
 */

interface MessageRepository {
    fun observeConversation(peerId: String): Flow<List<Message>>
    suspend fun send(message: Message): Result<Unit>
    suspend fun markRead(messageId: String)
    suspend fun deleteMessage(messageId: String)
}

interface ReportRepository {
    fun observeActiveReports(nowMs: Long): Flow<List<CommunityReport>>
    fun observeReportsInBounds(
        nowMs: Long,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double,
    ): Flow<List<CommunityReport>>
    suspend fun createReport(report: CommunityReport): Result<Unit>
    suspend fun flagStale(reportId: String)
}

interface PeerRepository {
    fun observeNearbyPeers(): Flow<List<net.meshnet.core.mesh.model.Peer>>
    suspend fun setPeerNickname(peerId: String, nickname: String)
    suspend fun setPeerTrustLevel(peerId: String, level: net.meshnet.core.mesh.model.TrustLevel)
}
