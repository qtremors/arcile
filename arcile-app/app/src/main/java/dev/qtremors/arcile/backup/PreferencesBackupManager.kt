package dev.qtremors.arcile.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.ThemeMode
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.ui.theme.ThemePreset
import dev.qtremors.arcile.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.R
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val themePreferences: ThemePreferences
) {
    constructor(context: Context) : this(context, ThemePreferences(context))

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportTo(uri: Uri): Result<PreferencesBackupOperationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val failures = mutableListOf<PreferencesBackupFailure>()
            val payload = PreferencesBackupPayload(
                createdAtMillis = System.currentTimeMillis(),
                packageName = context.packageName,
                themeState = themePreferences.themeState.first().toBackupState(),
                stores = preferenceStoreNames.mapNotNull { storeName ->
                    val file = dataStoreFile(storeName)
                    if (!file.exists()) return@mapNotNull null
                    runCatching {
                        PreferencesBackupStore(
                            name = storeName,
                            encodedBytes = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        )
                    }.getOrElse { error ->
                        failures += PreferencesBackupFailure(
                            storeName.displayName(),
                            error.message ?: context.getString(R.string.settings_backup_store_read_failed)
                        )
                        null
                    }
                }
            )
            require(payload.stores.isNotEmpty() || payload.themeState != null) { "No settings are available to export yet" }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(json.encodeToString(payload).toByteArray())
            } ?: error("Unable to open backup destination")
            PreferencesBackupOperationResult(
                items = payload.toExportItems(),
                failures = failures
            )
        }
    }

    suspend fun preview(uri: Uri): Result<PreferencesBackupPreview> = withContext(Dispatchers.IO) {
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

    suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = readPayload(uri)
            validatePayload(payload)

            val backupStoresByName = payload.stores.associateBy { it.name }
            val decodedStores = backupStoresByName.mapValues { (_, store) ->
                Base64.decode(store.encodedBytes, Base64.DEFAULT)
            }
            val items = mutableListOf<PreferencesBackupItem>()
            val failures = mutableListOf<PreferencesBackupFailure>()

            preferenceStoreNames
                .filterNot { payload.hasStore(it) }
                .forEach { storeName ->
                    runCatching { dataStoreFile(storeName).delete() }
                        .onSuccess {
                            items += PreferencesBackupItem(storeName, storeName.displayName(), PreferencesBackupItemStatus.Reset)
                        }
                        .onFailure { error ->
                            failures += PreferencesBackupFailure(
                                storeName.displayName(),
                                error.message ?: context.getString(R.string.settings_backup_store_reset_failed)
                            )
                        }
                }
            decodedStores.forEach { (storeName, bytes) ->
                val store = backupStoresByName.getValue(storeName)
                if (store.name !in preferenceStoreNames) return@forEach
                runCatching {
                    val target = dataStoreFile(store.name)
                    target.parentFile?.mkdirs()
                    val temp = File(target.parentFile, "${target.name}.restore.tmp")
                    temp.writeBytes(bytes)
                    if (target.exists() && !target.delete()) {
                        error("Unable to replace existing preference store")
                    }
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }.onSuccess {
                    items += store.toItem(PreferencesBackupItemStatus.Restored)
                }.onFailure { error ->
                    failures += PreferencesBackupFailure(
                        store.name.displayName(),
                        error.message ?: context.getString(R.string.settings_backup_store_restore_failed)
                    )
                }
            }
            payload.themeState?.let { themeBackup ->
                runCatching {
                    themePreferences.saveThemeState(themeBackup.toThemeState())
                }.onSuccess {
                    if (items.none { it.id == THEME_STORE_NAME }) {
                        items += PreferencesBackupItem(THEME_STORE_NAME, THEME_STORE_NAME.displayName(), PreferencesBackupItemStatus.Restored)
                    }
                }.onFailure { error ->
                    failures += PreferencesBackupFailure(
                        THEME_STORE_NAME.displayName(),
                        error.message ?: context.getString(R.string.settings_backup_theme_restore_failed)
                    )
                }
            }
            PreferencesBackupOperationResult(items = items, failures = failures)
        }
    }

    private fun readPayload(uri: Uri): PreferencesBackupPayload {
        val encoded = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().decodeToString()
        } ?: error("Unable to open backup file")
        return json.decodeFromString(encoded)
    }

    private fun validatePayload(payload: PreferencesBackupPayload) {
        require(payload.schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported backup version" }
        require(payload.packageName == context.packageName) { "Backup belongs to a different app" }
    }

    private fun dataStoreFile(storeName: String): File =
        File(context.filesDir, "datastore/$storeName.preferences_pb")

    private fun PreferencesBackupStore.toItem(status: PreferencesBackupItemStatus): PreferencesBackupItem =
        PreferencesBackupItem(
            id = name,
            label = name.displayName(),
            status = status
        )

    private fun PreferencesBackupPayload.toExportItems(): List<PreferencesBackupItem> {
        val items = stores.map { it.toItem(PreferencesBackupItemStatus.Exported) }.toMutableList()
        if (themeState != null && items.none { it.id == THEME_STORE_NAME }) {
            items += PreferencesBackupItem(THEME_STORE_NAME, THEME_STORE_NAME.displayName(), PreferencesBackupItemStatus.Exported)
        }
        return items
    }

    private fun PreferencesBackupPayload.hasStore(storeName: String): Boolean =
        stores.any { it.name == storeName } || (storeName == THEME_STORE_NAME && themeState != null)

    private fun String.displayName(): String = when (this) {
        "browser_prefs" -> "Browser preferences"
        "quick_access_prefs" -> "Quick Access"
        "storage_classifications_prefs" -> "Storage classifications"
        "onboarding_prefs" -> "Onboarding state"
        "theme_prefs" -> "Theme and appearance"
        "activity_log" -> "Activity log"
        "storage_cleaner_prefs" -> "Storage Cleaner rules"
        "utility_prefs" -> "Home tools"
        else -> this
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val THEME_STORE_NAME = "theme_prefs"

        val preferenceStoreNames = setOf(
            "browser_prefs",
            "quick_access_prefs",
            "storage_classifications_prefs",
            "onboarding_prefs",
            "theme_prefs",
            "activity_log",
            "storage_cleaner_prefs",
            "utility_prefs"
        )
    }
}

