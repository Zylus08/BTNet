# MeshNet

**Decentralized offline mesh communication for Android.**

MeshNet enables secure, private, peer-to-peer communication and community situational awareness in environments where internet and cellular infrastructure is unavailable or unreliable — natural disasters, remote expeditions, search & rescue, large public gatherings, and humanitarian operations.

No servers. No accounts. No phone numbers. Works when nothing else does.

---

## Features

| Feature | Status |
|---------|--------|
| BLE Mesh Messaging (multi-hop) | Phase 2 |
| Wi-Fi Direct (high-bandwidth) | Phase 4 |
| End-to-End Encryption (X3DH + Double Ratchet) | Phase 3 |
| Store-and-Forward (DTN / epidemic routing) | Phase 2 |
| Offline Maps (OpenStreetMap / MapLibre) | Phase 6 |
| Community Safety Reports | Phase 6 |
| TrustEngine (confidence scoring) | Phase 6 |
| Chunked File Transfer | Phase 4 |
| Opus Voice Notes | Phase 4 |
| Rotating Identifiers (privacy) | Phase 7 |
| QR Friend Exchange | Phase 7 |
| PRoPHET Routing | Phase 5 |
| CRDT Synchronization | Phase 5 |
| Battery Optimization | Phase 9 |

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                   :app                          │
├────────────┬────────────┬───────────────────────┤
│ feature:   │ feature:   │ feature:  feature:    │
│ chat       │ maps       │ reports   settings    │
├────────────┴────────────┴───────────────────────┤
│                  :domain                        │
│         (use cases, repository interfaces)      │
├─────────────────────────────────────────────────┤
│                   :data                         │
│         (repository implementations)            │
├──────────┬──────────┬──────────┬────────────────┤
│core:mesh │core:rout-│core:stor-│ core:crypto    │
│          │ ing      │ age      │                │
├──────────┴──────────┴──────────┴────────────────┤
│ core:protocol │ core:trust │ core:security       │
└─────────────────────────────────────────────────┘
```

Clean Architecture — domain layer has zero Android dependencies.
Multi-module — layer boundaries enforced at compile time.

### Transport Abstraction

```kotlin
interface MeshTransport {
    suspend fun start()
    suspend fun stop()
    suspend fun advertise()
    suspend fun scan()
    suspend fun send(packet: MeshPacket, peer: Peer): Result<Unit>
    fun incomingPackets(): Flow<IncomingPacket>
    fun connectedPeers(): Flow<List<Peer>>
    fun events(): Flow<TransportEvent>
}
```

Implementations: `BLETransport` · `WifiDirectTransport` · `LANTransport` (future) · `LoRaTransport` (future)

### Routing Strategies (plugin architecture)

```kotlin
interface RoutingStrategy {
    fun nextHops(packet: MeshPacket, peers: List<Peer>, localId: ByteArray): List<Peer>
    fun onPeerDiscovered(peer: Peer)
    fun onPacketReceived(packet: MeshPacket, from: Peer)
    fun onDeliveryAck(packetId: ByteArray, via: Peer)
    fun deliveryProbability(destination: ByteArray): Float
}
```

Strategies: `EpidemicRouting` · `PRoPHETRouting` · `SprayAndWait` · `GossipRouting`

---

## Security Model

| Primitive | Algorithm |
|-----------|-----------|
| Identity keypair | Ed25519 (Android Keystore) |
| Key exchange | X25519 ECDH |
| Key derivation | HKDF-SHA256 |
| Symmetric encryption | AES-256-GCM |
| Forward secrecy | Double Ratchet (Phase 3) |
| Library | Google Tink |

**Privacy guarantees:** No phone numbers, IMEI, Android ID, or real MAC address ever transmitted. BLE MAC randomization enabled. Identity IDs rotate every 15 minutes.

**Replay protection:** Sliding 10-minute exact-match cache + Bloom filter.
**Rate limiting:** Token bucket (20 burst, 2/s refill) per sender.
**Packet validation:** 10-check fail-fast pipeline; unsigned packets rejected.

---

## Packet Format

```
MeshPacket {
  protocol_version, packet_version, capabilities   // versioning + negotiation
  packet_id (16B UUID), sender_id (32B), recipient_id (32B)
  ttl, timestamp_ms, hop_count
  type, payload (LZ4 → AES-256-GCM), nonce (12B)
  signature (Ed25519 over all above fields)
}
```

Payload pipeline: `plaintext → LZ4 compress → AES-256-GCM encrypt → Ed25519 sign`

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** Clean Architecture + MVVM + Repository
- **DI:** Hilt
- **Database:** Room (6 separate DBs)
- **Deduplication:** Guava Bloom Filter (1M capacity, <0.1% FPR)
- **Crypto:** Google Tink
- **Compression:** LZ4
- **Serialization:** Protocol Buffers (lite)
- **Async:** Coroutines + Flow
- **Maps:** MapLibre + OSM tiles
- **CI:** GitHub Actions

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android device or emulator with API 26+
- Bluetooth LE hardware

### Build

```bash
git clone https://github.com/your-org/meshnet.git
cd meshnet
./gradlew assembleDebug
```

### Run tests

```bash
./gradlew testDebugUnitTest          # unit tests (all modules)
./gradlew connectedDebugAndroidTest  # instrumented tests (device required)
```

### Static analysis

```bash
./gradlew ktlintCheck
./gradlew detekt
```

---

## Project Structure

```
meshnet/
├── app/                    # Application entry point, Hilt modules
├── core/
│   ├── crypto/             # X25519, AES-GCM, Ed25519, HKDF, LZ4
│   ├── mesh/               # MeshTransport interface + TransportManager
│   ├── routing/            # RoutingStrategy plugins
│   ├── storage/            # 6 Room databases + Bloom filter
│   ├── protocol/           # Protobuf definitions
│   ├── trust/              # TrustEngine, confidence scoring
│   ├── security/           # RateLimiter, PacketValidator, ReplayDetector
│   ├── maps/               # MapLibre, MBTiles (Phase 6)
│   └── sync/               # CRDT, vector clocks (Phase 5)
├── feature/
│   ├── chat/               # 1:1 and group messaging UI
│   ├── maps/               # Map UI + report overlay
│   ├── reports/            # Report creation and viewing
│   ├── settings/           # App settings
│   └── onboarding/         # First-run, identity setup
├── domain/                 # Use cases, repository interfaces, domain models
├── data/                   # Repository implementations
└── benchmark/              # Macrobenchmark module
```

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All contributions are welcome.

**Security disclosures:** Please do not file public issues for security vulnerabilities. Contact the maintainers directly.

---

## License

Apache-2.0 — see [LICENSE](LICENSE).

> **Why Apache-2.0?** The patent grant clause protects users from contributor patent claims. Compatible with most open-source licenses. Preferred by the Android ecosystem (AOSP).
