package dev.qtremors.arcile.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.emptyPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.data.activityLogDataStore
import dev.qtremors.arcile.core.storage.data.browserDataStore
import dev.qtremors.arcile.core.storage.data.classificationDataStore
import dev.qtremors.arcile.core.storage.data.onboardingDataStore
import dev.qtremors.arcile.core.storage.data.quickAccessDataStore
import dev.qtremors.arcile.core.storage.data.storageCleanerDataStore
import dev.qtremors.arcile.core.storage.data.utilityDataStore
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupFailure
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupGateway
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItem
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupPreview
import dev.qtremors.arcile.core.ui.theme.AccentColor
import dev.qtremors.arcile.core.ui.theme.ThemeMode
import dev.qtremors.arcile.core.ui.theme.ThemePreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.dataStore as themeDataStore
import dev.qtremors.arcile.core.vault.data.vaultSecurityDataStore
import dev.qtremors.arcile.presentation.ui.browserTabsDataStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PreferencesBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val themePreferences: ThemePreferences
) : PreferencesBackupGateway {
    constructor(context: Context) : this(context, ThemePreferences(context))

    internal var beforeRestoreCommit: (storeName: String) -> Unit = {}

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override suspend fun exportTo(uri: Uri): Result<PreferencesBackupOperationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val failures = mutableListOf<PreferencesBackupFailure>()
            var totalBytes = 0L
            val themeState = themePreferences.themeState.first().toBackupState()
            val stores = livePreferenceStores().mapNotNull { (storeName, store) ->
                runCatching {
                    val bytes = serializePreferences(store.data.first())
                    require(bytes.size <= MAX_STORE_BYTES) { "$storeName is too large to export" }
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_TOTAL_STORE_BYTES) { "Settings data is too large to export" }
                    PreferencesBackupStore(
                        name = storeName,
                        encodedBytes = Base64.encodeToString(bytes, Base64.NO_WRAP),
                        decodedSizeBytes = bytes.size,
                        sha256 = bytes.sha256()
                    )
                }.getOrElse { error ->
                    failures += PreferencesBackupFailure(
                        storeName.displayName(),
                        error.message ?: context.getString(R.string.settings_backup_store_read_failed)
                    )
                    null
                }
            }
            val payload = PreferencesBackupPayload(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                createdAtMillis = System.currentTimeMillis(),
                packageName = context.packageName,
                themeState = themeState,
                stores = stores
            )
            require(payload.stores.isNotEmpty() || payload.themeState != null) {
                "No settings are available to export yet"
            }
            val encodedPayload = json.encodeToString(payload).toByteArray()
            require(encodedPayload.size <= MAX_ENVELOPE_BYTES) { "Settings backup is too large" }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(encodedPayload)
            } ?: error("Unable to open backup destination")
            PreferencesBackupOperationResult(items = payload.toExportItems(), failures = failures)
        }
    }

    override suspend fun preview(uri: Uri): Result<PreferencesBackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = readPayload(uri)
            validatePayload(payload)
            PreferencesBackupPreview(
                createdAtMillis = payload.createdAtMillis,
                items = preferenceStoreNames.map { storeName ->
                    PreferencesBackupItem(
                        id = storeName,
                        label = storeName.displayName(),
                        status = if (payload.hasStore(storeName)) {
                            PreferencesBackupItemStatus.WillRestore
                        } else {
                            PreferencesBackupItemStatus.WillReset
                        }
                    )
                }
            )
        }
    }

    override suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = readPayload(uri)
            validatePayload(payload)
            val stores = livePreferenceStores()
            val desired = decodeDesiredPreferences(payload)
            val originals = stores.mapValues { (_, store) -> store.data.first() }
            val committed = mutableListOf<String>()

            try {
                preferenceStoreNames.forEach { storeName ->
                    beforeRestoreCommit(storeName)
                    stores.getValue(storeName).updateData { desired.getValue(storeName) }
                    committed += storeName
                }
            } catch (restoreError: Throwable) {
                withContext(NonCancellable) {
                    committed.asReversed().forEach { storeName ->
                        runCatching {
                            stores.getValue(storeName).updateData { originals.getValue(storeName) }
                        }.onFailure(restoreError::addSuppressed)
                    }
                }
                throw restoreError
            }

            PreferencesBackupOperationResult(
                items = preferenceStoreNames.map { storeName ->
                    PreferencesBackupItem(
                        id = storeName,
                        label = storeName.displayName(),
                        status = if (payload.hasStore(storeName)) {
                            PreferencesBackupItemStatus.Restored
                        } else {
                            PreferencesBackupItemStatus.Reset
                        }
                    )
                },
                failures = emptyList()
            )
        }
    }

    private fun readPayload(uri: Uri): PreferencesBackupPayload {
        val encoded = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBounded(MAX_ENVELOPE_BYTES).decodeToString()
        } ?: error("Unable to open backup file")
        return json.decodeFromString(encoded)
    }

    private fun validatePayload(payload: PreferencesBackupPayload) {
        require(payload.schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version"
        }
        require(payload.packageName == context.packageName) { "Backup belongs to a different app" }
        require(payload.stores.size <= preferenceStoreNames.size) { "Backup contains too many stores" }
        val names = payload.stores.map(PreferencesBackupStore::name)
        require(names.size == names.distinct().size) { "Backup contains duplicate stores" }
        require(names.all { it in preferenceStoreNames }) { "Backup contains an unknown store" }
        var declaredTotalBytes = 0L
        payload.stores.forEach { store ->
            require(store.encodedBytes.length <= MAX_ENCODED_STORE_CHARS) {
                "${store.name} is too large"
            }
            if (payload.schemaVersion >= INTEGRITY_SCHEMA_VERSION) {
                require(store.decodedSizeBytes != null && store.sha256 != null) {
                    "${store.name} is missing integrity metadata"
                }
                require(store.decodedSizeBytes in 0..MAX_STORE_BYTES) {
                    "${store.name} is too large"
                }
                declaredTotalBytes += store.decodedSizeBytes
                require(declaredTotalBytes <= MAX_TOTAL_STORE_BYTES) {
                    "Settings payload is too large"
                }
            }
        }
    }

    private suspend fun decodeDesiredPreferences(payload: PreferencesBackupPayload): Map<String, Preferences> {
        var totalDecodedBytes = 0L
        val decoded = payload.stores.associate { store ->
            val bytes = try {
                Base64.decode(store.encodedBytes, Base64.NO_WRAP)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("${store.name} is not valid Base64", error)
            }
            require(bytes.size <= MAX_STORE_BYTES) { "${store.name} is too large" }
            totalDecodedBytes += bytes.size
            require(totalDecodedBytes <= MAX_TOTAL_STORE_BYTES) { "Settings payload is too large" }
            store.decodedSizeBytes?.let { expected ->
                require(bytes.size == expected) { "${store.name} has an invalid length" }
            }
            store.sha256?.let { expected ->
                require(bytes.sha256().equals(expected, ignoreCase = true)) {
                    "${store.name} failed its integrity check"
                }
            }
            store.name to PreferencesFileSerializer.readFrom(ByteArrayInputStream(bytes))
        }
        return preferenceStoreNames.associateWith { storeName ->
            decoded[storeName]
                ?: payload.themeState
                    ?.takeIf { storeName == THEME_STORE_NAME }
                    ?.toPreferences()
                ?: emptyPreferences()
        }
    }

    private suspend fun serializePreferences(preferences: Preferences): ByteArray {
        val output = ByteArrayOutputStream()
        PreferencesFileSerializer.writeTo(preferences, output)
        return output.toByteArray()
    }

    private fun livePreferenceStores(): Map<String, DataStore<Preferences>> = linkedMapOf(
        "browser_prefs" to context.browserDataStore,
        "browser_tabs" to context.browserTabsDataStore,
        "quick_access_prefs" to context.quickAccessDataStore,
        "storage_classifications_prefs" to context.classificationDataStore,
        "onboarding_prefs" to context.onboardingDataStore,
        THEME_STORE_NAME to context.themeDataStore,
        "activity_log" to context.activityLogDataStore,
        "storage_cleaner_prefs" to context.storageCleanerDataStore,
        "utility_prefs" to context.utilityDataStore,
        "onlyfiles_security" to context.vaultSecurityDataStore
    )

    private fun PreferencesBackupStore.toItem(status: PreferencesBackupItemStatus): PreferencesBackupItem =
        PreferencesBackupItem(id = name, label = name.displayName(), status = status)

    private fun PreferencesBackupPayload.toExportItems(): List<PreferencesBackupItem> {
        val items = stores.map { it.toItem(PreferencesBackupItemStatus.Exported) }.toMutableList()
        if (themeState != null && items.none { it.id == THEME_STORE_NAME }) {
            items += PreferencesBackupItem(
                THEME_STORE_NAME,
                THEME_STORE_NAME.displayName(),
                PreferencesBackupItemStatus.Exported
            )
        }
        return items
    }

    private fun PreferencesBackupPayload.hasStore(storeName: String): Boolean =
        stores.any { it.name == storeName } || (storeName == THEME_STORE_NAME && themeState != null)

    private fun String.displayName(): String = when (this) {
        "browser_prefs" -> "Browser preferences"
        "browser_tabs" -> "Browser tabs"
        "quick_access_prefs" -> "Quick Access"
        "storage_classifications_prefs" -> "Storage classifications"
        "onboarding_prefs" -> "Onboarding state"
        "theme_prefs" -> "Theme and appearance"
        "activity_log" -> "Activity log"
        "storage_cleaner_prefs" -> "Storage Cleaner rules"
        "utility_prefs" -> "Home tools"
        "onlyfiles_security" -> "OnlyFiles preferences"
        else -> this
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Settings backup is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String {
        val digits = "0123456789abcdef"
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 0x0f])
            }
        }
    }

    private companion object {
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val CURRENT_SCHEMA_VERSION = 2
        const val INTEGRITY_SCHEMA_VERSION = 2
        const val THEME_STORE_NAME = "theme_prefs"
        const val MAX_STORE_BYTES = 4 * 1024 * 1024
        const val MAX_TOTAL_STORE_BYTES = 16 * 1024 * 1024
        const val MAX_ENVELOPE_BYTES = 24 * 1024 * 1024
        const val MAX_ENCODED_STORE_CHARS = (MAX_STORE_BYTES + 2) / 3 * 4

        val preferenceStoreNames = linkedSetOf(
            "browser_prefs",
            "browser_tabs",
            "quick_access_prefs",
            "storage_classifications_prefs",
            "onboarding_prefs",
            "theme_prefs",
            "activity_log",
            "storage_cleaner_prefs",
            "utility_prefs",
            "onlyfiles_security"
        )
    }
}

