# iOS Launcher (Android)

A lightweight Jetpack Compose home-screen launcher with an iOS-inspired look and feel.
Only original look-alike assets are used: no Apple fonts, icons or wallpapers. Text uses **Inter** (SIL OFL 1.1, see `FONT_LICENSE_Inter_OFL.txt`).

- Kotlin + Jetpack Compose, minSdk 26, targetSdk 34, release APK ≈ 2 MB (R8 + resource shrinking)
- Install the APK, press Home, choose **iOS Launcher**, then *Always* (the onboarding screen asks for this too)

## Features (v1.0.0)
**Home screen**
- Configurable grid (default 4×6, 3–6 columns × 4–8 rows), horizontal paging, page dots
- Icons rendered full-bleed from adaptive icons and masked to an original superellipse "squircle"
- Frosted dock: the real (blurred) system wallpaper aligned behind the dock, with a translucent fallback
- Long-press → iOS-style context menu: app shortcuts (LauncherApps), Edit Home Screen, Add to Home, App Info, Hide, Remove, Delete App
- Long-press + drag → jiggle edit mode: reorder, **move between pages** (hold at the screen edge), **into and out of the dock** (max 4), drop onto an icon to make or extend a **folder**; a new page appears while dragging
- Edit mode: minus badge → Remove from Home / Delete App (uninstall); "+" for widgets, Settings, Done
- Folders: frosted-wallpaper panel, rename, take apps out
- Notification **badges** on icons and folders (needs notification access)
- Spring animations, scale-up app-open animation, spring zoom on return

**Gestures and overlays**
- Swipe down in the middle → Spotlight-like search
- Swipe down from the **top-right** → **Control Center**: Wi-Fi / Internet / Bluetooth / Airplane (open the system panels), flashlight toggle, rotation lock, brightness and volume sliders, Focus/DND, Camera, Settings, **media controls** (title, art, play/pause, previous/next)
- Swipe down from the **top-left** → **Notification Center**: clock, date, notification list (tap to open, swipe to dismiss, Clear all)
- Overlays blur the launcher; on Android 12+ the window also blurs the wallpaper behind it

**Pages**
- **Today View** (left of page 1): search, built-in Date/Clock and Battery widgets, plus **real Android widgets** (AppWidgetHost, bind and configure flows, remove in Edit)
- **App Library** (last page): Recently Added + automatic categories, A–Z list with search

**Settings** (Edit mode → Settings): grid size, badges, dock blur, **icon packs** (ADW / Nova / Apex / GO `appfilter.xml`), **hide apps**, permission shortcuts, layout reset.
**Onboarding** on first run: set as default Home app, notification access, write-settings, and storage (wallpaper blur on Android ≤ 12).

## Build
```
export JAVA_HOME=~/jdk17
./gradlew testDebugUnitTest assembleDebug          # debug APK
./gradlew assembleRelease                          # signed if ./keystore.properties exists (never committed)
```
`keystore.properties` holds the following keys: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Android limits (no root)
- Wi-Fi, Bluetooth and Airplane mode **cannot be toggled directly** by third-party apps on modern Android. The tiles open the system panels or settings instead.
- Control Center and Notification Center live **inside the launcher**. They can't replace the system shade over other apps; that would need system-overlay hacks.
- Brightness and rotation lock need the user to grant "Modify system settings". Without it, brightness only affects the launcher window.
- Real wallpaper blur needs read access to the wallpaper. Android 13+ blocks this for normal apps, so the dock falls back to translucent there. Overlays still use the Android 12+ window blur when the device supports it. Live wallpapers always use the fallback.
- App shortcuts only work while this is the **default launcher**.
- Badges and media controls need **notification access**.

## Possible next steps
- Live Activities / Dynamic Island look-alike, Stack widgets, widgets on home pages
- Parallax wallpaper, haptics tuning, more icon-pack options (masks, scale)
