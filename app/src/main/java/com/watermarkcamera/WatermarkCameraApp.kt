package com.watermarkcamera

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.watermarkcamera.ui.camera.CameraScreen
import com.watermarkcamera.ui.camera.ManualPlaceData
import com.watermarkcamera.ui.placepicker.PlacePickerScreen
import com.watermarkcamera.ui.preview.PreviewScreen
import com.watermarkcamera.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val MANUAL_PLACE_RESULT_KEY = "manual_place_result"

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
            LaunchedEffect(Unit) {
                val place = navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.get<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
                if (place != null) {
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
                }
            }

            CameraScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPreview = { uri ->
                    navController.navigate(Screen.Preview.createRoute(uri))
                },
                onNavigateToPlacePicker = {
                    navController.navigate(Screen.PlacePicker.route)
                },
                consumeManualPlaceResult = {
                    val place = navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.get<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
                    if (place != null) {
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.remove<ManualPlaceData>(MANUAL_PLACE_RESULT_KEY)
                    }
                    place
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
            PlacePickerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConfirmPlace = { place ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(MANUAL_PLACE_RESULT_KEY, place)
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