@Serializable
private data class PreferencesBackupPayload(
    val schemaVersion: Int = 1,
    val createdAtMillis: Long,
    val packageName: String,
    val themeState: ThemeBackupState? = null,
    val stores: List<PreferencesBackupStore>
)

@Serializable
private data class PreferencesBackupStore(
    val name: String,
    val encodedBytes: String,
    val decodedSizeBytes: Int? = null,
    val sha256: String? = null
)

@Serializable
private data class ThemeBackupState(
    val themeMode: String,
    val accentColor: String,
    val harmonizeColors: Boolean,
    val vibrationsEnabled: Boolean,
    val doubleLineFilenames: Boolean,
    val marqueeFilenames: Boolean,
    val landscapeDualPaneEnabled: Boolean = false,
    val folderIconsEnabled: Boolean = false,
    val themePreset: String,
    val customPrimaryColorHex: String,
    val customBackgroundColorHex: String
)

private fun ThemeState.toBackupState(): ThemeBackupState = ThemeBackupState(
    themeMode = themeMode.name,
    accentColor = accentColor.name,
    harmonizeColors = harmonizeColors,
    vibrationsEnabled = vibrationsEnabled,
    doubleLineFilenames = doubleLineFilenames,
    marqueeFilenames = marqueeFilenames,
    landscapeDualPaneEnabled = landscapeDualPaneEnabled,
    folderIconsEnabled = folderIconsEnabled,
    themePreset = themePreset.name,
    customPrimaryColorHex = customPrimaryColorHex,
    customBackgroundColorHex = customBackgroundColorHex
)

