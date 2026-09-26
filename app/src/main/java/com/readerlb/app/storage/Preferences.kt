package com.readerlb.app.storage

import android.content.Context
import android.net.Uri
import com.readerlb.app.importer.DEFAULT_EPUB_IMAGE_LIMIT_MB
import com.readerlb.app.importer.DEFAULT_EPUB_SOURCE_LIMIT_MB
import com.readerlb.app.importer.DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("readerlb", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    var ranobeLibAccessIntroDone: Boolean
        get() = prefs.getBoolean(
            "ranobelib_access_intro_done",
            false
        )
        set(value) = prefs.edit()
            .putBoolean(
                "ranobelib_access_intro_done",
                value
            )
            .apply()

    var shizukuAccessIntroDone: Boolean
        get() = prefs.getBoolean(
            "shizuku_access_intro_done",
            false
        )
        set(value) = prefs.edit()
            .putBoolean(
                "shizuku_access_intro_done",
                value
            )
            .apply()


    var ranobeLibBookTree: Uri?
        get() = prefs.getString(
            "ranobelib_book_tree",
            null
        )?.let(Uri::parse)
        set(value) = prefs.edit()
            .putString(
                "ranobelib_book_tree",
                value?.toString()
            )
            .apply()

    var localLibraryCacheJson: String?
        get() = prefs.getString(
            "local_library_cache_json",
            null
        )
        set(value) = prefs.edit()
            .putString(
                "local_library_cache_json",
                value
            )
            .apply()

    var localLibraryCacheTree: Uri?
        get() = prefs.getString(
            "local_library_cache_tree",
            null
        )?.let(Uri::parse)
        set(value) = prefs.edit()
            .putString(
                "local_library_cache_tree",
                value?.toString()
            )
            .apply()

    var lastImportDocument: Uri?
        get() = prefs.getString(
            "last_import_document",
            null
        )?.let(Uri::parse)
        set(value) = prefs.edit()
            .putString(
                "last_import_document",
                value?.toString()
            )
            .apply()

    var lastUpdateCheckMillis: Long
        get() = prefs.getLong("last_update_check_millis", 0L)
        set(value) = prefs.edit()
            .putLong("last_update_check_millis", value)
            .apply()

    var autoUpdateChecks: Boolean
        get() = prefs.getBoolean("auto_update_checks", true)
        set(value) = prefs.edit()
            .putBoolean("auto_update_checks", value)
            .apply()

    var releaseNotificationsEnabled: Boolean
        get() = prefs.getBoolean(
            "release_notifications_enabled",
            false
        )
        set(value) = prefs.edit()
            .putBoolean(
                "release_notifications_enabled",
                value
            )
            .apply()

    var directImportEnabled: Boolean
        get() = prefs.getBoolean("direct_import_enabled", true)
        set(value) = prefs.edit()
            .putBoolean("direct_import_enabled", value)
            .apply()

    var importHintsDone: Boolean
        get() = prefs.getBoolean("import_hints_done", false)
        set(value) = prefs.edit()
            .putBoolean("import_hints_done", value)
            .apply()

    var epubSourceLimitMb: Long
        get() =
            prefs.getLong(
                "epub_source_limit_mb",
                DEFAULT_EPUB_SOURCE_LIMIT_MB
            )
        set(value) =
            prefs.edit()
                .putLong(
                    "epub_source_limit_mb",
                    value.coerceAtLeast(0L)
                )
                .apply()

    var epubImageLimitMb: Long
        get() =
            prefs.getLong(
                "epub_image_limit_mb",
                DEFAULT_EPUB_IMAGE_LIMIT_MB
            )
        set(value) =
            prefs.edit()
                .putLong(
                    "epub_image_limit_mb",
                    value.coerceAtLeast(0L)
                )
                .apply()

    var epubTotalImageLimitMb: Long
        get() =
            prefs.getLong(
                "epub_total_image_limit_mb",
                DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB
            )
        set(value) =
            prefs.edit()
                .putLong(
                    "epub_total_image_limit_mb",
                    value.coerceAtLeast(0L)
                )
                .apply()
}
