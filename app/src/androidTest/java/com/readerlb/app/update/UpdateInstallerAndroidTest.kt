package com.readerlb.app.update

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateInstallerAndroidTest {

    @Test
    fun packageInstallerSessionTargetsReaderLbAndCanBeAbandoned() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val installer =
            context.packageManager.packageInstaller
        val sessionId =
            installer.createSession(
                createUpdateInstallSessionParams(
                    packageName = context.packageName,
                    apkSize = 1024L
                )
            )

        try {
            val info =
                installer.getSessionInfo(sessionId)

            assertNotNull(info)
            assertEquals(
                context.packageName,
                info?.appPackageName
            )
        } finally {
            installer.abandonSession(sessionId)
        }
    }

    @Test
    fun unknownSourcesSettingsIntentTargetsReaderLbPackage() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val intent =
            UpdateManager(context)
                .unknownSourcesSettingsIntent()

        assertEquals(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            intent.action
        )
        assertEquals(
            "package:${context.packageName}",
            intent.dataString
        )
    }
}
