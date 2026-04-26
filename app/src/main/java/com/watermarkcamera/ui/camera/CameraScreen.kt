package com.watermarkcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermarkcamera.data.WatermarkPreferences
import com.watermarkcamera.ui.components.LargeButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val previewView = remember { PreviewView(context) }

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

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(48.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
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
                                .padding(16.dp)
                                .size(48.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
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
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        InfoCard(
                            locationData = uiState.locationData,
                            onRefreshLocation = { viewModel.fetchLocation() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .height(72.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            LargeButton(
                                text = "拍照",
                                onClick = { viewModel.takePicture() },
                                enabled = uiState.isCameraReady && !uiState.isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    locationData: LocationUiData?,
    onRefreshLocation: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { WatermarkPreferences(context) }
    val layoutConfig = preferences.loadLayoutConfig()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (layoutConfig.timestamp.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "时间: ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
                            .format(Date()),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (layoutConfig.address.enabled) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "位置: ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        if (locationData?.isLoading == true) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = when {
                                    locationData?.hasAddress() == true -> locationData.address.orEmpty()
                                    locationData?.hasCoordinates() == true -> "地址解析失败"
                                    else -> locationData?.statusMessage ?: "位置不可用"
                                },
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (layoutConfig.address.enabled || layoutConfig.coords.enabled) {
                    IconButton(
                        onClick = onRefreshLocation,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新位置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (layoutConfig.coords.enabled && locationData?.hasCoordinates() == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = locationData.coordinateText().orEmpty(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (!locationData?.statusMessage.isNullOrBlank() && locationData?.hasAddress() != false) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = locationData?.statusMessage.orEmpty(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (layoutConfig.custom.enabled && preferences.customText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "文本: ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = preferences.customText,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
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
