# Threat Model

MeshNet is designed to operate in hostile, unmonitored environments (disaster zones, protests, remote expeditions).

## 1. Assumptions
- The physical radio medium (BLE, Wi-Fi) is fundamentally insecure and subject to interception by anyone within range.
- Any node relaying a message is untrusted.
- Android devices may be seized, meaning data must be protected both in transit and at rest.

## 2. In-Scope Protections

### 2.1 Eavesdropping & Interception
Mitigated by strict End-to-End Encryption (X3DH + Double Ratchet) on all user payloads. Intermediate relay nodes cannot read or modify the `payload` of a `MeshPacket`.

### 2.2 Replay Attacks
Mitigated by the Double Ratchet's ephemeral keys and strict packet timestamp validation. Replayed packets will fail MAC validation or be dropped as stale.

### 2.3 Sybil Attacks & Flooding
Mitigated by the `ConfidenceEngine`. Nodes cannot artificially inflate report confidence by generating multiple virtual identities (Sybil), because the engine strongly weights the *Trust History* (which Sybil nodes lack) and *Spatial Distance* of corroborating reports. The routing engine utilizes Bloom Filters to aggressively drop circular routing loops.

## 3. Out-of-Scope Risks

### 3.1 Metadata Traffic Analysis
Relay nodes *can* observe that Node A sent a packet of size X at time Y, even if they cannot read it. In extremely high-risk scenarios, adversaries can trace packet flow geographically. MeshNet prioritizes battery efficiency over Tor-style onion routing, leaving metadata analysis out of scope for Phase 1.

### 3.2 Device Compromise
If a device is compromised while unlocked, the attacker has access to the local SQLCipher database. The OS-level Keystore protects the Identity Key from extraction, but local messages are accessible while the device is decrypted.
