// SPDX-License-Identifier: GPL-3.0-or-later
package com.readerlb.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import io.github.muntashirakon.adb.PRNGFixes;

/**
 * Process bootstrap for ReaderLB's embedded Wireless ADB client.
 */
public final class ReaderLbApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PRNGFixes.apply();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/sun/");
        }
    }
}
