package dev.qtremors.arcile.core.operation.android.apk

enum class ApkUpdateRejectionReason {
    IncompatibleSdk,
    SignatureMismatch,
    DowngradeOrSameVersion,
    IncompleteSplit,
    IncompatiblePluginApi,
    InvalidPluginMetadata,
    CorruptedOrInaccessible
}
