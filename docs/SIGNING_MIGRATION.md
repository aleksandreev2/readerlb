# Android update signing migration

ReaderLB 0.4.x exposed a CI signing bug that explains why an APK could install
cleanly but would not update the already installed app on a device such as a
Redmi 9.

## What actually happened

The workflow cached this key:

`$HOME/.android/debug.keystore`

but Gradle's default debug signing configuration on the runner was using a
different path. As a result, separate CI builds could be signed by separate
debug certificates even though `versionCode` increased correctly.

Fingerprints recovered directly from the published APK signing blocks:

- ReaderLB 0.4.1: `460470a11c4a15b3caa40bcc5779c65be3382276e741ae5254a9f7d6b60ea8ba`
- ReaderLB 0.4.2: `171d0580de60360f9f641a7b573f61d852fd3a3dd96cc0975d42fc31d87d5417`
- Current pinned test certificate: `c17731310b880d0fb9c4e55c1863aee7461650725003d02fa985ce2be3d22b22`

These certificates are different. Android therefore correctly rejects an
in-place update from those historical builds. This is not a Redmi-specific
package manager bug; Redmi/MIUI simply exposed the signing inconsistency.

## One-time migration

There is no safe way to make Android accept a new APK over a package signed by
an unrelated private key. The old CI-generated private keys are not available,
so 0.4.x requires one final uninstall/reinstall when moving to the stable
signing channel.

After that migration, every public ReaderLB release must use the same permanent
release key. Never rotate it accidentally.

The app's updater also checks the downloaded APK certificate before launching
the installer, so a mismatched future build fails with an explicit ReaderLB
message rather than an opaque package-installer failure.

## CI protection

Current CI pins the debug/test build to one explicit cached key path and asserts
that the generated APK certificate matches that key. Optimized test APKs use the
same pinned test certificate.

For public releases, use the dedicated release keystore stored in GitHub Actions
secrets as described in `docs/RELEASING.md`.
