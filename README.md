# iOS Launcher (Android)

Lightweight Jetpack Compose home-screen launcher with an iOS-inspired look and feel. Uses only original look-alike assets — no Apple fonts, icons, or wallpapers.

- Kotlin + Jetpack Compose, minSdk 26, targetSdk 34
- Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Install, press Home, choose **iOS Launcher**, then "Always".

## Features (v0.1.0)
- 4×6 home grid, horizontal paging, page dots
- App icons masked to an original superellipse ("squircle") shape
- Translucent dock (long-press → edit, remove)
- Swipe down on home → Spotlight-like app search
- App Library (last page): auto categories (Android app category) + A–Z list with search
- Long-press → jiggle edit mode, minus badge removes from home (app stays in App Library), Done button
- Long-press-drag an icon onto another → folder (or adds to folder); drag onto empty area reorders
- Folders: blurred overlay (API 31+), editable name, remove apps in edit mode
- Spring press / overlay animations, scale-up app-open animation, spring zoom on return
- System wallpaper shown behind; layout persisted (SharedPreferences JSON)
- Home button closes overlays → exits edit mode → returns to page 1

## TODO
- Control Center (swipe down from top-right) and Notification Center
- Widgets (AppWidgetHost) and Today View (left page)
- Notification badges (NotificationListenerService)
- Drag icons across pages / into dock; add-to-home from App Library
- Real wallpaper blur for the dock (currently translucent tint)
- Bundle Inter font (OFL) in res/font; icon packs; settings screen
- Shortcuts (long-press menu), app uninstall from edit mode
- Smarter Suggestions (usage stats)
