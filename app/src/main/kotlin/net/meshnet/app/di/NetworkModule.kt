package net.meshnet.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.meshnet.core.mesh.transport.MeshTransport
import net.meshnet.core.mesh.transport.TransportManager
import net.meshnet.core.routing.EpidemicRouting
import net.meshnet.core.routing.GossipRouting
import net.meshnet.core.routing.PRoPHETRouting
import net.meshnet.core.routing.RoutingStrategy
import net.meshnet.core.routing.SprayAndWait
import javax.inject.Singleton

/**
 * Wires MeshTransport implementations and RoutingStrategy plugins.
 *
 * Transports and strategies are provided as [Set] multibindings,
 * allowing [TransportManager] and RoutingManager to discover all
 * registered implementations without knowing their concrete types.
 *
 * To add a new transport: implement [MeshTransport] and add an @IntoSet binding here.
 * To add a new routing strategy: implement [RoutingStrategy] and add @IntoSet binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    // ── Routing strategies (multibinding) ────────────────────────────────────

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindEpidemicRouting(impl: EpidemicRouting): RoutingStrategy

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindPRoPHETRouting(impl: PRoPHETRouting): RoutingStrategy

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSprayAndWait(impl: SprayAndWait): RoutingStrategy

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindGossipRouting(impl: GossipRouting): RoutingStrategy

    companion object {

        // ── TransportManager ──────────────────────────────────────────────────
        // Note: actual MeshTransport implementations (BLETransport, WifiDirectTransport)
        // are added in Phase 2 and 4 respectively. The empty Set allows the graph
        // to compile in Phase 1.

        @Provides
        @Singleton
        fun provideTransportSet(): Set<@JvmSuppressWildcards MeshTransport> = emptySet()

        @Provides
        @Singleton
        fun provideTransportManager(
            transports: Set<@JvmSuppressWildcards MeshTransport>,
        ): TransportManager = TransportManager(transports)
    }
}
