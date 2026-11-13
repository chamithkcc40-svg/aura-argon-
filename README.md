# AURA — Liquid Glass Music Player for Android

AURA is a native Android music player (Kotlin + Jetpack Compose + Media3) featuring a multi-layer **Liquid Glass** optical material engine, real hardware-accelerated backdrop blur (Android 12+), adaptive Dark & Crystal Light themes, artwork-derived ambient lighting, and a centralized physical spring motion system.

It plays **your real music library** — no demo or fake data.

| Area | Implementation |
|---|---|
| Playback | Media3 `ExoPlayer` inside a foreground `MediaSessionService` (notification, lock screen, Bluetooth/headset controls), `MediaController` in UI |
| Library | `MediaStore` scan **and** SAF folder access (persisted tree permissions) |
| Storage | Room: tracks, favorites, playlists + ordered entries, play history, recent searches |
| Lists | Paging 3 (`Pager` + Room `PagingSource`) for the Songs list, lazy lists with stable keys elsewhere |
| Artwork | Custom Coil `Fetcher` (`loadThumbnail` / embedded picture) + `Palette` → ambient colours |
| Liquid Glass | Preserved `GraphicsLayer` + `RenderEffect` backdrop blur (`TileMode.Clamp` + saturation filter) enhanced with `GlassLevel` hierarchy (`Subtle`, `Regular`, `Strong`, `Lens`), inner refraction bevel ring, upper/lower lens crescents, chromatic caustics, directional specular borders, and floating elevation shadows |
| Spring Motion | Centralized `Spr` vocabulary (`Navigation`, `ButtonPress`, `ButtonRelease`, `Dropdown`, `BottomSheet`, `MiniPlayer`, `Artwork`, `Popup`, `Favorite`) with anisotropic squash-and-stretch (`stretchAxes`) and asymptotic `rubberBand` drag physics |

## Screens
Home · Library (Albums / Artists / Songs / Playlists / Favorites) · Search · Playlists · Album / Artist / Playlist detail · Mini Player · Now Playing (+ Queue sheet) · Settings (System / Dark / Light theme, Accent palettes, Glass quality, Ambience, Reduced motion, Haptics, SAF folders).

## Build
See [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md) or `AURA-APK-Build-Guide.md`. Short version:

```bash
./gradlew testDebugUnitTest assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Or push to GitHub: `.github/workflows/android-build.yml` runs unit tests, builds the debug APK, validates package metadata with `aapt2`, and uploads the `AURA-debug-apk` artifact.

Requirements: JDK 17, Android SDK platform 35. `minSdk` 26, `targetSdk` 35, package `com.aura.player`.

## Docs
- [`docs/UI_DESIGN.md`](docs/UI_DESIGN.md) — Liquid Glass optical layers, reference image mapping, and spring motion architecture
- [`docs/ENGINEERING_LOG.md`](docs/ENGINEERING_LOG.md) — Root-cause crash diagnosis & fix, blur preservation, verification status, and limitations
- [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md) — Local and GitHub Actions CI builds
