<p align="center">
  <img src="assets/Arcile.png" alt="Arcile Logo" width="120"/>
</p>

<h1 align="center"><a href="https://qtremors.github.io/arcile/">Arcile</a></h1>

<p align="center">
  An advanced, powerful, fast, and smooth Android file manager built with Kotlin and Material Design 3.<br><br>
  <strong><a href="https://qtremors.github.io/arcile/">🌐 Visit the Official Website</a></strong>
</p>

> [!IMPORTANT]
> **Beta Status** 🧪 Arcile is currently in **Beta** and under heavy development. Expect rapid changes, potential bugs, and evolving features.


<p align="center">
<img src="https://img.shields.io/badge/Version-0.4.7-blueviolet" alt="Version">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/Min_SDK-24-34A853?logo=android" alt="Android">
  <img src="https://img.shields.io/badge/License-TSL-red" alt="License">
</p>

> [!NOTE]
> **Personal Project** 🎯 Built to create a fast, clean, and modern file manager for Android — prioritizing smooth UX, Material You theming, and a native Kotlin-first approach.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 📂 **Multi-Volume Support** | Seamlessly manage Internal Storage, SD Cards, and USB OTG devices |
| 🗂️ **Breadcrumb Navigation** | Visual path breadcrumbs with auto-scroll and tap-to-navigate |
| ✅ **Batch Operations** | Multi-select files for copy, cut, move, or permanent delete |
| 🛡️ **Conflict Resolution** | Intelligent handling of file conflicts (skip, overwrite, rename) during copy/move operations |
| 🏠 **Home Dashboard** | Volume-scoped storage summary, category shortcuts, and recent files |
| 🎨 **Material You Theming** | Dynamic wallpaper colors, custom accent colors, light/dark/OLED modes |
| 🌈 **Dynamic Colors** | Powered by **MaterialKolor** for harmonious, accessible custom palettes |
| 🌍 **Localization Base** | 130+ string resources are already extracted, with a smaller set of remaining hardcoded strings tracked in `TASKS.md` |
| 🗑️ **Trash Subsystem** | Safely remove files with metadata-aware restoration |
| ⚙️ **Settings & About** | Theme customization and comprehensive app information |

---

## 🚀 Quick Start

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Android Studio | Latest (Ladybug+) | [developer.android.com](https://developer.android.com/studio) |
| JDK | 11+ | Bundled with Android Studio |
| Android SDK | API 36 | Via SDK Manager |

### Setup

```bash
# Clone the repository
git clone https://github.com/qtremors/arcile.git

# Open in Android Studio
# File → Open → select the arcile-app directory
```

Or build from the command line (run from inside `arcile-app/`):

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
adb install app/build/outputs/apk/debug/Arcile-dev.qtremors.arcile.debug-0.4.7-debug.apk
```

### Release Signing

Release builds are signed using credentials stored in `local.properties` (not committed). To configure signing:

1. Generate a keystore (or use an existing one):
   ```bash
   keytool -genkeypair -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```
2. Add the following to `arcile-app/local.properties`:
   ```properties
   signing.storeFile=/absolute/path/to/my-release-key.jks
   signing.storePassword=your_store_password
   signing.keyAlias=your_key_alias
   signing.keyPassword=your_key_password
   ```
3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

> **Note:** The app currently requires **All Files Access** permission on Android 11+ and **READ/WRITE_EXTERNAL_STORAGE** on Android 9 and below for full file-system access. **Android 10 (API 29) remains unsupported** because the app's `java.io.File`-based architecture does not work reliably under Scoped Storage.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.2.10 |
| **UI Framework** | Jetpack Compose with Material 3 |
| **Architecture** | MVVM (Feature-Scoped ViewModels + StateFlow) with Hilt DI |
| **Navigation** | Navigation Compose |
| **Async** | Kotlin Coroutines |
| **Build System** | Gradle (Kotlin DSL) with Version Catalogs |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |

---

## 📁 Project Structure

```
arcile/
├── arcile-app/                          # Android project root
│   ├── app/src/main/
│   │   ├── java/dev/qtremors/arcile/
│   │   │   ├── ArcileApp.kt             # Application class (Coil image loader, Hilt app)
│   │   │   ├── MainActivity.kt          # Entry point, permissions, nav shell
│   │   │   ├── data/                    # Repository Implementations
│   │   │   ├── di/                      # Dependency Injection (Hilt)
│   │   │   ├── domain/                  # Core Models & Repository Interfaces
│   │   │   ├── image/                   # Coil custom fetchers
│   │   │   ├── navigation/              # Route string constants
│   │   │   ├── presentation/            # Feature-Scoped ViewModels & UI
│   │   │   │   ├── browser/
│   │   │   │   ├── home/
│   │   │   │   ├── recentfiles/
│   │   │   │   ├── settings/
│   │   │   │   ├── trash/
│   │   │   │   └── ui/                  # Compose UI Screens & Components
│   │   │   ├── ui/theme/                # Theme, colors, typography, shapes
│   │   │   └── utils/                   # Formatting & color utilities
│   │   └── res/                         # Android resources
│   ├── build.gradle.kts
│   └── gradle/libs.versions.toml        # Version catalog
├── DEVELOPMENT.md                       # Developer documentation
├── CHANGELOG.md                         # Version history
├── TASKS.md                             # Audit findings and planned work
├── LICENSE.md
└── README.md
```

---

## 🧪 Testing

Run these commands from inside the `arcile-app/` directory:

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew :app:connectedDebugAndroidTest
```

> **Note:** The project already includes a layered JVM test suite for domain logic, repository behavior, ViewModels, and a first batch of Robolectric-backed Compose component tests. Instrumented UI coverage is still minimal and currently limited to the generated example test.

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Developer guide, architecture, and conventions |
| [CHANGELOG.md](CHANGELOG.md) | Version history and release notes |
| [TASKS.md](TASKS.md) | Audit findings, planned features, and known issues |
| [LICENSE.md](LICENSE.md) | License terms and attribution |

---

## 📄 License

**Tremors Source License (TSL)** - Source-available license allowing viewing, forking, and derivative works with **mandatory attribution**. Commercial use requires written permission.

Web Version: [github.com/qtremors/license](https://github.com/qtremors/license)

See [LICENSE.md](LICENSE.md) for full terms.

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/qtremors">Tremors</a>
</p>
