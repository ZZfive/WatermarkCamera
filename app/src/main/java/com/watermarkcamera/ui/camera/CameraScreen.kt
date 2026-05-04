package com.watermarkcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermarkcamera.data.WatermarkPreferences
import com.watermarkcamera.ui.components.LargeButton
import com.watermarkcamera.watermark.WatermarkLayoutConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    onNavigateToPlacePicker: (LocationUiData?) -> Unit,
    consumeManualPlaceResult: () -> ManualPlaceData?,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(cameraGranted, locationGranted)
    }

    fun checkAndRequestPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val hasLocationPermission = fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED

        viewModel.onPermissionResult(
            hasCameraPermission = cameraPermission == PackageManager.PERMISSION_GRANTED,
            hasLocationPermission = hasLocationPermission
        )

        if (cameraPermission != PackageManager.PERMISSION_GRANTED || !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(uiState.hasCameraPermission) {
        if (uiState.hasCameraPermission) {
            viewModel.initialize(lifecycleOwner, previewView)
        }
    }

    LaunchedEffect(uiState.hasCameraPermission, configuration.orientation) {
        if (uiState.hasCameraPermission) {
            viewModel.updateCameraRotation(previewView)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.capturedPhotoUri) {
        uiState.capturedPhotoUri?.let { uri ->
            onNavigateToPreview(uri.toString())
            viewModel.clearCapturedPhoto()
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions()
    }

    LaunchedEffect(uiState.isManualLocationLocked, uiState.locationData) {
        consumeManualPlaceResult()?.let { place ->
            viewModel.consumeReturnedPlace(place)
        }
    }

    val layoutConfig = remember { WatermarkPreferences(context).loadLayoutConfig() }
    val preferences = remember { WatermarkPreferences(context) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            }
        },
        floatingActionButton = {
            if (!uiState.hasCameraPermission) return@Scaffold
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    FloatingActionButton(
                        onClick = {},
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    FloatingActionButton(
                        onClick = { viewModel.takePicture() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "拍照",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!uiState.hasCameraPermission) {
                PermissionRequestContent(
                    onRequestPermission = { checkAndRequestPermissions() }
                )
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp)
                        .size(44.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.switchCamera(previewView) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 8.dp, start = 8.dp)
                        .size(44.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "切换摄像头",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                InfoCard(
                    locationData = uiState.locationData,
                    isManualLocationLocked = uiState.isManualLocationLocked,
                    layoutConfig = layoutConfig,
                    preferences = preferences,
                    onRefreshLocation = { viewModel.fetchLocation() },
                    onOpenPlacePicker = { onNavigateToPlacePicker(uiState.locationData) },
                    onSwitchToAuto = { viewModel.switchToAutoLocation() }
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    locationData: LocationUiData?,
    isManualLocationLocked: Boolean,
    layoutConfig: WatermarkLayoutConfig,
    preferences: WatermarkPreferences,
    onRefreshLocation: () -> Unit,
    onOpenPlacePicker: () -> Unit,
    onSwitchToAuto: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var panelExpanded by rememberSaveable { mutableStateOf(true) }
    var panelOffsetX by rememberSaveable { mutableStateOf(Float.NaN) }
    var panelOffsetY by rememberSaveable { mutableStateOf(Float.NaN) }
    var panelWidthPx by remember { mutableIntStateOf(0) }
    var panelHeightPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val horizontalMarginPx = with(density) { 12.dp.toPx() }
        val topMarginPx = with(density) { 12.dp.toPx() }
        val bottomReservedPx = with(density) { if (isLandscape) 96.dp.toPx() else 120.dp.toPx() }

        LaunchedEffect(
            containerWidthPx,
            containerHeightPx,
            panelWidthPx,
            panelHeightPx,
            isLandscape,
            panelExpanded
        ) {
            if (panelWidthPx == 0 || panelHeightPx == 0) return@LaunchedEffect

            val defaultOffset = defaultPanelOffset(
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                panelWidthPx = panelWidthPx.toFloat(),
                panelHeightPx = panelHeightPx.toFloat(),
                horizontalMarginPx = horizontalMarginPx,
                topMarginPx = topMarginPx,
                bottomReservedPx = bottomReservedPx,
                isLandscape = isLandscape
            )

            val currentX = if (panelOffsetX.isNaN()) defaultOffset.first else panelOffsetX
            val currentY = if (panelOffsetY.isNaN()) defaultOffset.second else panelOffsetY
            val clamped = clampPanelOffset(
                desiredX = currentX,
                desiredY = currentY,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                panelWidthPx = panelWidthPx.toFloat(),
                panelHeightPx = panelHeightPx.toFloat(),
                horizontalMarginPx = horizontalMarginPx,
                topMarginPx = topMarginPx,
                bottomReservedPx = bottomReservedPx
            )
            panelOffsetX = clamped.first
            panelOffsetY = clamped.second
        }

        Card(
            modifier = Modifier
                .offset {
                    IntOffset(
                        panelOffsetX.takeUnless { it.isNaN() }?.roundToInt() ?: 0,
                        panelOffsetY.takeUnless { it.isNaN() }?.roundToInt() ?: 0
                    )
                }
                .fillMaxWidth(if (isLandscape) 0.45f else 0.9f)
                .widthIn(max = if (isLandscape) 420.dp else 560.dp)
                .onSizeChanged {
                    panelWidthPx = it.width
                    panelHeightPx = it.height
                },
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.62f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f))
                        .pointerInput(
                            containerWidthPx,
                            containerHeightPx,
                            panelWidthPx,
                            panelHeightPx,
                            isLandscape
                        ) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val clamped = clampPanelOffset(
                                    desiredX = (panelOffsetX.takeUnless { it.isNaN() } ?: 0f) + dragAmount.x,
                                    desiredY = (panelOffsetY.takeUnless { it.isNaN() } ?: 0f) + dragAmount.y,
                                    containerWidthPx = containerWidthPx,
                                    containerHeightPx = containerHeightPx,
                                    panelWidthPx = panelWidthPx.toFloat(),
                                    panelHeightPx = panelHeightPx.toFloat(),
                                    horizontalMarginPx = horizontalMarginPx,
                                    topMarginPx = topMarginPx,
                                    bottomReservedPx = bottomReservedPx
                                )
                                panelOffsetX = clamped.first
                                panelOffsetY = clamped.second
                            }
                        }
                        .clickable { panelExpanded = !panelExpanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.7f))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "拖动或点按这里展开/收起",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.78f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (layoutConfig.timestamp.enabled) {
                                    Text(
                                        text = SimpleDateFormat("HH:mm  yyyy-MM-dd", Locale.getDefault())
                                            .format(Date()),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }

                                val summaryText = when {
                                    layoutConfig.address.enabled && locationData?.hasAddress() == true -> {
                                        locationData.displayAddress().orEmpty()
                                    }
                                    locationData?.hasCoordinates() == true -> {
                                        locationData.coordinateText().orEmpty()
                                    }
                                    else -> {
                                        locationData?.statusMessage ?: "拖动面板以避开画面"
                                    }
                                }

                                Text(
                                    text = summaryText,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = if (panelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (panelExpanded) "收起" else "展开",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = panelExpanded,
                    enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)) +
                        fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(220)) +
                        fadeOut(animationSpec = tween(220))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (layoutConfig.timestamp.enabled) {
                            Text(
                                text = "时间: " + SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
                                    .format(Date()),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (layoutConfig.address.enabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isManualLocationLocked) Color(0xFFFFB300) else Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "位置: ",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                if (locationData?.isLoading == true) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    val displayText = when {
                                        locationData?.hasAddress() == true -> locationData.displayAddress().orEmpty()
                                        locationData?.hasCoordinates() == true -> "地址解析失败"
                                        else -> locationData?.statusMessage ?: "位置不可用"
                                    }
                                    Text(
                                        text = displayText,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (layoutConfig.coords.enabled && locationData?.hasCoordinates() == true) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = locationData.coordinateText().orEmpty(),
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        if (!locationData?.statusMessage.isNullOrBlank() && locationData?.hasAddress() != false) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = locationData?.statusMessage.orEmpty(),
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        if (layoutConfig.address.enabled || layoutConfig.coords.enabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextButton(
                                    text = if (isManualLocationLocked) "重新选点" else "手动选点",
                                    onClick = onOpenPlacePicker,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isManualLocationLocked) {
                                    OutlinedTextButton(
                                        text = "切回自动",
                                        onClick = onSwitchToAuto,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    OutlinedTextButton(
                                        text = "刷新位置",
                                        onClick = onRefreshLocation,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (layoutConfig.custom.enabled && preferences.customText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "文本: ",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = preferences.customText,
                                    fontSize = 16.sp,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultPanelOffset(
    containerWidthPx: Float,
    containerHeightPx: Float,
    panelWidthPx: Float,
    panelHeightPx: Float,
    horizontalMarginPx: Float,
    topMarginPx: Float,
    bottomReservedPx: Float,
    isLandscape: Boolean
): Pair<Float, Float> {
    val defaultX = if (isLandscape) {
        containerWidthPx - panelWidthPx - horizontalMarginPx
    } else {
        (containerWidthPx - panelWidthPx) / 2f
    }
    val defaultY = containerHeightPx - panelHeightPx - bottomReservedPx
    return clampPanelOffset(
        desiredX = defaultX,
        desiredY = defaultY,
        containerWidthPx = containerWidthPx,
        containerHeightPx = containerHeightPx,
        panelWidthPx = panelWidthPx,
        panelHeightPx = panelHeightPx,
        horizontalMarginPx = horizontalMarginPx,
        topMarginPx = topMarginPx,
        bottomReservedPx = bottomReservedPx
    )
}

private fun clampPanelOffset(
    desiredX: Float,
    desiredY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    panelWidthPx: Float,
    panelHeightPx: Float,
    horizontalMarginPx: Float,
    topMarginPx: Float,
    bottomReservedPx: Float
): Pair<Float, Float> {
    val maxX = (containerWidthPx - panelWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
    val maxY = (containerHeightPx - panelHeightPx - bottomReservedPx).coerceAtLeast(topMarginPx)
    return Pair(
        desiredX.coerceIn(horizontalMarginPx, maxX),
        desiredY.coerceIn(topMarginPx, maxY)
    )
}

@Composable
private fun OutlinedTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "需要相机和位置权限",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "为了添加水印，需要获取相机和位置权限",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        LargeButton(
            text = "授予权限",
            onClick = onRequestPermission
        )
    }
}
