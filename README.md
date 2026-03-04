<p align="center">
  <img src="assets/Arcile.png" alt="Arcile Logo" width="120"/>
</p>

<h1 align="center">Arcile</h1>

<p align="center">
  An advanced, powerful, fast, and smooth Android file manager built with Kotlin and Material Design 3.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin" alt="Kotlin">
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
| 📂 **File Browsing** | Navigate internal storage with sorted directory listings (folders first, alphabetical) |
| 🗂️ **Breadcrumb Navigation** | Visual path breadcrumbs with auto-scroll and tap-to-navigate |
| ✅ **Multi-Select** | Long-press to select files, batch operations with contextual top bar |
| 📁 **Create Folders** | Create new directories via FAB or overflow menu |
| 🗑️ **Delete Files** | Delete selected files and directories (recursive) |
| 🏠 **Home Dashboard** | Storage summary card, category shortcuts, folder quick-access, and recent files |
| 🎨 **Material You Theming** | Dynamic wallpaper colors, custom accent colors, light/dark/OLED modes |
| 🔧 **Tools Screen** | Planned utilities: FTP Server, Storage Analyzer, Junk Cleaner, and more |
| ⚙️ **Settings** | Theme mode and accent color selector with bottom sheet pickers |

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
# Clone and navigate
git clone https://github.com/qtremors/arcile.git
cd arcile/arcile-app

# Open in Android Studio
# File → Open → select the arcile-app directory

# Or build via CLI
./gradlew assembleDebug
```

Install the APK on a connected device or emulator:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** The app requires **All Files Access** permission (Android 11+) or **READ/WRITE_EXTERNAL_STORAGE** (Android 10 and below) to function.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.2 |
| **UI Framework** | Jetpack Compose with Material 3 |
| **Architecture** | MVVM (ViewModel + StateFlow) |
| **Navigation** | Navigation Compose |
| **Async** | Kotlin Coroutines |
| **Build System** | Gradle (Kotlin DSL) with Version Catalogs |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |

---

## 📁 Project Structure

```
arcile/
├── arcile-app/                   # Android project root
│   ├── app/src/main/
│   │   ├── java/dev/qtremors/arcile/
│   │   │   ├── MainActivity.kt           # Entry point, permissions, nav shell
│   │   │   ├── data/                      # Data layer
│   │   │   │   └── LocalFileRepository.kt # File system operations
│   │   │   ├── domain/                    # Domain layer
│   │   │   │   ├── FileModel.kt           # File data model
│   │   │   │   └── FileRepository.kt      # Repository interface + StorageInfo
│   │   │   ├── presentation/              # Presentation layer
│   │   │   │   ├── FileManagerViewModel.kt # Shared ViewModel + state
│   │   │   │   └── ui/                    # Composable screens
│   │   │   │       ├── HomeScreen.kt
│   │   │   │       ├── FileManagerScreen.kt
│   │   │   │       ├── SettingsScreen.kt
│   │   │   │       ├── ToolsScreen.kt
│   │   │   │       └── components/        # Reusable UI components
│   │   │   └── ui/theme/                  # Theme configuration
│   │   └── res/                           # Android resources
│   ├── build.gradle.kts                   # App-level build config
│   └── gradle/libs.versions.toml          # Version catalog
├── DEVELOPMENT.md                # Developer documentation
├── CHANGELOG.md                  # Version history
├── TASKS.md                      # Audit findings and planned work
├── LICENSE.md                    # License terms
└── README.md
```

---

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

> **Note:** Test infrastructure is currently minimal — see [TASKS.md](TASKS.md) section H4.

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
