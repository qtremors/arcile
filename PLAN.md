# Arcile - Storage Classification Refinement Plan

This document tracks the remaining **Correctness, Performance, and Security** items for the Storage Classification system that were identified as "not properly done" during the 2026-03-20 audit.

---

## 🏗️ Remaining Technical Debt & Refinements

The core features are implemented, but the following areas require refinement to be considered "production-ready."

### 1. Robustness & Correctness
- [ ] **Cache Key Sanitization**: `LocalFileRepository` uses raw `volumeId` for cache keys. These need to be sanitized to prevent invalid filename issues on certain systems.
- [ ] **Active Roots Thread Safety**: `activeStorageRoots` is a mutable `var` assessed across different dispatchers. Needs thread-safe access (AtomicReference or Mutex).
- [ ] **Dead Code Cleanup**: Remove redundant top-level `mergeStorageClassifications` in `LocalFileRepository.kt`.

### 2. Performance & Efficiency
- [ ] **Redundant Volume Discovery**: `discoverPlatformVolumes()` is called 3+ times during initialization/refresh. Implement results caching at the repository level.
- [ ] **Search Safety**: Add `maxDepth` and result limits to `searchFiles()` filesystem walk to prevent UI hangs on deep storage structures (e.g., `Android/data`).
- [ ] **Home Dashboard Fan-out**: `HomeViewModel` triggers multiple parallel repository calls on every silent volume refresh. Implement debouncing or differential refreshes.

### 3. Security
- [ ] **Broadcast Security**: Update `receiver` registration in `observeStorageVolumes()` to use `RECEIVER_NOT_EXPORTED` for API 34+ compatibility.

---