data class PreferencesBackupPreview(
    val createdAtMillis: Long,
    val items: List<PreferencesBackupItem>
)

data class PreferencesBackupOperationResult(
    val items: List<PreferencesBackupItem>,
    val failures: List<PreferencesBackupFailure> = emptyList()
) {
    val successCount: Int
        get() = items.count { it.status == PreferencesBackupItemStatus.Exported || it.status == PreferencesBackupItemStatus.Restored || it.status == PreferencesBackupItemStatus.Reset }

    val hasFailures: Boolean
        get() = failures.isNotEmpty()
}

data class PreferencesBackupItem(
    val id: String,
    val label: String,
    val status: PreferencesBackupItemStatus
)

enum class PreferencesBackupItemStatus {
    Exported,
    WillRestore,
    WillReset,
    Restored,
    Reset
}

data class PreferencesBackupFailure(
    val label: String,
    val message: String
)

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
    val encodedBytes: String
)

@Serializable
private data class ThemeBackupState(
    val themeMode: String,
    val accentColor: String,
    val harmonizeColors: Boolean,
    val vibrationsEnabled: Boolean,
    val doubleLineFilenames: Boolean,
    val marqueeFilenames: Boolean,
    val themePreset: String,
    val customPrimaryColorHex: String,
    val customBackgroundColorHex: String
)

private fun ThemeState.toBackupState(): ThemeBackupState =
    ThemeBackupState(
        themeMode = themeMode.name,
        accentColor = accentColor.name,
        harmonizeColors = harmonizeColors,
        vibrationsEnabled = vibrationsEnabled,
        doubleLineFilenames = doubleLineFilenames,
        marqueeFilenames = marqueeFilenames,
        themePreset = themePreset.name,
        customPrimaryColorHex = customPrimaryColorHex,
        customBackgroundColorHex = customBackgroundColorHex
    )

private fun ThemeBackupState.toThemeState(): ThemeState =
    ThemeState(
        themeMode = ThemeMode.entries.find { it.name == themeMode } ?: ThemeMode.SYSTEM,
        accentColor = AccentColor.entries.find { it.name == accentColor } ?: AccentColor.DYNAMIC,
        harmonizeColors = harmonizeColors,
        vibrationsEnabled = vibrationsEnabled,
        doubleLineFilenames = doubleLineFilenames,
        marqueeFilenames = marqueeFilenames,
        themePreset = ThemePreset.entries.find { it.name == themePreset } ?: ThemePreset.NONE,
        customPrimaryColorHex = customPrimaryColorHex,
        customBackgroundColorHex = customBackgroundColorHex
    )
