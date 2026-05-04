package com.watermarkcamera.ui.placepicker

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.watermarkcamera.ui.camera.ManualPlaceData
import com.watermarkcamera.ui.components.LargeButton
import com.watermarkcamera.ui.components.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerScreen(
    initialPlace: ManualPlaceData?,
    onNavigateBack: () -> Unit,
    onConfirmPlace: (ManualPlaceData) -> Unit,
    viewModel: PlacePickerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val currentMapView by rememberUpdatedState(mapView)
    val selectedMarker = remember { arrayOfNulls<Marker>(1) }

    DisposableEffect(lifecycleOwner, currentMapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                currentMapView.onResume()
            }

            override fun onResume(owner: LifecycleOwner) {
                currentMapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                currentMapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                currentMapView.onPause()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                currentMapView.onDestroy()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentMapView.onPause()
        }
    }

    LaunchedEffect(initialPlace) {
        viewModel.initialize(initialPlace)
    }

    LaunchedEffect(Unit) {
        mapView.map.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            setOnMapClickListener { latLng ->
                moveCamera(CameraUpdateFactory.newLatLng(latLng))
            }
            setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                override fun onCameraChange(cameraPosition: CameraPosition) = Unit

                override fun onCameraChangeFinish(cameraPosition: CameraPosition) {
                    val target = cameraPosition.target ?: return
                    viewModel.onMapCenterChanged(target.latitude, target.longitude)
                    viewModel.resolveMapCenterSelection(target.latitude, target.longitude)
                }
            })
        }
    }

    LaunchedEffect(uiState.mapCameraLatitude, uiState.mapCameraLongitude, uiState.hasPendingCameraMove) {
        val latitude = uiState.mapCameraLatitude ?: return@LaunchedEffect
        val longitude = uiState.mapCameraLongitude ?: return@LaunchedEffect
        val latLng = LatLng(latitude, longitude)
        if (selectedMarker[0] == null) {
            selectedMarker[0] = mapView.map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            selectedMarker[0]?.position = latLng
        }
        if (uiState.hasPendingCameraMove) {
            val update = if (uiState.hasCompletedInitialMapMove) {
                CameraUpdateFactory.newLatLng(latLng)
            } else {
                CameraUpdateFactory.newLatLngZoom(latLng, 16f)
            }
            mapView.map.animateCamera(update)
            if (!uiState.hasCompletedInitialMapMove) {
                viewModel.markInitialMapMoveCompleted()
            }
            viewModel.markPendingCameraMoveConsumed()
        }
    }

    LaunchedEffect(uiState.selectedPlaceVersion, uiState.selectedPlace) {
        val place = uiState.selectedPlace ?: return@LaunchedEffect
        val latLng = LatLng(place.latitude, place.longitude)
        if (selectedMarker[0] == null) {
            selectedMarker[0] = mapView.map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            selectedMarker[0]?.position = latLng
        }
        selectedMarker[0]?.title = place.title
        selectedMarker[0]?.snippet = place.address
        selectedMarker[0]?.showInfoWindow()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "手动选点",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索地点") },
                placeholder = { Text("如：人民医院、北京站、小区名") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LargeButton(
                    text = if (uiState.isSearching) "搜索中..." else "搜索",
                    onClick = { viewModel.searchPlaces() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSearching
                )
                SecondaryButton(
                    text = "确认使用",
                    onClick = {
                        viewModel.buildManualPlaceData()?.let(onConfirmPlace)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.selectedPlace != null
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = {
                        it.onResume()
                    }
                )

                if (uiState.isLoadingCurrentLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.selectedPlace == null && !uiState.isSearching) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "先搜索地点，或直接拖动地图选择位置",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (uiState.lastSearchSourceIsMap) "已根据地图当前位置生成候选地点" else "请选择一个搜索结果或地图候选点",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.searchResults) { place ->
                        val isSelected = uiState.selectedPlace == place
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectPlace(place) }
                                .padding(14.dp)
                        ) {
                            Text(
                                text = place.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = place.address,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "纬度: %.6f  经度: %.6f".format(place.latitude, place.longitude),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (place.fromMapInteraction) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "地图候选点",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "已选中",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
