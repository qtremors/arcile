# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.6.5
> **Last Updated:** 2026-05-12

---

## Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **Storage Health Diagnostics**: Basic S.M.A.R.T. status checks, disk health metrics, and repair/trim suggestions for mounted volumes.

### File Operations & Automation
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Browsing & Organization
- **Rich Media Previews**: Continue expanding custom Coil `Fetcher` components for rich file previews.
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with tabbed or segmented navigation.
- **Storage Analyzer ("Filelight" view)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.

### Tools & Development
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing purposes.
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive flower petal UI.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
