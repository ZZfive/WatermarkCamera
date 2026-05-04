# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**WatermarkCamera** is an Android camera app built with Jetpack Compose and Kotlin. It captures photos and overlays watermarks with timestamp, GPS location address, coordinates, and custom text. The app uses the AMap (高德地图) SDK for location services, POI search, and reverse geocoding.

### Tech Stack

- **Language**: Kotlin 1.9.22
- **UI**: Jetpack Compose with Material3
- **Camera**: CameraX 1.3.1
- **Location**: Google Play Services Location + Android system location providers (multi-strategy fallback)
- **Maps/Geocoding**: AMap SDK 10.1.200
- **Navigation**: Jetpack Navigation Compose
- **Architecture**: MVVM with `StateFlow`-based unidirectional data flow
- **Build**: Gradle 8.4, AGP 8.2.2

## Key Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing.properties)
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run tests
./gradlew test

# Install debug APK to device
./gradlew installDebug

# Lint
./gradlew lint

# Start the dev environment
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Local Setup

1. Copy `local.properties.example` to `local.properties` and add your AMap API key.
2. Copy `signing.properties.example` to `signing.properties` and add signing config (optional for debug builds).
3. Both property files are in `.gitignore` — never commit real keys.

## Architecture

### Navigation

Single-activity app with 4 screens managed by `NavHost` in `WatermarkCameraApp.kt`:

- **Camera** (`camera`) — Main screen with camera preview, location info card, and capture button
- **Settings** (`settings`) — Watermark block customization (toggle, alignment, font size, background)
- **PlacePicker** (`place_picker`) — AMap map view with POI search and reverse geocoding for manual location selection
- **Preview** (`preview/{photoUri}`) — Photo preview with watermark preview, share, and save actions

Navigation uses `SavedStateHandle` for passing data between Camera and PlacePicker screens (manual place selection roundtrip).

### Module Structure

All code lives under `com.watermarkcamera` in `app/src/main/java/`:

| Module | Description |
|--------|-------------|
| `WatermarkCameraApplication` | App entry, initializes AMap privacy compliance |
| `MainActivity` | Hosts `WatermarkCameraApp` composable |
| `WatermarkCameraApp` | Navigation setup |
| `ui/camera/` | Camera screen + ViewModel, location state models |
| `ui/preview/` | Photo preview screen + ViewModel |
| `ui/settings/` | Settings screen + ViewModel for watermark config |
| `ui/placepicker/` | Map-based place picker screen + ViewModel |
| `ui/components/` | Reusable Compose components (LargeButton, FontSizeSlider, GridPositionSelector) |
| `ui/theme/` | Compose theme (Color, Type, Theme) |
| `camera/CameraXManager` | CameraX lifecycle management, photo capture to Bitmap |
| `location/LocationManager` | Multi-strategy location fetch (GMS fused → GPS → network → fallback) |
| `location/GeocodingHelper` | Android Geocoder for reverse geocoding |
| `watermark/` | Watermark rendering and configuration |
| `data/WatermarkPreferences` | SharedPreferences-backed settings persistence |
| `util/` | PermissionUtils, DateTimeUtils |

### Key Design Patterns

- **StateFlow + ViewModel**: Each screen has a dedicated ViewModel exposing `StateFlow<UiState>`. UI collects state and dispatches events via callback lambdas.
- **Multi-strategy location**: `LocationManager` tries 5 fallback strategies (last known → GMS single → GPS → network → GMS updates) to maximize success on devices without GMS (common in China).
- **Watermark rendering**: `WatermarkComposer` uses Android Canvas to draw text blocks onto the captured Bitmap. Supports 4 independent blocks (timestamp, address, coords, custom), each with configurable alignment (9-grid), font size, and background toggle. Paint objects are cached via LRU.
- **Settings persistence**: `WatermarkPreferences` wraps SharedPreferences, providing typed getters/setters and `loadLayoutConfig()`/`saveLayoutConfig()` for bulk operations.

### Image Pipeline

1. `CameraXManager.takePictureToBitmap()` captures from CameraX `ImageCapture`
2. `CameraViewModel.composeWatermarkBitmap()` calls `WatermarkComposer.composeWatermark()` with current `WatermarkConfig` + `WatermarkLayoutConfig`
3. Result saved to gallery via `MediaStore` (Android 10+) or filesystem (legacy)
4. Navigation triggers to `PreviewScreen` with the photo URI

## Important Configuration Files

- `app/build.gradle.kts` — Android config, dependencies, Compose/CameraX/AMap versions
- `local.properties` — AMap API key (gitignored)
- `signing.properties` — Release signing config (gitignored)
- `app/src/main/AndroidManifest.xml` — Permissions, AMap meta-data, FileProvider
