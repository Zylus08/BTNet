package net.meshnet.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide event bus for decoupled inter-module communication.
 *
 * Based on [MutableSharedFlow] with a replay buffer of [REPLAY_CACHE] events
 * (useful for late subscribers that need recent state on attach).
 *
 * Ordering: events are delivered in emission order within a single coroutine context.
 * Backpressure: [EXTRA_BUFFER_CAPACITY] slots; slow subscribers cause suspension, not drops.
 *
 * Usage:
 *   Emit: `eventBus.emit(MeshEvent.PacketReceived(...))`
 *   Subscribe all: `eventBus.events.collect { ... }`
 *   Subscribe typed: `eventBus.on<MeshEvent.PacketReceived>().collect { ... }`
 *
 * Module wiring:
 *   BLETransport        → emits PacketReceived, PeerDiscovered, PeerLost
 *   RelayEngine         → consumes PacketReceived, emits PacketForwarded, PacketDropped
 *   DtnManager          → consumes PacketReceived, emits MessageStored, PacketExpired
 *   TrustEngine         → consumes ReportReceived, emits ReportConfidenceUpdated
 *   PacketTracer        → consumes PacketReceived, PacketForwarded, DeliveryAcknowledged
 *   ViewModel (UI)      → consumes MessageStored, ReportConfidenceUpdated, PeerDiscovered
 */
@Singleton
class EventBus @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<MeshEvent>(
        replay = REPLAY_CACHE,
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
    )

    /** Hot flow of all emitted events. Safe to collect from multiple coroutines. */
    val events: Flow<MeshEvent> = _events.asSharedFlow()

    /**
     * Emits [event] to all current subscribers.
     * Safe to call from any coroutine context or thread.
     * Returns immediately (non-suspending) unless the buffer is full.
     */
    fun emit(event: MeshEvent) {
        val emitted = _events.tryEmit(event)
        if (!emitted) {
            // Buffer full — emit via coroutine to suspend until space available
            scope.launch {
                _events.emit(event)
                Timber.w("EventBus backpressure: ${event::class.simpleName} queued via suspend")
            }
        }
    }

    /**
     * Suspending emit — prefer for high-priority events where backpressure must be honoured.
     */
    suspend fun emitSuspend(event: MeshEvent) {
        _events.emit(event)
    }

    /**
     * Returns a [Flow] filtered to events of type [T].
     *
     * Example:
     * ```kotlin
     * eventBus.on<MeshEvent.PeerDiscovered>().collect { event ->
     *     updatePeerList(event.peer)
     * }
     * ```
     */
    inline fun <reified T : MeshEvent> on(): Flow<T> = _events.filterIsInstance<T>()

    companion object {
        /** Number of recent events replayed to new subscribers. */
        const val REPLAY_CACHE = 16
        /** Extra buffer capacity before backpressure kicks in. */
        const val EXTRA_BUFFER_CAPACITY = 512
    }
}
