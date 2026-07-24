# API Reference

MeshNet is designed to be extensible. Developers can swap out Transports or Routing Algorithms by implementing standard interfaces.

## Transports

To add a new transport layer (e.g. LoRa or a LAN Bridge), implement `MeshTransport`.

```kotlin
interface MeshTransport {
    suspend fun start()
    suspend fun stop()
    suspend fun advertise()
    suspend fun scan()
    suspend fun send(packet: MeshPacket): Result<Unit>
    
    fun incomingPackets(): Flow<MeshPacket>
    fun connectedPeers(): Flow<List<Peer>>
}
```

Then, inject it into the `TransportManager` inside `MeshNodeService`.

## Routing Engine

To create a custom routing algorithm, implement `RoutingAlgorithm`.

```kotlin
interface RoutingAlgorithm {
    /**
     * Called when a packet is received that is NOT destined for this node.
     * Return true if this node should buffer and relay it.
     */
    fun shouldRelay(packet: MeshPacket, peer: Peer): Boolean
    
    /**
     * Called when discovering a peer.
     * Return the subset of the local buffer that should be forwarded to this peer.
     */
    fun getPacketsToForward(peer: Peer, buffer: List<MeshPacket>): List<MeshPacket>
}
```
