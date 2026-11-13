# AURA — APK Build & Installation Guide

## 1. Local Build (Android Studio or Command Line)

### Prerequisites
- **JDK 17** (Temurin / OpenJDK 17)
- **Android SDK** with `platforms;android-35` and `build-tools;34.0.0`

### Command-Line Build
```bash
# Extract AURA-Liquid-Glass-Fixed.zip and enter the project directory:
cd AURA

# Set ANDROID_HOME (or create local.properties with sdk.dir=/path/to/Android/Sdk):
export ANDROID_HOME=$HOME/Android/Sdk

# If gradle/wrapper/gradle-wrapper.jar is not yet initialized locally, generate it with Gradle 8.9:
# gradle wrapper --gradle-version 8.9

# Run JVM unit tests:
./gradlew --no-daemon --stacktrace testDebugUnitTest

# Assemble the Debug APK:
./gradlew --no-daemon --stacktrace assembleDebug
```
The compiled Debug APK is written to:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 2. GitHub Actions Cloud Build (Recommended if Building Without Local Android SDK)

The project includes `.github/workflows/android-build.yml`, which automatically provisions **JDK 17**, **Android SDK 35**, and **Gradle 8.9**, ensures the Gradle Wrapper JAR is present, runs `./gradlew testDebugUnitTest`, builds `./gradlew assembleDebug`, verifies the APK with `aapt2`, and uploads the **`AURA-debug-apk`** artifact.

### Step-by-Step GitHub Upload & Build
1. **Extract `AURA-Liquid-Glass-Fixed.zip`** on your computer. Inside is the `AURA/` project root.
2. **Show hidden files** in your file manager (`Cmd + Shift + .` on macOS, `Ctrl + H` on Linux, or *View ▸ Hidden items* on Windows) so `.github/workflows/android-build.yml` and `.gitignore` are visible.
3. Create or open a GitHub repository on `github.com` with default branch **`main`**.
4. Click **Add file ▸ Upload files** and drag **all files and folders inside `AURA/`** (`app/`, `gradle/`, `docs/`, `.github/`, `gradlew`, `gradlew.bat`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `README.md`, `.gitignore`) directly into the repository root, then commit to `main`.
   - Alternatively, push via Git CLI:
     ```bash
     cd AURA
     git init -b main
     git add -A
     git commit -m "AURA Liquid Glass + crash fix + spring motion"
     git remote add origin https://github.com/<your-username>/<your-repo>.git
     git push -u origin main
     ```
5. Open the **Actions** tab in your GitHub repository and select the **Android Build** workflow run.
6. Once the build completes with a green checkmark, scroll to **Artifacts** at the bottom of the run summary and download **`AURA-debug-apk`**.
7. Unzip `AURA-debug-apk.zip` to get `app-debug.apk`, transfer it to your Android phone, and install it.

---

## 3. Runtime Verification Checklist on Device

After installing `app-debug.apk` on an Android phone (Android 12+ / API 31+ recommended for hardware `RenderEffect` backdrop blur; API 26–30 uses the multi-layer frosted glass fallback):
1. **Grant Audio Permission** when prompted (or add a folder via **Settings ▸ Library ▸ Add music folder**).
2. **Verify Now Playing Crash Fix Across All 10 Scenarios:**
   - Open Now Playing while a song is playing (tap or drag up the Mini Player).
   - Open Now Playing while playback is paused.
   - Open Now Playing immediately after selecting a track.
   - Open Now Playing on a track without embedded artwork.
   - Open the Queue sheet (`Up next`) and test reordering/jumping tracks.
   - Collapse Now Playing, switch tabs (`Home`, `Library`, `Search`, `Playlists`, `Settings`), and reopen Now Playing.
   - Skip next/previous via buttons and via horizontal artwork swipe.
   - Rotate the device / resize window and verify the square artwork layout and state retention.
3. **Verify Liquid Glass & Spring Motion:**
   - Observe the 3D Liquid Glass active tab lens in the bottom navigation bar stretching and settling as you switch tabs.
   - Test dropdown pills in **Settings ▸ Appearance** (`Theme`, `Accent`, `Glass quality`) and the sort menu in **Library ▸ Songs**.
   - Test toggling between **Dark** and **Light** themes in **Settings ▸ Appearance ▸ Theme** (or via Android's system Dark theme tile when set to `System`).
