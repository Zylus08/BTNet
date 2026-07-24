# MeshNet Public Roadmap

This document outlines the high-level goals for MeshNet post-v1.0.

## Q1: Hardening & Auditing
- [ ] Commission External Cryptographic Audit of X3DH/Ratchet implementations.
- [ ] Fix any vulnerabilities discovered.
- [ ] Stabilization of Wi-Fi Direct transition edge cases.

## Q2: Interoperability & Transport Plugins
- [ ] **LoRa Transport Plugin**: Enable extreme-range, low-bandwidth text messaging using external LoRa hardware over USB OTG / BLE serial.
- [ ] **Internet Bridge**: Optional setting allowing nodes with internet to bridge mesh traffic globally via WebSocket relays.

## Q3: Advanced Spatial Features
- [ ] **Live Map Collaboration**: Drawing shared polygons (e.g. marking a flood zone area) synchronized across the mesh.
- [ ] **MapLibre Dynamic Routing**: Directly injecting hazard zones into MapLibre's routing graph instead of just filtering destinations.

*Note: Priorities are subject to change based on community feedback and real-world disaster response needs.*
