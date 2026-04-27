package com.watermarkcamera.ui.placepicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.services.core.AMapException
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.poisearch.PoiSearch
import com.watermarkcamera.ui.camera.ManualPlaceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlacePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlacePickerUiState())
    val uiState: StateFlow<PlacePickerUiState> = _uiState.asStateFlow()

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun selectPlace(place: PlaceSearchItem) {
        _uiState.update { it.copy(selectedPlace = place) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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

            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = result,
                    selectedPlace = result.firstOrNull(),
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
}
