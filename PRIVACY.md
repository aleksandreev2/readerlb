# Privacy

This document describes ReaderLB's current data behavior.

## Books and local library

ReaderLB processes imported EPUB/TXT files and the selected RanobeLib local
library on the device.

Android application backup is disabled for ReaderLB so the app's private
preferences and cached local-library metadata are not copied into a cloud backup
by ReaderLB's manifest configuration.

ReaderLB does **not** upload:

- book text;
- chapter contents;
- covers or illustrations;
- the list of locally installed RanobeLib titles;
- import history.

These values are used locally to build or inspect the RanobeLib offline format.

## Network transport

ReaderLB disables cleartext HTTP traffic at the Android application level.
Network features are expected to use HTTPS.

## GitHub Releases

When update checks are enabled, ReaderLB contacts GitHub to discover the latest
ReaderLB release and, when the user chooses to update, download its APK.

This network request is used only for ReaderLB update delivery.

## Firebase Cloud Messaging

Push notifications about new stable ReaderLB releases are optional and disabled
by default.

When the user enables the setting:

- Firebase Cloud Messaging initialization is enabled;
- the device subscribes to the `readerlb_releases` topic;
- Firebase creates/uses the technical registration token required to deliver
  messages.

ReaderLB does not include Firebase Analytics and does not send book/library
content through FCM.

When release notifications are disabled, ReaderLB unsubscribes from the topic,
deletes the current FCM token and disables FCM auto-initialization.

## Android permissions

ReaderLB currently uses permissions for:

- internet access, for GitHub update checks/downloads and optional FCM;
- notification display on Android 13+ when the user enables release pushes;
- package installation flow for user-requested ReaderLB updates.

Access to the RanobeLib book directory is granted explicitly through Android's
Storage Access Framework rather than a broad storage permission.

## Crash/usage analytics

ReaderLB currently has no Firebase Analytics or crash-reporting SDK configured.

If telemetry is added in the future, this document and the in-app behavior
should be updated before collection is enabled.
