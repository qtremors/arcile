# Arcile - Plans

## Comprehensive Test Suite Implementation Plan

### Status Snapshot (Updated 2026-03-20)

#### Completed So Far
- **Phase 1 foundation work:** Added JVM tests for `FormatUtils`, `CategoryColors`, `FileModel`, and `StorageInfo`.
- **Phase 1 hardening:** Fixed an edge-case in `formatFileSize` so near-boundary values no longer render awkward unit transitions like `1024.0 KB`.
- **Phase 2 test harness:** Added a reusable `MainDispatcherRule` for coroutine/ViewModel testing.
- **Phase 2 ViewModel coverage:** Added JVM tests for `HomeViewModel`, `BrowserViewModel`, `RecentFilesViewModel`, and `TrashViewModel`.
- **Phase 2 delete-flow coverage:** Covered mixed delete policy handling, trash/permanent-delete branching, and native-confirmation state handoff in browser/recent/trash flows.
- **Browser testability refactor:** Introduced a `BrowserPreferencesStore` interface and wired it through DI so browser state logic can be unit tested cleanly.
- **Browser path robustness:** Hardened browser volume-path matching to support Android-style `/storage/...` paths consistently.
- **Phase 3 component harness:** Enabled Robolectric-backed JVM Compose component tests with Android-resource support and a shared `ArcileTestTheme` wrapper.
- **Phase 3 first component batch:** Added Compose tests for `DeleteConfirmationDialog`, `ArcileTopBar`, and `EmptyState`.
- **Current JVM suite status:** `:app:testDebugUnitTest` passes with the current test set.
- **Priority gap refinement from review:** the biggest remaining risks are navigation/state-restoration coverage, `FileManagerScreen` interaction coverage, and missing setup for future Flow/mock-heavy tests.

#### Current Coverage Additions Since This Plan Was Written
**Domain Layer:**
- `DeletePolicy` (`DeletePolicyTest.kt`)
- `FileModel` (`FileModelTest.kt`)
- `StorageInfo` and `StorageKind` policy behavior (`StorageInfoTest.kt`)

**Utilities:**
- `FormatUtils` (`FormatUtilsTest.kt`)
- `CategoryColors` (`CategoryColorsTest.kt`)

**Presentation Layer (Logic / State Management):**
- `FilePresentation` (`FilePresentationTest.kt`)
- `StorageScopeViewModel` (`StorageScopeViewModelTest.kt`)
- `HomeViewModel` (`HomeViewModelTest.kt`)
- `BrowserViewModel` (`BrowserViewModelTest.kt`)
- `RecentFilesViewModel` (`RecentFilesViewModelTest.kt`)
- `TrashViewModel` (`TrashViewModelTest.kt`)

**UI Components (Compose / Robolectric):**
- `DeleteConfirmationDialog` (`DeleteConfirmationDialogTest.kt`)
- `ArcileTopBar` (`ArcileTopBarTest.kt`)
- `EmptyState` (`EmptyStateTest.kt`)

#### In Progress Now
- **Phase 3 expansion:** moving from the first Compose component batch into additional dialogs, controls, and filter/list components, with `FileManagerScreen` and navigation flows now the highest priority.

---

### 1. Current Test Coverage Analysis

#### ✅ What is Currently Tested
The current test coverage now includes core business logic, filtering, storage classification, several domain models/utilities, the main ViewModel state machines, and the first Compose component batch.

**Domain Layer:**
*   `DeletePolicy` (`DeletePolicyTest.kt`)
*   `FileModel` (`FileModelTest.kt`)
*   `StorageInfo` / `StorageKind` policies (`StorageInfoTest.kt`)

**Data Layer (Business Rules & Routing):**
*   Storage Classification logic (`CategoryScopeMatchingTest.kt`, `ReclassificationBehaviorTest.kt`, `StorageClassificationMergeTest.kt`)
*   Filtering rules (`StorageFilteringTest.kt`)
*   Trash routing (`TrashRoutingTest.kt`)
*   Local File rules (`LocalFileOperationsTest.kt`)

