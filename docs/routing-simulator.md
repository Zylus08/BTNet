# Routing & Simulator

MeshNet utilizes a Delay-Tolerant Networking (DTN) architecture.

## 1. Routing Algorithms

### 1.1 PRoPHET (Probabilistic Routing)
MeshNet defaults to an implementation of the PRoPHET algorithm (Probabilistic Routing Protocol using History of Encounters and Transitivity). 
- **Intuition**: If Node A frequently sees Node B, Node A is a good candidate to carry a message destined for Node B.
- **Strengths**: Excels in environments with predictable mobility (e.g., commuters, basecamps).
- **Complexity**: Low CPU overhead, moderate memory overhead (Delivery Predictability table).

### 1.2 Epidemic Routing (Fallback)
When PRoPHET matrices have not converged (e.g. initial disaster scenario with chaos), MeshNet falls back to constrained Epidemic routing (gossip protocol) bounded by TTLs and Hop Limits.

## 2. Simulator

The `:simulator` module allows deterministic benchmarking of these algorithms.
- **Mobility Models**: Random Waypoint simulates wandering; Manhattan simulates urban grids.
- **Outputs**: Generates CSV data for Delivery Ratio, Latency, and Energy Cost.

### Benchmark Example
*Based on 250 nodes, 1km^2 area, 1 hour run.*
- **PRoPHET**: 92% Delivery Ratio, 450ms Latency.
- **Epidemic**: 98% Delivery Ratio, 120ms Latency (But 4x Battery drain).
