package net.meshnet.core.maps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the downloading, caching, and serving of offline MBTiles (Protomaps/OpenMapTiles).
 * During a disaster, peers can share .mbtiles files via the mesh file transfer protocol,
 * which this manager then loads as local tile sources.
 */
@Singleton
class OfflineRegionManager @Inject constructor() {

    private val _availableRegions = MutableStateFlow<List<OfflineRegion>>(emptyList())
    val availableRegions: Flow<List<OfflineRegion>> = _availableRegions.asStateFlow()

    init {
        // Scan for existing MBTiles in app-specific storage
        scanLocalStorage()
    }

    private fun scanLocalStorage() {
        // Stub: In reality, we'd read a specific directory and parse MBTile metadata
        val stubRegions = listOf(
            OfflineRegion("san_francisco_bay", "San Francisco Bay Area", 450_000_000L, true),
            OfflineRegion("sierra_nevada", "Sierra Nevada", 210_000_000L, true)
        )
        _availableRegions.update { stubRegions }
    }

    /**
     * Registers a new MBTiles file that was just transferred over the mesh.
     */
    fun registerNewRegion(file: File, name: String) {
        val newRegion = OfflineRegion(
            id = file.nameWithoutExtension,
            displayName = name,
            sizeBytes = file.length(),
            isAvailable = true
        )
        _availableRegions.update { current -> current + newRegion }
    }

    /**
     * Returns the local URI for the MBTiles source to feed into MapLibre.
     */
    fun getTileSourceUri(regionId: String): String? {
        // Example: mbtiles://path/to/file.mbtiles
        return "mbtiles://stub/path/$regionId.mbtiles"
    }
}

data class OfflineRegion(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val isAvailable: Boolean
)
