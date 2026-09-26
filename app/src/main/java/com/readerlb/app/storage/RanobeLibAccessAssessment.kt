package com.readerlb.app.storage

enum class RanobeLibAccessCapability {
    CONNECTED,
    USER_PICKER,
    SHIZUKU_PERMISSION_REQUIRED,
    SHIZUKU_STOPPED,
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_CONNECTING,
    SHIZUKU_FOLDER_MISSING
}

enum class RanobeLibStorageBackend { SAF, SHIZUKU, PORTABLE }

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
    shizukuState: ShizukuAccessState = ShizukuAccessState.NOT_INSTALLED,
    connectedWithShizuku: Boolean = false
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

    return RanobeLibAccessAssessment(
        capability =
            when {
                connected && (!connectedWithShizuku ||
                    shizukuState == ShizukuAccessState.READY) ->
                    RanobeLibAccessCapability
                        .CONNECTED

                sdkInt <
                    android.os.Build
                        .VERSION_CODES.R ->
                    RanobeLibAccessCapability
                        .USER_PICKER

                else -> when (shizukuState) {
                    ShizukuAccessState.READY -> RanobeLibAccessCapability.CONNECTED
                    ShizukuAccessState.PERMISSION_REQUIRED ->
                        RanobeLibAccessCapability.SHIZUKU_PERMISSION_REQUIRED
                    ShizukuAccessState.STOPPED ->
                        RanobeLibAccessCapability.SHIZUKU_STOPPED
                    ShizukuAccessState.NOT_INSTALLED ->
                        RanobeLibAccessCapability.SHIZUKU_NOT_INSTALLED
                    ShizukuAccessState.FOLDER_MISSING ->
                        RanobeLibAccessCapability.SHIZUKU_FOLDER_MISSING
                    ShizukuAccessState.CHECKING,
                    ShizukuAccessState.CONNECTING ->
                        RanobeLibAccessCapability.SHIZUKU_CONNECTING
                }
            },
        androidLabel = label,
        backend = when {
            connected && !connectedWithShizuku -> RanobeLibStorageBackend.SAF
            connected && shizukuState == ShizukuAccessState.READY ->
                RanobeLibStorageBackend.SHIZUKU
            sdkInt < android.os.Build.VERSION_CODES.R -> RanobeLibStorageBackend.SAF
            shizukuState == ShizukuAccessState.READY -> RanobeLibStorageBackend.SHIZUKU
            else -> RanobeLibStorageBackend.PORTABLE
        }
    )
}
