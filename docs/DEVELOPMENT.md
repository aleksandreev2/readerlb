# Development workflow

## Branch model

ReaderLB uses a simple trunk-oriented workflow:

- `main` is the default and releasable branch;
- all normal work starts from the latest `main`;
- feature/fix/docs/chore branches are short-lived;
- changes return to `main` through pull requests.

The old long-running MVP branch exists only as project history. New development
must not continue from it after the initial merge.

## Branch names

- `feat/<short-name>`
- `fix/<short-name>`
- `docs/<short-name>`
- `chore/<short-name>`

## Gradle Wrapper

Use the committed Gradle Wrapper for all local and CI builds:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Windows users can run the equivalent commands through `gradlew.bat`.

The repository pins Gradle 8.9 in `gradle/wrapper/gradle-wrapper.properties`
and verifies the official distribution SHA-256. Do not replace the wrapper JAR
manually; regenerate it with a trusted Gradle installation when upgrading.

## Pull request gates

Before merge:

1. unit tests are green;
2. Android runtime tests compile;
3. instrumented tests pass on Android 10 / API 29 and Android 15 / API 35;
4. Android Lint is green;
5. debug and optimized APKs build;
6. signing identity check passes;
7. APK size budget passes;
8. CodeQL has no blocking result.

For storage, update, signing or notification changes, add a manual smoke-test
note to the PR.

## Android runtime matrix

`.github/workflows/android-runtime.yml` executes the instrumented Android tests
on both ends of ReaderLB's important compatibility range:

- API 29 — the minimum supported Android version;
- API 35 — a modern scoped-storage/notification-permission environment.

This is intentionally separate from the faster build workflow: the normal
Android job still compiles instrumented tests early, while the runtime workflow
boots real emulators and executes them.

## Security scanning

`.github/workflows/codeql.yml` performs GitHub CodeQL analysis for Kotlin using
manual build mode. Kotlin requires a real build for complete extraction, so the
workflow builds the debug app between CodeQL initialization and analysis.

The workflow runs for `main`, pull requests into `main`, a weekly schedule,
and manual dispatch.

## Dependencies

Dependabot checks Gradle and GitHub Actions monthly. Related updates are grouped
to avoid a stream of one-dependency pull requests.

Dependency PRs are not auto-merged. Major framework/tooling changes should pass
the same CI and device checks as hand-written changes.

## Releases

Stable releases are tag-driven and come only from a reviewed commit already in
`main`.

The release tag must match `versionName`, for example `v1.0.0`.

Test artifacts from ordinary CI are not stable releases and must not be
advertised by the in-app updater.

See [RELEASING.md](RELEASING.md).

## Versioning

Before 1.0, ReaderLB uses `0.x.y`.

After 1.0:

- patch: bug/security/device compatibility fixes;
- minor: backwards-compatible features;
- major: incompatible storage/update behavior.

## Main protection

Recommended GitHub rules for `main`:

- require pull requests before merging;
- require the Android build and CodeQL status checks;
- block force pushes;
- block branch deletion;
- require branches to be up to date before merging;
- dismiss stale approvals when code changes once external contributors are
  active.

Repository-admin branch rules are configured in GitHub itself rather than in the
source tree.
