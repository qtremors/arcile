# Privacy Policy for Arcile

**Last Updated:** 2026-10-04

Arcile is a personal project built with a privacy-first mindset. This policy explains how the application handles your information.

## 1. No Data Collection

Arcile **does not collect or transmit** personal data, usage statistics, or telemetry to any service. Preferences, favorites, playlists, playback position, and listening history are stored locally to support the app's features. Listening history can be reset in Audio.

## 2. Offline by Design

Arcile is designed to operate entirely offline. The application does not declare the `android.permission.INTERNET` permission, so Arcile cannot directly upload files, telemetry, or activity data over the network.

## 3. No Advertisements or Trackers

The application contains **zero advertisements** and **zero third-party tracking SDKs**.

## 4. Local File Access

Arcile requires the `MANAGE_EXTERNAL_STORAGE` permission (Android 11+) to perform file management operations such as browsing, copying, moving, and deleting files as initiated by you. These operations run locally. Files leave Arcile only through an action you request, such as sharing or opening a file with another installed application.

## 5. OnlyFiles Vaults

OnlyFiles vaults encrypt their contents locally and do not use a cloud service. Optional biometric unlock material is protected by Android Keystore. App-private vaults are removed if Arcile is uninstalled or its app data is cleared; portable vaults remain in their selected storage location.

OnlyFiles has not received an independent security audit. Its format, visible metadata, plaintext handoff boundaries, backup guidance, and recovery limits are documented in the [OnlyFiles format and security guide](https://github.com/qtremors/arcile/blob/main/arcile-app/docs/ONLYFILES_FORMAT_AND_SECURITY.md).

## 6. Source Availability

Arcile's source code is publicly available for inspection. You are welcome to audit it yourself on [GitHub](https://github.com/qtremors/arcile).

## 7. Changes to This Policy

This policy may be updated as new features are introduced. However, the core principles — **privacy, offline-only operations, and zero data collection** — will remain unchanged.

---
[Back to Home](https://qtremors.github.io/arcile/)
