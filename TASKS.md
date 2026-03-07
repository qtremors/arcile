# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.2.3
> **Last Updated:** 2026-03-07

---

### Medium Priority

- [ ] [Refactor] ViewModel directly instantiates `LocalFileRepository` — no DI (`FileManagerViewModel.kt:29`)
  - Hardcoded concrete implementation makes unit testing impossible without real filesystem.
- [ ] [Refactor] `FileModel` holds a `java.io.File` reference — breaks domain separation (`FileModel.kt:6`)
  - Leaks data layer into domain; makes `FileModel` non-serializable/non-parcelable.
- [ ] [Refactor] Single ViewModel for all screens — poor separation of concerns (`FileManagerViewModel.kt`)
  - Manages home, file browser, and file operations. Should be split.

---

### Comprehensive Audit Findings

#### A. Correctness & Reliability
- [ ] [Bug] `FileManagerViewModel` state loss on process death.
  - **Problem:** ViewModel state (`currentPath`, `isHomeScreen`) is not persisted across process deaths.
  - **Location:** `FileManagerViewModel.kt:18` (`FileManagerState`)
  - **Impact:** User loses their navigation position if the app is killed in the background.
  - **Fix:** Integrate `SavedStateHandle` to persist navigation state variables.

#### D. Architecture & Design Quality
- [ ] [Architecture] Hardcoded Android framework dependencies in ViewModel.
  - **Problem:** `FileManagerViewModel` initializes `storageRootPath` via `Environment.getExternalStorageDirectory()`.
  - **Location:** `FileManagerViewModel.kt:39`
  - **Impact:** Makes the ViewModel impossible to unit test without Robolectric.
  - **Fix:** Inject `storageRootPath` via the constructor or retrieve it asynchronously via `FileRepository`.

#### F. UI / UX & Accessibility
- [ ] [UX] Missing adaptive layouts for large screens.
  - **Problem:** Root composables force narrow vertical lists regardless of window width.
  - **Location:** `HomeScreen.kt`, `FileManagerScreen.kt`
  - **Impact:** Poor utilization of screen real estate on tablets and foldables.
  - **Fix:** Implement `WindowSizeClass` checks and use `LazyVerticalGrid` or standard row/column dual-pane layouts for expanded widths.

#### G. Material Design 3 Expressive Implementation
- [ ] [Design] Incorrect manual tonal palette generation for custom seeds.
  - **Problem:** `generateColorSchemeFromSeed` fakes tonal palettes by applying alpha transparency.
  - **Location:** `Theme.kt:53-63`
  - **Impact:** Seed color themes lack accessibility mapping, causing poor text contrast.
  - **Fix:** Use the Material Color Utilities library (`m3color` / HCT) to generate a mathematically correct `ColorScheme`.

---

### Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

- SAF (Storage Access Framework) integration for better Android 11+ compatibility
- File properties dialog (size, permissions, creation date)
- Compress/extract ZIP support
- Multi-window / split-screen support
- Root access support for power users
- SD card and USB OTG storage support
- **"OnlyFiles" Encrypted Vault**: Implement a secure, encrypted vault for storing sensitive files/folders.
  - Requires PIN/Password and Biometric authentication.
  - Users can send files directly from storage to the vault.
  - Requires initial setup (specifying a destination for encrypted storage).
  - Support for file operations and creation directly within the vault.
- **Customizable Quick Access**: Replace hard-coded folders on the Home screen with user-selectable folders for quick access.
- **Enhanced Category Browsing**: Opening a category displays all related folders and files, featuring navigation via tabs or a bottom app bar for all the related folders.
- **File/Folder Properties**: Add an option to view detailed properties directly.
- **Contextual Search**:
  - *Home Page*: Search across categories, quick access folders, and utility tools.
  - *Browse Page*: Search the current directory and all subdirectories.
  - *Filters*: Include filter chips to narrow down search results by type, date, size, etc.
- **Starred Files**: Add a "Starred" files section within the Quick Access area.
