package com.watermarkcamera.ui.placepicker

data class PlaceSearchItem(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val poiId: String? = null
)

data class PlacePickerUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<PlaceSearchItem> = emptyList(),
    val selectedPlace: PlaceSearchItem? = null,
    val errorMessage: String? = null
)
