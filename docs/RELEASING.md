# ReaderLB releases and updates

ReaderLB uses GitHub Releases as the update source.

## Permanent signing key

Do not distribute CI debug APKs as updateable releases. Android only accepts an
upgrade when the new APK is signed by the same certificate as the installed
version.

The repository release workflow expects one permanent Android signing keystore
stored only in GitHub Actions secrets:

- `READERLB_KEYSTORE_B64`
- `READERLB_KEYSTORE_PASSWORD`
- `READERLB_KEY_ALIAS`
- `READERLB_KEY_PASSWORD`

The keystore itself must never be committed to this public repository.

After the first release-signed build is installed, every later release must use
the same key.

## Publishing

Create a tag such as `v0.5.0`. The release workflow runs tests and lint,
builds an R8/resource-shrunk release APK, calculates SHA-256 and publishes both
files to GitHub Releases.

## In-app updater

ReaderLB checks `releases/latest` at most once per 24 hours when the app is
opened. It does not run a permanent background service.

When a newer release exists:

1. ReaderLB shows an update card.
2. The user taps Update.
3. ReaderLB downloads the APK to its private cache and verifies the GitHub
   release SHA-256 digest when one is available.
4. ReaderLB verifies that the downloaded APK has the same application package
   name.
5. ReaderLB opens a PackageInstaller session and Android handles the final user-authorized install.

Android 8+ may require the user to allow ReaderLB as an install source. Older
Android versions and many OEM builds still require an explicit confirmation for
each sideloaded update, so the universal flow is intentionally semi-automatic.

## Legacy test-build warning

Do not promise an in-place upgrade from historical 0.3.1–0.4.2 test APKs.
Their signing certificates differ from each other and from the pinned test
certificate. See `docs/SIGNING_MIGRATION.md`.

The first build distributed as the stable update channel must be signed by the
permanent release key, and all later release APKs must use that exact key.
