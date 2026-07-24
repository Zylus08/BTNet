# Architecture

MeshNet employs a strict Unidirectional Data Flow (UDF) across isolated feature modules and a headless background runtime.

## Module Graph

```mermaid
graph TD
    UI(Jetpack Compose UI) --> VM(ViewModels)
    VM --> Repos(Repository Layer)
    
    Runtime(MeshNodeService) --> TransMgr(TransportManager)
    Runtime --> RouteEng(RoutingEngine)
    Runtime --> CryptEng(CryptoEngine)
    
    TransMgr --> BLE(BLE Transport)
    TransMgr --> WiFiDirect(Wi-Fi Direct Transport)
    
    BLE --> Network((Physical Layer))
    WiFiDirect --> Network
    
    TransMgr --> EB(EventBus)
    EB --> Repos
    Repos --> DB[(Room DB - SQLCipher)]
    DB --> VM
```

## Layers

1. **Transport Layer (`core:mesh`)**: Abstracted interface `MeshTransport` handling raw byte transmission. Pluggable design (BLE, Wi-Fi Direct).
2. **Protocol & Routing (`core:protocol`, `core:routing`)**: Handles PRoPHET routing, capability negotiation, and packet serialization (Protobuf).
3. **Crypto Engine (`core:crypto`)**: Exposes Tink primitives and manages Double Ratchet sessions.
4. **Mesh Runtime (`core:mesh:MeshNodeService`)**: A Foreground Service keeping the node alive in Doze mode. Independent of the UI.
5. **Storage (`core:storage`)**: Encrypted-at-rest SQLite database (SQLCipher) mapping entities to domain models.
6. **UI (`feature:*`)**: Stateless Jetpack Compose UI reacting strictly to `StateFlow` updates from the Repositories.
