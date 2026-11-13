# AURA Engineering Log — Critical Bug Fix, Liquid Glass & Spring Motion

## 1. Problem 1: Critical Now Playing Crash — Root-Cause Diagnosis & Fix

### Primary Root Cause (`IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext`)
- **Location:** `AuraRoot.kt:66` (`val sheet = remember { PlayerSheet(app.appScope) }`) and `AuraEnv.kt:76–109` (`PlayerSheet`).
- **Mechanism:**
  - `AuraApp.appScope` (`AuraApp.kt:18`) is constructed in `Application.onCreate()` as `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`. Because it is an application-level scope created outside Jetpack Compose, its `CoroutineContext` does **not** contain an `androidx.compose.runtime.MonotonicFrameClock`.
  - `PlayerController` only used `appScope` for Room I/O (`Dispatchers.IO`), so selecting a song and starting playback worked normally and `MiniPlayer` appeared at the bottom of the screen.
  - However, `PlayerSheet` held a `androidx.compose.animation.core.Animatable(0f)` (`progress`) and launched `progress.animateTo(...)` on `app.appScope` inside `expand()`, `collapse()`, and `settle()`.
  - The exact instant the user tapped the Mini Player (`env.sheet.expand()`) or lifted their finger after dragging it (`env.sheet.settle(velocityY)`), `Animatable.animateTo` invoked `withFrameNanos`, which threw:
    ```
    java.lang.IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext.
    Callers should supply an appropriate MonotonicFrameClock using withContext.
    ```
    Because `appScope` runs on `Dispatchers.Main.immediate` with no `CoroutineExceptionHandler`, this uncaught exception immediately crashed the Android process.
- **Fix:**
  - `AuraRoot.kt` now initializes `PlayerSheet(scope)` using the composition's `rememberCoroutineScope()` (which carries Compose's `AndroidUiFrameClock` / `MonotonicFrameClock`) and re-binds the active composition scope on every recomposition via `SideEffect { sheet.bindScope(scope) }`.
  - `PlayerSheet` also checks `coroutineContext[MonotonicFrameClock] != null` before calling `progress.animateTo(...)` (falling back to `progress.snapTo(target)` if ever invoked outside a frame clock) and guards all animation calls against cancellation or unexpected exceptions.

### Secondary Root Causes Fixed in Now Playing, Playback & Glass Rendering
1. **`ArtworkPager` Contradictory Aspect-Ratio Constraints (`NowPlaying.kt:126, 209–212`):**
   - Previously, `ArtworkPager` sat inside `Box(Modifier.weight(1f).fillMaxWidth())` and applied `.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)`. On 20:9 portrait phones, `maxHeight` of the weighted box (~450 dp) exceeded `maxWidth` (~344 dp), locking `minHeight = maxHeight > maxWidth` so a 1:1 square was impossible without constraint violation/distortion; in landscape or split-screen, tight vertical height could collapse the box to 0 px.
   - **Fix:** `ArtworkPager` now uses `BoxWithConstraints` and computes `val side = minOf(maxWidth, maxHeight).coerceAtLeast(64.dp)` with `.size(side)`, guaranteeing a true 1:1 square across portrait, landscape, and multi-window layouts, and guards `size.width.coerceAtLeast(1f)` in horizontal swipe gestures.
2. **Zero-Size `RadialGradient` Crash in `Glass.kt` (`Glass.kt:101–137`):**
   - During the initial 0-height frame of `AnimatedVisibility(expandVertically / shrinkVertically)`, `w == 0f && h == 0f` caused `Brush.radialGradient(..., radius = maxOf(w, h) * 0.75f)` to pass `radius = 0f` to `android.graphics.RadialGradient`, which throws `IllegalArgumentException: radius must be > 0`.
   - **Fix:** All radial gradient radii in `Glass.kt` are clamped to `>= 1f`, and `onDrawWithContent` / `onDrawBehind` skip offscreen `GraphicsLayer.record` and path drawing when `w <= 0.5f || h <= 0.5f`.
3. **Spring Overshoot / Undershoot Value Clamping (`NowPlaying.kt`, `Controls.kt`, `Overlay.kt`):**
   - Underdamped springs (`Spr.Bouncy`, `Spr.Snappy`, `Spr.MiniPlayer`) overshoot above `1.0f` and undershoot below `0.0f`. Previously, `NowPlayingLayer` computed `translationY = (1f - sheet.progress.value) * size.height`, which translated the sheet above the screen bottom on overshoot (`progress > 1f`), and un-clamped spring fractions were multiplied into `Color.copy(alpha = ...)` and circle radii.
   - **Fix:** `NowPlayingLayer` clamps slide translation to `0f..1f` and converts positive overshoot (`rawP - 1f`) into a physical vertical elastic stretch (`scaleY > 1f`, `scaleX < 1f`) anchored at `TransformOrigin(0.5f, 1f)`. All alpha values and radii are strictly clamped.