**Utilities:**
*   `FormatUtils` (`FormatUtilsTest.kt`)
*   `CategoryColors` (`CategoryColorsTest.kt`)

**Presentation Layer (Logic & State Management):**
*   `FilePresentation` mapping (`FilePresentationTest.kt`)
*   `StorageScopeViewModel` logic (`StorageScopeViewModelTest.kt`)
*   `HomeViewModel` (`HomeViewModelTest.kt`)
*   `BrowserViewModel` (`BrowserViewModelTest.kt`)
*   `RecentFilesViewModel` (`RecentFilesViewModelTest.kt`)
*   `TrashViewModel` (`TrashViewModelTest.kt`)

**UI Layer (Compose Components):**
*   `DeleteConfirmationDialog` (`DeleteConfirmationDialogTest.kt`)
*   `ArcileTopBar` (`ArcileTopBarTest.kt`)
*   `EmptyState` (`EmptyStateTest.kt`)

---

#### ❌ What is NOT Tested (Remaining Gaps)
Formal coverage is still missing primarily in repository implementations, Android-dependent utilities, navigation/state restoration, screen-level Compose flows, and most reusable UI components.

**Data Layer (Implementations):**
*   `BrowserPreferencesRepository` (real DataStore interactions)
*   `LocalFileRepository` (broader Android `MediaStore`/File system interactions)
*   `StorageClassificationRepository`

**Domain Layer (Models & Use Cases):**
*   `BrowserPreferences`, `ConflictModels`, `FileCategories`, `SearchFilters`, `StorageBrowserLocation`, `StorageScope`, `TrashMetadata`
*   `FileRepository` interface contracts

**Presentation Layer (Remaining State / Logic Gaps):**
*   `ClipboardState`, presentation `SearchFilters`
*   `BrowserViewModel` restore/init/back-stack branches (`restoreLocationFromState`, argument initialization, refresh path selection, multi-step navigate-back behavior)
*   Additional edge branches in delete, paste, rename, and refresh flows where applicable

**UI Layer (Jetpack Compose - remaining gaps):**
*   `AboutScreen`, `AppNavigationGraph`, `ArcileAppShell`
*   `FileManagerScreen` as the highest-risk untested screen due to search, back handling, pull-to-refresh, conflict dialogs, delete dialogs, snackbars, and native-confirmation orchestration
*   `HomeScreen`, `RecentFilesScreen`, `SettingsScreen`
*   `StorageDashboardScreen`, `StorageManagementScreen`, `ToolsScreen`, `TrashScreen`
*   Most granular Compose `components/*` beyond the first tested batch

**Utilities & Image Fetchers:**
*   `ShareHelper`
*   `ApkIconFetcher`, `AudioAlbumArtFetcher`

**Navigation:**
*   `AppRoutes` (route generation and parameter parsing)
*   `AppNavigationGraph` route transitions, back-stack behavior, and screen wiring

---

### 2. Implementation Strategy for 100% Coverage

To ensure "every single little UI/UX interaction" is covered, we will implement a robust testing pyramid:

1.  **Unit Tests (Fast, Isolated):** For Domain models, Utility functions, and Data Layer mappings.
2.  **ViewModel/Integration Tests:** Testing Coroutine flows, StateFlow emissions, and intention handling using `Turbine` and `kotlinx-coroutines-test`.
3.  **UI Component Tests (Compose UI Tests):** Testing individual Compose elements for proper rendering, state changes, and accessibility semantics.
4.  **UI Integration/Navigation Tests:** Testing screen-to-screen navigation and complex user flows using `ComposeTestRule` and Robolectric/Espresso.

---

### 3. Phased Implementation Plan

