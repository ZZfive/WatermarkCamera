package com.watermarkcamera.ui.placepicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.poisearch.PoiSearch
import com.watermarkcamera.location.LocationFetchResult
import com.watermarkcamera.location.LocationManager
import com.watermarkcamera.ui.camera.ManualPlaceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class PlacePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val locationManager = LocationManager(application)
    private var lastResolvedCenter: Pair<Double, Double>? = null
    private val _uiState = MutableStateFlow(PlacePickerUiState())
    val uiState: StateFlow<PlacePickerUiState> = _uiState.asStateFlow()

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun initialize(initialPlace: ManualPlaceData?) {
        if (_uiState.value.hasCenteredOnInitialLocation) return
        if (initialPlace != null) {
            val seededPlace = initialPlace.toPlaceSearchItem(fromMapInteraction = true)
            _uiState.update {
                it.copy(
                    searchResults = listOf(seededPlace),
                    selectedPlace = seededPlace,
                    currentCenterLatitude = seededPlace.latitude,
                    currentCenterLongitude = seededPlace.longitude,
                    mapCameraLatitude = seededPlace.latitude,
                    mapCameraLongitude = seededPlace.longitude,
                    hasCenteredOnInitialLocation = true,
                    hasPendingCameraMove = true,
                    selectedPlaceVersion = it.selectedPlaceVersion + 1,
                    isLoadingCurrentLocation = false,
                    lastSearchSourceIsMap = true,
                    errorMessage = null
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoadingCurrentLocation = true,
                    errorMessage = null
                )
            }
            val initial = when (val result = locationManager.getLocationResult()) {
                is LocationFetchResult.Success -> result.location.toPlaceSearchItem()
                is LocationFetchResult.Partial -> result.location.toPlaceSearchItem()
                is LocationFetchResult.Failure -> null
            }
            _uiState.update {
                it.copy(
                    searchResults = initial?.let(::listOf).orEmpty(),
                    selectedPlace = initial,
                    currentCenterLatitude = initial?.latitude,
                    currentCenterLongitude = initial?.longitude,
                    mapCameraLatitude = initial?.latitude,
                    mapCameraLongitude = initial?.longitude,
                    hasCenteredOnInitialLocation = true,
                    hasPendingCameraMove = initial != null,
                    selectedPlaceVersion = if (initial != null) it.selectedPlaceVersion + 1 else it.selectedPlaceVersion,
                    isLoadingCurrentLocation = false,
                    lastSearchSourceIsMap = initial != null,
                    errorMessage = if (initial == null) "未获取到当前位置，请拖动地图或搜索地点" else null
                )
            }
        }
    }

    fun markInitialMapMoveCompleted() {
        if (_uiState.value.hasCompletedInitialMapMove) return
        _uiState.update { it.copy(hasCompletedInitialMapMove = true, hasPendingCameraMove = false) }
    }

    fun markPendingCameraMoveConsumed() {
        if (!_uiState.value.hasPendingCameraMove) return
        _uiState.update { it.copy(hasPendingCameraMove = false) }
    }

    fun selectPlace(place: PlaceSearchItem) {
        _uiState.update {
            it.copy(
                selectedPlace = place,
                currentCenterLatitude = place.latitude,
                currentCenterLongitude = place.longitude,
                mapCameraLatitude = place.latitude,
                mapCameraLongitude = place.longitude,
                hasPendingCameraMove = true,
                selectedPlaceVersion = it.selectedPlaceVersion + 1,
                errorMessage = null
            )
        }
    }

    fun onMapCenterChanged(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                currentCenterLatitude = latitude,
                currentCenterLongitude = longitude
            )
        }
    }

    fun resolveMapCenterSelection(latitude: Double, longitude: Double) {
        val roundedCenter = latitude.toCenterKey() to longitude.toCenterKey()
        if (lastResolvedCenter == roundedCenter) return
        lastResolvedCenter = roundedCenter
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    errorMessage = null,
                    currentCenterLatitude = latitude,
                    currentCenterLongitude = longitude
                )
            }
            val candidates = searchNearbyPlaces(latitude, longitude)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = candidates,
                    selectedPlace = candidates.firstOrNull(),
                    lastSearchSourceIsMap = true,
                    selectedPlaceVersion = if (candidates.isNotEmpty()) it.selectedPlaceVersion + 1 else it.selectedPlaceVersion,
                    errorMessage = if (candidates.isEmpty()) "未找到附近地点，请继续移动地图或搜索" else null
                )
            }
        }
    }

    fun searchPlaces() {
        val keyword = _uiState.value.query.trim()
        if (keyword.isEmpty()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    selectedPlace = null,
                    errorMessage = "请输入地点名称"
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    errorMessage = null,
                    selectedPlace = null
                )
            }

            val result = searchWithInputTips(keyword).ifEmpty {
                searchWithPoi(keyword)
            }
            val first = result.firstOrNull()

            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = result,
                    selectedPlace = first,
                    lastSearchSourceIsMap = false,
                    currentCenterLatitude = first?.latitude ?: it.currentCenterLatitude,
                    currentCenterLongitude = first?.longitude ?: it.currentCenterLongitude,
                    mapCameraLatitude = first?.latitude ?: it.mapCameraLatitude,
                    mapCameraLongitude = first?.longitude ?: it.mapCameraLongitude,
                    hasPendingCameraMove = first != null,
                    selectedPlaceVersion = if (first != null) it.selectedPlaceVersion + 1 else it.selectedPlaceVersion,
                    errorMessage = if (result.isEmpty()) "未找到相关地点" else null
                )
            }
        }
    }

    fun buildManualPlaceData(): ManualPlaceData? {
        val place = _uiState.value.selectedPlace ?: return null
        return ManualPlaceData(
            title = place.title,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude
        )
    }

    private suspend fun searchNearbyPlaces(latitude: Double, longitude: Double): List<PlaceSearchItem> {
        val nearby = searchNearbyPoi(latitude, longitude)
        if (nearby.isNotEmpty()) {
            return nearby
        }
        return listOf(reverseGeocodeFallback(latitude, longitude))
    }

    private suspend fun searchNearbyPoi(latitude: Double, longitude: Double): List<PlaceSearchItem> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val query = PoiSearch.Query("", "", "")
                query.pageSize = 20
                query.pageNum = 0
                val poiSearch = PoiSearch(getApplication(), query)
                poiSearch.setBound(PoiSearch.SearchBound(LatLonPoint(latitude, longitude), 500, true))
                poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: com.amap.api.services.poisearch.PoiResult?, code: Int) {
                        if (continuation.isCompleted) return
                        if (code == AMapException.CODE_AMAP_SUCCESS) {
                            val items = result?.pois.orEmpty().mapNotNull { poi ->
                                val point = poi.latLonPoint ?: return@mapNotNull null
                                val title = poi.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                PlaceSearchItem(
                                    title = title,
                                    address = poi.snippet?.takeIf { it.isNotBlank() } ?: title,
                                    latitude = point.latitude,
                                    longitude = point.longitude,
                                    poiId = poi.poiId,
                                    fromMapInteraction = true
                                )
                            }
                            continuation.resume(items)
                        } else {
                            continuation.resume(emptyList())
                        }
                    }

                    override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItem?, code: Int) = Unit
                })
                poiSearch.searchPOIAsyn()
            } catch (_: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    private suspend fun reverseGeocodeFallback(latitude: Double, longitude: Double): PlaceSearchItem {
        return suspendCancellableCoroutine { continuation ->
            try {
                val geocodeSearch = GeocodeSearch(getApplication())
                geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onRegeocodeSearched(result: com.amap.api.services.geocoder.RegeocodeResult?, code: Int) {
                        if (continuation.isCompleted) return
                        val formattedAddress = if (code == AMapException.CODE_AMAP_SUCCESS) {
                            result?.regeocodeAddress?.formatAddress?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                        val address = formattedAddress ?: "已选地图位置"
                        continuation.resume(
                            PlaceSearchItem(
                                title = "地图选点",
                                address = address,
                                latitude = latitude,
                                longitude = longitude,
                                fromMapInteraction = true
                            )
                        )
                    }

                    override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, code: Int) = Unit
                })
                geocodeSearch.getFromLocationAsyn(
                    RegeocodeQuery(LatLonPoint(latitude, longitude), 200f, GeocodeSearch.AMAP)
                )
            } catch (_: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resume(
                        PlaceSearchItem(
                            title = "地图选点",
                            address = "已选地图位置",
                            latitude = latitude,
                            longitude = longitude,
                            fromMapInteraction = true
                        )
                    )
                }
            }
        }
    }

    private suspend fun searchWithPoi(keyword: String): List<PlaceSearchItem> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val query = PoiSearch.Query(keyword, "", "")
                query.pageSize = 20
                query.pageNum = 0
                val poiSearch = PoiSearch(getApplication(), query)
                poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: com.amap.api.services.poisearch.PoiResult?, code: Int) {
                        if (continuation.isCompleted) return
                        if (code == AMapException.CODE_AMAP_SUCCESS) {
                            continuation.resume(
                                result?.pois.orEmpty().mapNotNull { poi ->
                                    val point = poi.latLonPoint ?: return@mapNotNull null
                                    val title = poi.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                    PlaceSearchItem(
                                        title = title,
                                        address = poi.snippet?.takeIf { it.isNotBlank() } ?: title,
                                        latitude = point.latitude,
                                        longitude = point.longitude,
                                        poiId = poi.poiId
                                    )
                                }
                            )
                        } else {
                            continuation.resume(emptyList())
                        }
                    }

                    override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItem?, code: Int) = Unit
                })
                poiSearch.searchPOIAsyn()
            } catch (_: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    private suspend fun searchWithInputTips(keyword: String): List<PlaceSearchItem> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val query = InputtipsQuery(keyword, "")
                query.setCityLimit(false)
                val inputTips = Inputtips(getApplication(), query)
                inputTips.setInputtipsListener { tips: MutableList<Tip>?, code: Int ->
                    if (continuation.isCompleted) return@setInputtipsListener
                    if (code == AMapException.CODE_AMAP_SUCCESS) {
                        continuation.resume(
                            tips.orEmpty().mapNotNull { tip -> tip.toPlaceSearchItemOrNull() }
                        )
                    } else {
                        continuation.resume(emptyList())
                    }
                }
                inputTips.requestInputtipsAsyn()
            } catch (_: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    private fun Tip.toPlaceSearchItemOrNull(): PlaceSearchItem? {
        val latLon = point ?: return null
        val title = name?.takeIf { it.isNotBlank() } ?: return null
        val displayAddress = when {
            !address.isNullOrBlank() -> address
            !district.isNullOrBlank() -> district
            else -> title
        }
        return PlaceSearchItem(
            title = title,
            address = displayAddress,
            latitude = latLon.latitude,
            longitude = latLon.longitude,
            poiId = poiID
        )
    }

    private fun ManualPlaceData.toPlaceSearchItem(fromMapInteraction: Boolean): PlaceSearchItem {
        return PlaceSearchItem(
            title = title,
            address = address,
            latitude = latitude,
            longitude = longitude,
            fromMapInteraction = fromMapInteraction
        )
    }

    private fun com.watermarkcamera.location.LocationData.toPlaceSearchItem(): PlaceSearchItem {
        val label = address?.takeIf { it.isNotBlank() } ?: "当前位置"
        return PlaceSearchItem(
            title = "当前位置",
            address = label,
            latitude = latitude,
            longitude = longitude,
            fromMapInteraction = true
        )
    }

    private fun Double.toCenterKey(): Double {
        return String.format(Locale.US, "%.4f", this).toDouble()
    }
}
