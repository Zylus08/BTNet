# Protocol Specification

This document specifies the wire format and behavior of the MeshNet protocol.

## 1. Packet Layout

All packets are serialized using Protocol Buffers (`proto3`). The maximum payload size over BLE is strictly limited by the MTU (typically 512 bytes on modern Android, though safely fragmented to 256 bytes).

### 1.1 Envelope (`MeshPacket`)
```proto
message MeshPacket {
    bytes id = 1; // 16-byte UUID
    uint64 timestamp = 2;
    bytes sender_id = 3; // 32-byte Ed25519 Public Key
    PacketType type = 4;
    uint32 hop_limit = 5; // Max 10
    bytes payload = 6;
}
```

### 1.2 Packet Types
- `PEER_ANNOUNCE`: Broadcast periodically to declare presence and capabilities.
- `CAPABILITY_PROBE`: Request to upgrade transport (e.g. to Wi-Fi Direct).
- `MESSAGE`: End-to-End Encrypted payload containing Double Ratchet ciphertext.
- `FILE_CHUNK`: 4KB chunk of a compressed file transfer.
- `ACK`: Delivery acknowledgement.

## 2. Capability Negotiation

Nodes advertise their features via an integer bitmask in `PEER_ANNOUNCE`:
- `0x01`: Wi-Fi Direct Group Owner
- `0x02`: LZ4 Compression
- `0x04`: Audio Opus Processing

When a node requires high bandwidth (e.g., File Transfer), it sends a `CAPABILITY_PROBE`. If both peers support Wi-Fi Direct, they initiate a connection via Android's `WifiP2pManager`.

## 3. Reliable File Transfer (ARQ)

Files are chunked into 4KB segments. A Sliding Window protocol (TCP Reno-style AIMD) manages flow control. 
- **Additive Increase**: +1 chunk on successful `ACK`.
- **Multiplicative Decrease**: Half window on timeout.
Selective retransmission (`RetransmissionEngine`) requests specific missing chunks instead of dropping the entire file.
