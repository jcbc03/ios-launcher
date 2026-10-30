# iOS Launcher for Android

Android launcher mimicking iOS home screen using Jetpack Compose.

## Features
- HorizontalPager with multiple home pages (4x6 icon grid)
- Custom squircle (superellipse) icon clipping for adaptive icons
- Dock with 4 pinned apps on frosted translucent background
- Page indicator dots
- Swipe down for Spotlight-like global search overlay (filters installed apps)
- Last page is App Library with auto-categorization (by ApplicationInfo.category) in 2x2 tiles + alphabetical list
- Long press to enter jiggle/edit mode (continuous wobble rotation animation, remove icons with minus badge)
- Drag and drop icons to create folders; tap folder opens blurred modal grid with rename
- Spring-based animations for presses, overlays, transitions
- App launches use ActivityOptions.makeScaleUpAnimation for iOS-like effect
- Persistent home layout and folders stored in SharedPreferences as JSON (org.json)
- No Apple assets used

## TODO
- Control Center (swipe from top right)
- Widgets support
- Notification badges on icons
- Icon packs support
- Bundle Inter font for system-like typography
- Folder drag reordering, multiple pages per folder
- Better blur implementation (RenderScript or Coil blur)

## Tech
- Kotlin + Jetpack Compose
- AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.00
- compileSdk/targetSdk 34, minSdk 26
- Gradle 8.9, Kotlin DSL

Build with `./gradlew :app:assembleDebug` (network required for first build).
