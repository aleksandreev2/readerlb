package com.readerlb.app.storage

enum class RanobeLibAccessCapability {
    CONNECTED,
    USER_PICKER,
    READERLB_SETUP_REQUIRED,
    READERLB_CONNECTING,
    READERLB_ERROR,
    SHIZUKU_PERMISSION_REQUIRED,
    SHIZUKU_STOPPED,
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_CONNECTING,
    SHIZUKU_FOLDER_MISSING
}

enum class RanobeLibStorageBackend {
    SAF,
    READERLB_BRIDGE,
    SHIZUKU,
    PORTABLE
}

enum class ReaderLbBuiltInAccessState {
    CHECKING,
    DISCONNECTED,
    PAIRING,
    STARTING,
    READY,
    ERROR
}

data class RanobeLibAccessAssessment(
    val capability: RanobeLibAccessCapability,
    val androidLabel: String,
    val backend: RanobeLibStorageBackend
) {
    val connected: Boolean
        get() =
            capability ==
                RanobeLibAccessCapability
                    .CONNECTED

    val canUseSystemFolderPicker: Boolean
        get() =
            capability ==
                RanobeLibAccessCapability
                    .USER_PICKER

    val blockedByModernAndroid: Boolean
        get() = !connected && !canUseSystemFolderPicker
}

fun assessRanobeLibAccess(
    sdkInt: Int,
    androidRelease: String,
    connected: Boolean,
    shizukuState: ShizukuAccessState =
        ShizukuAccessState.NOT_INSTALLED,
    connectedWithShizuku: Boolean = false,
    builtInState: ReaderLbBuiltInAccessState =
        ReaderLbBuiltInAccessState.DISCONNECTED,
    connectedWithBuiltInBridge: Boolean = false
): RanobeLibAccessAssessment {
    val label =
        androidRelease
            .trim()
            .takeIf(
                String::isNotBlank
            )
            ?.let {
                "Android $it"
            }
            ?: "Android API $sdkInt"

    val capability =
        when {
            connected &&
                connectedWithBuiltInBridge &&
                builtInState !=
                    ReaderLbBuiltInAccessState.READY ->
                RanobeLibAccessCapability
                    .READERLB_SETUP_REQUIRED

            connected &&
                connectedWithShizuku &&
                shizukuState !=
                    ShizukuAccessState.READY ->
                RanobeLibAccessCapability
                    .READERLB_SETUP_REQUIRED

            connected ->
                RanobeLibAccessCapability
                    .CONNECTED

            sdkInt <
                android.os.Build
                    .VERSION_CODES.R ->
                RanobeLibAccessCapability
                    .USER_PICKER

            builtInState ==
                ReaderLbBuiltInAccessState.READY ->
                RanobeLibAccessCapability
                    .CONNECTED

            shizukuState ==
                ShizukuAccessState.READY ->
                RanobeLibAccessCapability
                    .CONNECTED

            builtInState in setOf(
                ReaderLbBuiltInAccessState.CHECKING,
                ReaderLbBuiltInAccessState.PAIRING,
                ReaderLbBuiltInAccessState.STARTING
            ) ->
                RanobeLibAccessCapability
                    .READERLB_CONNECTING

            builtInState ==
                ReaderLbBuiltInAccessState.ERROR ->
                RanobeLibAccessCapability
                    .READERLB_ERROR

            else ->
                RanobeLibAccessCapability
                    .READERLB_SETUP_REQUIRED
        }

    val backend =
        when {
            connected &&
                connectedWithBuiltInBridge ->
                RanobeLibStorageBackend
                    .READERLB_BRIDGE

            connected &&
                connectedWithShizuku ->
                RanobeLibStorageBackend
                    .SHIZUKU

            connected ->
                RanobeLibStorageBackend.SAF

            builtInState ==
                ReaderLbBuiltInAccessState.READY ->
                RanobeLibStorageBackend
                    .READERLB_BRIDGE

            shizukuState ==
                ShizukuAccessState.READY ->
                RanobeLibStorageBackend
                    .SHIZUKU

            sdkInt <
                android.os.Build
                    .VERSION_CODES.R ->
                RanobeLibStorageBackend.SAF

            else ->
                RanobeLibStorageBackend.PORTABLE
        }

    return RanobeLibAccessAssessment(
        capability = capability,
        androidLabel = label,
        backend = backend
    )
}
