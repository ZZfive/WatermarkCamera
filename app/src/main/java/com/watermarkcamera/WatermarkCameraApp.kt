package com.watermarkcamera

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.watermarkcamera.ui.camera.CameraScreen
import com.watermarkcamera.ui.preview.PreviewScreen
import com.watermarkcamera.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Settings : Screen("settings")
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
            CameraScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPreview = { uri ->
                    navController.navigate(Screen.Preview.createRoute(uri))
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