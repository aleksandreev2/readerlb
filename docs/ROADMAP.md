# ReaderLB roadmap

This roadmap defines how ReaderLB should evolve without turning every test build
into a public release or breaking the local RanobeLib library.

## Release channels

ReaderLB uses two channels only:

1. **Test builds**
   - Produced by the normal Android CI workflow.
   - Distributed manually to testers as GitHub Actions artifacts.
   - May change frequently.
   - Must never be offered by the in-app updater.
   - Use the pinned test signing certificate.

2. **Stable releases**
   - Published only through GitHub Releases.
   - Built only from a version tag matching `versionName`.
   - Signed with the permanent release key.
   - Are the only builds visible to the in-app updater.

Do not add a beta channel until the updater has explicit pre-release version
handling. Until then, test artifacts are the beta channel.

## Mandatory release gates

Every build intended for testing must pass:

- unit tests;
- Android Lint;
- debug APK build;
- signing-certificate verification;
- optimized APK build;
- optimized APK size budget (currently <= 6 MiB).

Every stable release must additionally pass:

- release-key restoration from GitHub Actions secrets;
- release signing-certificate verification;
- version tag == `versionName`;
- release APK SHA-256 generation;
- GitHub Release publication.

Before every stable release, manually smoke-test:

- fresh install;
- update from the previous stable version;
- EPUB import into a new local title;
- incremental import into an existing local title;
- chapter 0 + illustrations;
- one large EPUB;
- RanobeLib folder reconnect after app restart;
- local Library refresh;
- update check/download/install flow.

The Redmi 9 remains a mandatory low-end test device. One newer Android device
should also be used to cover Android 12+ PackageInstaller behavior.

## 0.5.0 — Stable foundation

Goal: stop treating ReaderLB as an experimental converter and establish a safe
base that can actually be updated.

Scope:

- permanent stable signing chain;
- GitHub Releases updater;
- optional automatic update checks;
- PackageInstaller handoff;
- signing/package verification before installation;
- lightweight optimized APK;
- guided import UI;
- contextual hints;
- real RanobeLib local-library scan;
- developer credit and project links;
- Dom Nekromanta EPUB easter egg;
- chapter 0 / illustration regression coverage.

Release criteria:

- no known data-loss bug;
- no known false overwrite of existing chapters;
- optimized APK <= 6 MiB;
- all mandatory release gates green;
- one clean Redmi 9 install test;
- one full import/update test using a real Dom Nekromanta EPUB.

Important migration rule:

Historical 0.3.1–0.4.2 test builds were signed by different CI certificates.
Moving from those builds to the stable channel requires one final uninstall and
reinstall. After the first stable release, this must never happen again.

## 0.5.1 / 0.5.2 — Field-fix window

Goal: fix real-device problems discovered after 0.5.0 without mixing in large
new features.

Allowed changes:

- crashes;
- storage permission failures;
- OEM-specific PackageInstaller issues;
- bad EPUB classification;
- incorrect chapter/image counts;
- update checker/install bugs;
- UI defects that block normal use;
- performance regressions.

Not allowed:

- large redesigns;
- new import formats;
- large library-management features.

A patch release should stay small and should not force users to relearn the UI.

## 0.6.0 — Import UX completion

Goal: make a first-time user able to import a book without knowing ReaderLB or
Android storage internals.

Planned work:

- [x] Android "Open with ReaderLB" / share-to-ReaderLB entry point for EPUB/TXT;
- [x] recent-file shortcut when Android granted persistent read access;
- [x] explicit import stages: analysis -> preparation -> verification -> writing -> finalization;
- [x] progress by chapter for long preparation work;
- [x] cancellable pre-write analysis;
- [x] clearer warning actions instead of a generic acknowledgement switch;
- [x] better recovery when the RanobeLib folder grant was revoked;
- [x] preserve in-progress import UI state across Activity recreation;
- [x] remember sensible user defaults without remembering dangerous overrides (currently the direct-import destination choice only).

Acceptance criteria:

- common import requires no more than file selection + one import action after
  the RanobeLib folder has been configured;
- no unexplained navigation jump;
- no technical Android/data text unless access actually needs attention;
- orientation/process recreation does not silently lose the selected file state.

## 0.7.0 — Library and management

Goal: turn the Library tab into a useful view of the actual RanobeLib local
library rather than a passive list.

Planned work:

- [x] search;
- [x] sorting/filtering;
- [x] title detail screen;
- [x] real cover display with bounded memory use;
- [x] chapter count/range and last local update;
- [x] distinguish ReaderLB-created titles from other local titles where possible;
- [x] refresh/reconnect actions;
- [ ] safe title actions only after their storage semantics are fully tested.

Destructive actions such as deleting a local title must not be introduced
without an explicit confirmation flow and dedicated tests.

## 0.8.0 — Large-book performance and reliability

Goal: make ReaderLB predictable on low-end phones and very large EPUB files.

Planned work:

- [x] stop retaining all large chapter images in memory at once;
- [x] stream/copy large assets through temporary storage;
- [x] define a memory contract for EPUB analysis: archive-backed chapter images must retain 0 image payload bytes in ParsedBook;
- [x] stress coverage includes 1200-chapter packages and an image-heavy chapter 0 low-memory parser fixture;
- [x] corrupt/truncated EPUB rejection tests;
- [x] cancellation plus stale temporary-asset cleanup coverage;
- benchmark parse/import time on Redmi 9;
- [x] prevent temporary-file leaks with parse-failure cleanup, screen disposal cleanup and stale-cache recovery.

Acceptance criteria:

- no OutOfMemoryError on the agreed large-book corpus;
- interrupted imports leave the pre-existing RanobeLib title valid;
- temporary files are removed after success and failure.

## 0.9.0 — Pre-1.0 hardening

Goal: freeze the feature set and remove accumulated rough edges.

Scope:

- [x] accessibility/content descriptions;
- [x] small-screen import layout audit (including full-width chapter-range fields);
- typography/spacing consistency;
- [x] empty/error/loading states with retry paths for EPUB analysis and library scanning;
- [x] update failure/recovery messaging for cancelled, blocked, conflicting, incompatible, invalid, storage, signature and integrity failures;
- [x] settings cleanup;
- corpus expansion;
- [x] final signing/update migration documentation;
- [x] copyable diagnostics limited to app/device/access state; no book titles, EPUB paths or reading-library content.

No major new feature should enter 0.9.x.

## 1.0.0 — Stable ReaderLB

Release only when:

- stable signing/update chain has already survived at least one real stable
  upgrade;
- no open known data-loss issue exists;
- import, incremental update, chapter images and library scan have regression
  coverage;
- Redmi 9 and one modern Android device both pass the release smoke test;
- optimized APK remains within the size budget;
- first-run flow no longer requires project-specific knowledge.

## Update policy after 1.0

Versioning:

- patch (1.0.x): bug/security/device compatibility fixes;
- minor (1.x.0): backwards-compatible features;
- major (2.0.0): only when storage/update behavior or core UX changes
  incompatibly.

Cadence:

- do not release by calendar just to increase the version number;
- accumulate low-risk fixes, but release data-loss/update failures quickly;
- keep test builds in Actions artifacts;
- only verified releases go to GitHub Releases and therefore to the in-app
  updater.

The permanent release key is part of ReaderLB's compatibility contract. Losing
or rotating it without a planned Android signing migration means losing seamless
updates.
