# ReaderLB releases and updates

ReaderLB uses GitHub Releases as the only stable update source.

## Release prerequisites

A stable release must come from a commit already merged into `main`.

Before tagging a release:

1. update `versionCode` and `versionName` in `app/build.gradle.kts`;
2. update `CHANGELOG.md`;
3. merge the change into `main`;
4. wait for Android CI and CodeQL to finish successfully;
5. verify the intended version on at least one physical Android device when the
   change touches storage, installation or notifications.

Do not publish ordinary CI artifacts as stable releases.

## Permanent signing key

Android only accepts an in-place upgrade when the new APK is signed by the same
certificate as the installed version.

The release workflow expects one permanent Android signing keystore stored only
in GitHub Actions secrets:

- `READERLB_KEYSTORE_B64`
- `READERLB_KEYSTORE_PASSWORD`
- `READERLB_KEY_ALIAS`
- `READERLB_KEY_PASSWORD`

The keystore itself must never be committed to this public repository or copied
into an issue, pull request, workflow log or release asset.

After the first release-signed build is installed, every later stable release
must use that exact key.

## Optional Firebase release push

Release notifications use Firebase Cloud Messaging. The release workflow can
send a push after the GitHub Release has been created successfully.

The sender credential is stored only as:

- `FIREBASE_SERVICE_ACCOUNT`

This must contain the Firebase service-account JSON, not
`app/google-services.json`.

If the secret is absent, release publication still succeeds and the push step
is skipped.

## Publishing

Create a SemVer-style tag that exactly matches `versionName`, for example:

```text
v1.0.0
```

The tag must point at the reviewed `main` commit.

The release workflow then:

1. verifies the tag/version match;
2. restores the permanent signing key;
3. runs the Android 10 emulator smoke test;
4. runs unit tests and release lint;
5. builds the R8/resource-shrunk APK;
6. verifies the signing certificate;
7. writes the APK SHA-256 file;
8. publishes both files to GitHub Releases;
9. sends the optional FCM release notification when configured.

## Post-release verification

After publication:

1. open the GitHub Release and verify the APK and `.sha256` assets exist;
2. verify `releases/latest` resolves to the new stable release;
3. check ReaderLB's in-app updater from the previous stable build;
4. if FCM is configured, verify a subscribed device receives the release push;
5. install the update over the previous stable build and confirm Android accepts
   the signature.

The stable-to-stable update test is mandatory before calling the signing/update
chain production-ready.

## In-app updater

ReaderLB checks `releases/latest` at most once per 24 hours when the app is
opened. A release push can also open the update screen and force a fresh check.

When a newer release exists:

1. ReaderLB shows an update card.
2. The user taps Update.
3. ReaderLB downloads the APK to its private cache and verifies the GitHub
   release SHA-256 digest when one is available.
4. ReaderLB verifies that the downloaded APK has the same application package
   name.
5. ReaderLB opens a PackageInstaller session and Android handles the final
   user-authorized install.

Android/OEM policy may still require user confirmation.

## Legacy test-build warning

Do not promise an in-place upgrade from historical 0.3.1–0.4.2 test APKs.
Their signing certificates differ from each other and from the pinned test
certificate. See `docs/SIGNING_MIGRATION.md`.

The first build distributed as the stable update channel must be signed by the
permanent release key, and all later release APKs must use that exact key.