private fun ThemeBackupState.toPreferences(): Preferences {
    val preferences = emptyPreferences().toMutablePreferences()
    preferences[ThemePreferences.THEME_MODE_KEY] = themeMode
    preferences[ThemePreferences.ACCENT_COLOR_KEY] = accentColor
    preferences[ThemePreferences.HARMONIZE_COLORS_KEY] = harmonizeColors
    preferences[ThemePreferences.VIBRATIONS_ENABLED_KEY] = vibrationsEnabled
    preferences[ThemePreferences.DOUBLE_LINE_FILENAMES_KEY] = doubleLineFilenames
    preferences[ThemePreferences.MARQUEE_FILENAMES_KEY] = marqueeFilenames
    preferences[ThemePreferences.LANDSCAPE_DUAL_PANE_KEY] = landscapeDualPaneEnabled
    preferences[ThemePreferences.FOLDER_ICONS_ENABLED_KEY] = folderIconsEnabled
    preferences[ThemePreferences.THEME_PRESET_KEY] = themePreset
    preferences[ThemePreferences.CUSTOM_PRIMARY_KEY] = customPrimaryColorHex
    preferences[ThemePreferences.CUSTOM_BACKGROUND_KEY] = customBackgroundColorHex
    return preferences.toPreferences()
}
