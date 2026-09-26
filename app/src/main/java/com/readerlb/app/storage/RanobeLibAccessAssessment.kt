package com.readerlb.app.storage

enum class RanobeLibAccessCapability {
    CONNECTED,
    USER_PICKER,
    SYSTEM_RESTRICTED
}

data class RanobeLibAccessAssessment(
    val capability: RanobeLibAccessCapability,
    val androidLabel: String
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
        get() =
            capability ==
                RanobeLibAccessCapability
                    .SYSTEM_RESTRICTED
}

fun assessRanobeLibAccess(
    sdkInt: Int,
    androidRelease: String,
    connected: Boolean
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
                connected ->
                    RanobeLibAccessCapability
                        .CONNECTED

                sdkInt <
                    android.os.Build
                        .VERSION_CODES.R ->
                    RanobeLibAccessCapability
                        .USER_PICKER

                else ->
                    RanobeLibAccessCapability
                        .SYSTEM_RESTRICTED
            },
        androidLabel = label
    )
}
