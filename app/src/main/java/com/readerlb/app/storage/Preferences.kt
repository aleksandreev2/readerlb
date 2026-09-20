package com.readerlb.app.storage

import android.content.Context
import android.net.Uri

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("readerlb", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    var ranobeLibBookTree: Uri?
        get() = prefs.getString("ranobelib_book_tree", null)?.let(Uri::parse)
        set(value) = prefs.edit().putString("ranobelib_book_tree", value?.toString()).apply()

    var lastUpdateCheckMillis: Long
        get() = prefs.getLong("last_update_check_millis", 0L)
        set(value) = prefs.edit()
            .putLong("last_update_check_millis", value)
            .apply()
}