#### Phase 1: Core Utilities, Models, and Data Repositories
*   **Status:** Mostly completed for utility/model foundations; repository implementation coverage still pending.
*   **Completed:**
    *   Added tests for `FormatUtils`.
    *   Added tests for `CategoryColors`.
    *   Added tests validating `FileModel` and `StorageInfo` behaviors.
*   **Remaining:**
    *   Add tests for `ShareHelper`.
    *   Add tests for image fetchers (`ApkIconFetcher`, `AudioAlbumArtFetcher`) using mocked `Context` or Robolectric.
    *   Implement tests for `BrowserPreferencesRepository` (using a test DataStore instance).
    *   Add direct tests for other remaining repository implementations and Android-dependent helpers once the component/screen backlog is under control.

#### Phase 2: State Management & ViewModels
*   **Status:** Substantially completed, but restore/navigation branches still leave meaningful risk.
*   **Completed:**
    *   Setup `MainDispatcherRule` for coroutine testing.
    *   Tested `HomeViewModel` loading, search, classification prompt, and failure rollback states.
    *   Tested `BrowserViewModel` navigation, search scoping, clipboard interactions, conflict flows, sort persistence, validation, and delete/native-confirmation branches.
    *   Tested `RecentFilesViewModel` search, refresh, delete-policy, and native-confirmation branches.
    *   Tested `TrashViewModel` restore, destination-picker, search, stale-selection cleanup, and native-confirmation state handling.
*   **Remaining:**
    *   Add explicit coverage for `BrowserViewModel` saved-state restoration, route initialization, and multi-step back-stack transitions.
    *   Fill any remaining edge branches in browser/recent/trash delete, paste, rename, and refresh flows as needed.
    *   Add `Turbine`-based flow assertions once the dependency is wired in so emission-level behavior can be asserted directly.

#### Phase 3: UI Component & UX Interaction Tests (Jetpack Compose)
*   **Status:** Started and active.
*   **Goal:** Verify visual states and UX interactions locally.
*   **Completed:**
    *   Enabled JVM Compose component tests with Robolectric.
    *   Added first component tests for `DeleteConfirmationDialog`, `ArcileTopBar`, and `EmptyState`.
*   **Next Tasks:**
    *   Prioritize `FileManagerScreen` coverage before broadening to lower-risk leaf components.
    *   Create tests for additional high-value custom components in `components/*`, especially dialogs, list controls, search/filter UI, and settings selectors.
    *   Verify click listeners, long-press actions, and scroll behaviors where applicable.
    *   Ensure semantic labels are present and correct for accessibility testing.

#### Phase 4: Screen & Navigation Flows (E2E Integration)
*   **Status:** Not started and now confirmed as the largest remaining confidence gap.
*   **Goal:** Test complete user journeys through the app.
*   **Tasks:**
    *   Test `AppNavigationGraph`: Verify routes transition correctly (e.g., Home -> Browser, Browser -> Settings).
    *   Test process recreation / saved-state restore journeys so browser/category/root state survives correctly.
    *   Test complete flows: e.g. Open App -> Navigate to Images -> Select File -> Copy -> Navigate to Internal Storage -> Paste.
    *   Test permissions handling UI states.
    *   Use Robolectric + Compose UI Test for fast, JVM-based UI integration tests where practical.

### 4. Required Testing Setup (build.gradle.kts)
**Currently integrated:**
*   `org.robolectric:robolectric`
*   `androidx.compose.ui:ui-test-junit4`
*   `androidx.compose.ui:ui-test-manifest`
*   `org.jetbrains.kotlinx:kotlinx-coroutines-test`

**Still missing from the intended setup:**
*   `io.mockk:mockk` (for mocking dependencies)
*   `app.cash.turbine:turbine` (for Flow testing)

The plan previously described the full intended toolset as if it were already present. Keep this section aligned with the real Gradle state so future coverage work is not planned on top of unavailable test libraries.
