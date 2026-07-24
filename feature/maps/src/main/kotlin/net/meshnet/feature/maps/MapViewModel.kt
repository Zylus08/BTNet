package net.meshnet.feature.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.meshnet.core.maps.OfflineRegion
import net.meshnet.core.maps.OfflineRegionManager
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val regionManager: OfflineRegionManager
) : ViewModel() {

    private val _mapState = MutableStateFlow(MapState())
    val mapState = _mapState.asStateFlow()

    init {
        viewModelScope.launch {
            regionManager.availableRegions.collect { regions ->
                _mapState.update { it.copy(availableRegions = regions) }
            }
        }
    }

    fun toggleLayer(layer: MapLayer) {
        _mapState.update { current ->
            val updatedLayers = current.activeLayers.toMutableSet()
            if (updatedLayers.contains(layer)) {
                updatedLayers.remove(layer)
            } else {
                updatedLayers.add(layer)
            }
            current.copy(activeLayers = updatedLayers)
        }
    }
}

data class MapState(
    val activeLayers: Set<MapLayer> = setOf(MapLayer.REPORTS, MapLayer.PEERS), // Default active
    val availableRegions: List<OfflineRegion> = emptyList()
)

enum class MapLayer {
    REPORTS,
    PEERS,
    OFFLINE_REGIONS,
    TRANSFER_RELAYS,
    DIAGNOSTICS
}
