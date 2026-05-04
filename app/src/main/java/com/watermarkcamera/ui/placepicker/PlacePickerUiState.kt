package com.watermarkcamera.ui.placepicker

data class PlaceSearchItem(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val poiId: String? = null,
    val fromMapInteraction: Boolean = false
)

data class PlacePickerUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<PlaceSearchItem> = emptyList(),
    val selectedPlace: PlaceSearchItem? = null,
    val errorMessage: String? = null,
    val currentCenterLatitude: Double? = null,
    val currentCenterLongitude: Double? = null,
    val mapCameraLatitude: Double? = null,
    val mapCameraLongitude: Double? = null,
    val hasCenteredOnInitialLocation: Boolean = false,
    val hasCompletedInitialMapMove: Boolean = false,
    val hasPendingCameraMove: Boolean = false,
    val selectedPlaceVersion: Int = 0,
    val isLoadingCurrentLocation: Boolean = false,
    val lastSearchSourceIsMap: Boolean = false
)
