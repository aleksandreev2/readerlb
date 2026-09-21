# Release push notifications

ReaderLB uses Firebase Cloud Messaging only for opt-in notifications about stable releases.

## Privacy model

FCM is disabled by default through `firebase_messaging_auto_init_enabled=false`.

When the user enables **Push о новых релизах**:

1. Android 13+ asks for notification permission;
2. FCM auto-initialization is enabled;
3. the device subscribes to topic `readerlb_releases`.

When the user disables the setting, ReaderLB unsubscribes from the topic, deletes the current FCM token and disables auto-initialization again.

ReaderLB does not include Firebase Analytics.

## Client configuration

Public Android config:

- `app/google-services.json`

This file identifies the Firebase project and Android package. It is not a server credential.

Never commit a Firebase Admin/service-account JSON.

## Server-side sender

Release notifications should be sent by GitHub Actions after a stable GitHub Release is published.

The sender must use a Firebase service account stored as a GitHub Actions secret, for example:

`FIREBASE_SERVICE_ACCOUNT`

The workflow should authenticate with Google, request an OAuth token for the FCM HTTP v1 API and publish a **data message** to topic `readerlb_releases`.

Recommended data payload:

```json
{
  "type": "release",
  "version": "v1.0.1",
  "title": "Доступно обновление ReaderLB",
  "body": "ReaderLB 1.0.1 уже доступна."
}
```

A data message is used so ReaderLB can create a consistent notification in both foreground and background and attach an intent that opens the update screen.

## Source of truth

Push is only a signal. GitHub Releases remains the source of truth.

After tapping a release notification, ReaderLB forces a fresh GitHub Releases check before offering an APK.