4. **Resilient Media3 Metadata & Queue Bounds (`PlayerController.kt`, `Sheets.kt`, `ArtFetcher.kt`, `ArtPalette.kt`):**
   - `MediaItem.toQueueItem()` now resolves `artUri` through a fallback chain (`extras["uri"]` → `requestMetadata.mediaUri` → `localConfiguration.uri` → `mediaMetadata.artworkUri`) so artwork is never lost when ExoPlayer merges in-stream ID3/MP4 tags.
   - `PlayerController` clamps `C.TIME_UNSET` in `position()` and `duration()`, and bounds-checks all queue index operations (`play`, `playNext`, `jumpTo`, `removeAt`, `moveQueueItem`, `next`, `previous`).
   - `NowPlayingContent`'s track menu button constructs a fallback `TrackEntity` from `QueueItem` if `dao.trackById(id)` returns null.
   - `QueueSheet` guards `scrollToItem` against empty queues and out-of-range indices.
   - `ArtFetcher` and `ArtPalette` guard against blank URIs, invalid image dimensions, `Bitmap.Config.HARDWARE`, and recycled bitmaps.

---

## 2. Problem 2: Preserving the Existing Blur & Implementing Real Liquid Glass

- **Preserved Backdrop Blur Pipeline:**
  - `AuraRoot` records the scene once per frame into `backdropLayer: GraphicsLayer`.
  - Floating surfaces (`FloatingNav`, `MiniPlayer`, `OverlayHost` menus/dialogs/sheets/toasts) sample `backdrop.layer` translated by `-origin.x, -origin.y` into their own `GraphicsLayer` with `BlurEffect(radius, radius, TileMode.Clamp)` and a saturation boost `ColorFilter` on Android 12+ (API 31+).
- **New Optical Liquid Glass Layers (`Glass.kt`, cached per size in `drawWithCache`):**
  1. **Material Hierarchy (`GlassLevel`):** `Subtle`, `Regular`, `Strong`, and `Lens` (3D liquid droplet/pill for active nav indicator, toggle thumb, primary transport controls, and badges).
  2. **Floating Elevation Shadow:** Offset translucent shadow path beneath `Regular`, `Strong`, and `Lens` surfaces.
  3. **Multi-Stop Optical Body Density:** 3-stop vertical gradient (brighter top meniscus, clearer optical center window, denser lower curve) adapted for both Dark and Light themes.
  4. **Upper & Lower Internal Lens Crescents:** Pronounced crescent reflections at the top and bottom curvatures (modeled after the WWDC25 / iOS 26 Control Center and navigation lens reference images).
  5. **Refraction-Inspired Inner Bevel Ring:** Computed via `PathOperation.Difference` between the outer outline and an inset `RoundRect`, painted with an angled highlight/shadow/chromatic gradient that creates a thick, rounded 3D glass wall.
  6. **Prismatic Caustic Reflections & Directional Specular Rim:** Dual corner caustics (`accent.c` and `accent.a`) and a 5-stop directional outer border highlight.

---

## 3. Problem 3: Centralized Spring Motion System (`Motion.kt`)

- **Centralized `Spr` Presets:**
  - `Navigation` (`0.70f, 360f`), `ButtonPress` (`0.90f, 760f`), `ButtonRelease` (`0.46f, 360f`), `Dropdown` (`0.62f, 410f`), `BottomSheet` (`0.74f, 350f`), `MiniPlayer` (`0.74f, 330f`), `Artwork` (`0.58f, 310f`), `Popup` (`0.64f, 440f`), `Favorite` (`0.42f, 430f`), plus `Soft`, `Snappy`, `Bouncy`, and `Critical`.
- **Physical Stretching & Pulling Helpers:**
  - `stretchAxes(stretchAmount, horizontal)`: Deforms `scaleX` and `scaleY` anisotropically while preserving approximate area (`scaleY = 1 / sqrt(scaleX)`), used by the sliding `FloatingNav` lens and `GlassSegmented` indicator.
  - `rubberBand(delta, dimension, resistance)`: Asymptotic iOS-style resistance curve used when over-pulling Now Playing upward, over-pulling Bottom Sheets upward, or scrubbing past the ends of the Now Playing `SeekBar`.
  - `Modifier.pressable`: Uses asymmetric `ButtonPress` on touch-down and `ButtonRelease` on lift with anisotropic squash-and-stretch.
  - **Reduced Motion:** Both the in-app "Reduce motion" toggle and Android's system `ANIMATOR_DURATION_SCALE == 0f` collapse all springs to `snap()`.

---

## 4. Verification Status (Honest Reporting)

- **Codebase & Static Verification Performed:**
  - Inspected all 23 Kotlin source files, `AndroidManifest.xml`, Gradle build scripts, and `.github/workflows/android-build.yml`.
  - Verified all package declarations, imports, Composable signatures, and state flows across all 10 screens.
  - Expanded unit tests in `app/src/test/java/com/aura/player/UtilsTest.kt` to cover `rubberBand`, `stretchAxes`, and `Spr` presets alongside existing utility tests.
- **Not Verified at Runtime in This Environment:**
  - This sandbox environment does not have an Android SDK, `kotlinc`, or an Android emulator/physical device attached, and outbound network access is disabled.
  - Therefore, **runtime execution, the Now Playing crash fix on a live device, and the visual appearance/frame rate of the Liquid Glass and spring animations remain unverified on a physical Android device or emulator**. Build the Debug APK via Android Studio, local Gradle with Android SDK, or the bundled GitHub Actions workflow (`.github/workflows/android-build.yml`).
