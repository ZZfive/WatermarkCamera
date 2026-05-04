package com.watermarkcamera

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.SavedStateHandle
import com.watermarkcamera.ui.camera.CameraScreen
import com.watermarkcamera.ui.camera.LocationUiData
import com.watermarkcamera.ui.camera.ManualPlaceData
import com.watermarkcamera.ui.camera.displayAddress
import com.watermarkcamera.ui.placepicker.PlacePickerScreen
import com.watermarkcamera.ui.preview.PreviewScreen
import com.watermarkcamera.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val MANUAL_PLACE_RESULT_KEY = "manual_place_result"
private const val PLACE_PICKER_SEED_KEY = "place_picker_seed"

private fun LocationUiData.toPlacePickerSeed(): ManualPlaceData? {
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null
    val displayText = displayAddress().orEmpty().ifBlank { "当前位置" }
    return ManualPlaceData(
        title = title?.takeIf { it.isNotBlank() } ?: displayText,
        address = address?.takeIf { it.isNotBlank() } ?: displayText,
        latitude = latitude,
        longitude = longitude
    )
}

private fun consumeManualPlaceResult(savedStateHandle: SavedStateHandle?): ManualPlaceData? {
    val place = savedStateHandle?.get<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
    if (place != null) {
        savedStateHandle.remove<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
    }
    return place
}

private fun readPlacePickerSeed(savedStateHandle: SavedStateHandle?): ManualPlaceData? {
    return savedStateHandle?.get(PLACE_PICKER_SEED_KEY)
}

private fun setPlacePickerSeed(savedStateHandle: SavedStateHandle?, locationData: LocationUiData?) {
    savedStateHandle?.set(PLACE_PICKER_SEED_KEY, locationData?.toPlacePickerSeed())
}

private fun updatePlacePickerSeed(savedStateHandle: SavedStateHandle?, place: ManualPlaceData?) {
    savedStateHandle?.set(PLACE_PICKER_SEED_KEY, place)
}

private fun clearPlacePickerSeed(savedStateHandle: SavedStateHandle?) {
    savedStateHandle?.remove<ManualPlaceData>(PLACE_PICKER_SEED_KEY)
}

private fun clearManualPlaceResult(savedStateHandle: SavedStateHandle?) {
    savedStateHandle?.remove<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
}

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Settings : Screen("settings")
    data object PlacePicker : Screen("place_picker")
    data object Preview : Screen("preview/{photoUri}") {
        fun createRoute(photoUri: String): String {
            val encoded = URLEncoder.encode(photoUri, "UTF-8")
            return "preview/$encoded"
        }
    }
}

@Composable
fun WatermarkCameraApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route
    ) {
        composable(Screen.Camera.route) {
            val currentHandle = navController.currentBackStackEntry?.savedStateHandle
            CameraScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPreview = { uri ->
                    navController.navigate(Screen.Preview.createRoute(uri))
                },
                onNavigateToPlacePicker = { locationData ->
                    clearManualPlaceResult(currentHandle)
                    setPlacePickerSeed(currentHandle, locationData)
                    navController.navigate(Screen.PlacePicker.route)
                },
                consumeManualPlaceResult = {
                    consumeManualPlaceResult(currentHandle)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.PlacePicker.route) {
            val previousSavedStateHandle = navController.previousBackStackEntry?.savedStateHandle
            val seedPlace = readPlacePickerSeed(previousSavedStateHandle)
            PlacePickerScreen(
                initialPlace = seedPlace,
                onNavigateBack = {
                    clearPlacePickerSeed(previousSavedStateHandle)
                    navController.popBackStack()
                },
                onConfirmPlace = { place ->
                    previousSavedStateHandle?.set(MANUAL_PLACE_RESULT_KEY, place)
                    updatePlacePickerSeed(previousSavedStateHandle, place)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Preview.route) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("photoUri") ?: ""
            val photoUri = try {
                URLDecoder.decode(encodedUri, "UTF-8")
            } catch (e: Exception) {
                encodedUri
            }
            PreviewScreen(
                photoUri = photoUri,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRetake = {
                    navController.popBackStack(Screen.Camera.route, inclusive = false)
                }
            )
        }
    }
}
