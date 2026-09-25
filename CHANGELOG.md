# Changelog

## Unreleased — 1.1.0

Local-library export.

- Added EPUB 3, PDF, FB2 and UTF-8 TXT export for local RanobeLib titles, including titles downloaded by RanobeLib itself and titles created by ReaderLB.
- Added a mobile export bottom sheet with format selection, optional chapter range, cover/illustration controls, live chapter progress and cancellation.
- Exported books are published atomically to `Downloads/ReaderLB`; failed or cancelled writes remove the unfinished MediaStore entry.
- EPUB export includes metadata, cover, navigation, one XHTML file per chapter, mobile-friendly CSS and embedded chapter illustrations.
- PDF export uses a mobile page size, paginated text, chapter headings and bounded image decoding through temporary files to reduce peak memory use.
- FB2 export includes metadata, chapter sections, cover and embedded illustrations; generated output is XML-parsed in tests.
- Added source badges and `Все / ReaderLB / RanobeLib` filtering to the local library.
- Local-title actions now use the actual on-disk folder name, so non-ReaderLB RanobeLib titles remain shareable and removable even when their folder name differs from metadata.
- Unknown RanobeLib document nodes and missing illustrations are surfaced as export warnings instead of being silently ignored.
- Added regressions for chapter `0`, `0.5`, `001`, corrupt/unsafe chapter ZIPs, missing images, cancellation, ReaderLB package round-trips and a streamed 2600-chapter EPUB.
- Added Android runtime coverage for PDF generation with illustrations and successful/failed MediaStore publication on API 29 and API 35.

## 1.1.0

Local-library export.

- Added export of local ReaderLB and ordinary RanobeLib titles to EPUB 3, PDF, FB2 and UTF-8 TXT.
- Export can use all chapters or a numeric range while preserving source chapter numbers such as 0, 0.5 and 001.
- EPUB includes metadata, clickable navigation, per-chapter XHTML, cover and streamed illustrations.
- PDF uses a mobile-oriented paginator with bounded image decoding instead of loading the whole book into memory.
- FB2 includes chapter sections and embedded images; generated output is covered by XML well-formedness tests.
- Local titles now show ReaderLB/RanobeLib source badges and can be filtered by source.
- Added a mobile bottom-sheet export flow with format/content controls, progress, cancellation, preview, warnings, open and share actions.
- Finished files are published atomically to Downloads/ReaderLB through MediaStore; partial output is removed after errors or cancellation.
- Added regressions for ReaderLB package round-trips, exact chapter numbering, corrupt chapter ZIPs, missing images, 2600-chapter EPUB export, PDF illustrations, and MediaStore cleanup on API 29/35.

## 1.0.1

In-app update experience polish.

- Added a dedicated update dialog shared by Home, Settings and release-notification entry points.
- Added real APK download progress and a separate verification stage before installation.
- New-version details now show the installed version, target version, release notes and APK size when GitHub provides it.
- Downloaded updates remain protected by SHA-256, package-name and signing-certificate verification before Android receives the APK.
- Installation permission is requested only when the verified APK is ready to install.
- Replaced text characters used as pseudo-icons in touched UI with real Material icons.

## 1.0.0

First stable ReaderLB release.

- Finalized EPUB import hardening, configurable limits, cancellation and temporary-file cleanup.
- Kept Android 11+ imports safe by using persisted RanobeLib access when available and portable ZIP fallback otherwise.
- Fixed Android system Back navigation across settings, import, title details and onboarding.
- Added explicit regression coverage for chapter 0 with embedded illustrations.
- Added PackageInstaller session runtime smoke coverage on supported Android API levels.
- Added an automated release-signed in-place upgrade smoke on Android 15 to verify the permanent signing chain before stable publication.
- Release-candidate APKs remain SHA-256 verified and are retained as short-lived CI artifacts.

## 0.9.6

Portable-package integrity hardening.

- ReaderLB portable-package inspection no longer depends on the manifest being the first ZIP entry.
- ReaderLB package manifests are size-bounded instead of being read into memory without a limit.
- Portable packages reject unsafe, nested, foreign and duplicate files before installation.
- Ordinary non-ReaderLB ZIP files still fall back to the normal ZIP/EPUB path instead of being rejected by transfer-only validation.
- RanobeLib package verification now confirms that first/last chapter metadata matches the actual chapter list.

## 0.9.5

Release notifications and repository hardening.

- Added opt-in Firebase Cloud Messaging support for stable-release notifications.
- FCM auto-initialization stays disabled until the user explicitly enables release notifications.
- Android 13+ notification permission is requested only when the user enables the feature.
- Release notifications open ReaderLB's update screen and force a fresh GitHub Releases check.
- Added professional repository contribution, security, issue, pull-request and dependency-update templates.
- Documented the new main/feature-branch workflow and Firebase notification architecture.

## 0.9.4

Portable ReaderLB package detection hotfix.

- ReaderLB now inspects every selected `.zip` for the portable-package manifest instead of requiring the exact `.readerlb.zip` filename.
- Packages renamed by Android, MIUI, messengers or file providers to names such as `*_readerlb.zip` are recognized and open on the dedicated ReaderLB package screen.
- Ordinary ZIP/EPUB archives still fall back to the existing EPUB parser when no ReaderLB manifest is present.
- The import screen now lists portable ReaderLB packages among supported inputs.

## 0.9.3

Local-title actions and portable ReaderLB packages.

- Simplified the title-detail screen back to useful local information instead of mirroring unrelated RanobeLib tabs and metadata.
- Added «Поделиться новеллой»: ReaderLB creates a streamed `.readerlb.zip` containing the exact local title files, chapters, cover and illustrations.
- A shared `.readerlb.zip` can be opened on another phone in ReaderLB and installed directly into that phone's RanobeLib library without reparsing an EPUB.
- Portable packages include a versioned manifest, strict path validation, package verification and a 2 GB extraction ceiling.
- Added «Удалить новеллу» with an explicit destructive confirmation; successful deletion immediately removes the title from ReaderLB's cached library view.
- FileProvider sharing is limited to ReaderLB's dedicated cache/share directory and stale share packages are cleaned automatically.

## 0.9.2

Title-detail and import-layout polish.

- Local title cards on the Home screen are now fully tappable and open the same title-detail screen as cards in Library.
- Rebuilt the title-detail screen around the RanobeLib local-title composition: cover hero/background, large cover, local status/action row, title tabs, metadata strip and local ReaderLB information block.
- Restored true symmetric centering for the empty import body instead of the previous asymmetric top/bottom padding that pulled the card upward.

## 0.9.1

Local-library scan hotfix for Android/MIUI devices.

- Replaced the blocking root `DocumentFile.listFiles()` library enumeration with direct `DocumentsContract` cursor traversal so title folders can be processed progressively.
- Local-library progress now starts updating as soon as title folders are discovered, even before the total directory count is known.
- ReaderLB persists the last successful local-library snapshot and keeps those title/chapter counts visible while a background rescan is running.
- Switching or disconnecting the RanobeLib folder clears the matching cached snapshot safely.
- The empty import group is shifted slightly above mathematical center for better visual balance on tall phones while remaining scrollable.

## 0.9.0

Dark UI, finite local-library refresh and pre-1.0 interface hardening.

- Reworked the local RanobeLib scan into a single-flight refresh: repeated taps no longer start parallel scans, progress shows scanned/total titles, and a stuck Android document provider times out instead of leaving an endless spinner.
- Large local `chapters.json` files are summarized with a streaming parser so thousands of chapters do not need to be materialized as one JSON array in memory.
- ReaderLB now uses a RanobeLib-inspired dark palette across the app and dark Android system/window surfaces.
- The official ReaderLB logo supplied for the project is used inside the app and as the launcher icon.
- The empty import experience is genuinely vertically centered while remaining scrollable on small displays.

- Expanded chapter-range controls now use full-width «С главы / По главу» fields instead of two cramped side-by-side inputs on narrow phones.
- Removed the non-functional overflow glyph from import-history cards.
- Added meaningful accessibility descriptions to actionable settings controls and real book covers.
- Settings now describe RanobeLib access in user terms instead of exposing a raw Android content URI, and the low-level package-format explanation was removed from the main settings screen.
- Help now includes copyable privacy-safe diagnostics with ReaderLB version, Android/API, device model and access/update state only; it excludes book titles, EPUB paths and library contents.
- Installer/update failures now explain cancellation, blocked unknown-source permission, storage shortage, signing migration, integrity failures and package conflicts in user-facing language.
- Failed EPUB analysis and failed local-library scans now expose an explicit retry action instead of leaving the user at a dead end.
- Real-corpus-derived regressions now cover numbered service sections, translator afterwords and a partial 100–191 source with the real internal gap 119–122; source EPUB contents remain private.

## Unreleased — 0.8.0

Large-book and low-memory reliability work in progress.

- EPUB chapter illustrations are streamed into temporary files during Android parsing instead of retaining all archive image payloads in ParsedBook memory.
- Chapter-package generation streams file-backed illustrations directly into RanobeLib ZIPs while preserving the legacy in-memory path for compatibility.
- An image-heavy chapter 0 regression fixture enforces zero retained archive-image payload bytes in the parsed book model.
- Temporary EPUB assets are cleaned when leaving/replacing an import, after parse failures, and by a stale-cache sweep after abandoned process sessions.
- Added truncated/malformed EPUB rejection coverage and moved the optional private EPUB corpus smoke test onto the same streamed-image path used by Android.

## Unreleased — 0.7.0

Local-library usability work in progress.

- Library search matches local title names and slug IDs.
- Library sorting supports recent local updates, alphabetical order and chapter count.
- ReaderLB-created local titles are detected from chapter metadata and labelled separately from other RanobeLib local titles.
- Tapping a library card opens a read-only detail screen with the real local cover, chapter count/range, local update time, source and slug ID.
- Library cover decoding remains downsampled to avoid loading full-resolution covers into memory on low-end devices.

## Unreleased — 0.6.0

Import UX work in progress.

- Android file managers can open or share EPUB/TXT/compatible ZIP files directly to ReaderLB.
- The selected file, edited title, chapter range, destination choice and current tab survive Activity recreation; persisted document grants are reused where Android allows it.
- Pre-write file analysis can be cancelled without exposing a dangerous cancel action during RanobeLib writes.
- ReaderLB offers a one-tap shortcut to the last persistently granted document without copying the source EPUB into app storage.
- Import now reports real stages: preparation, verification, writing and finalization.
- Long imports report real chapter preparation progress instead of an indeterminate spinner only.
- Warning acknowledgement is an explicit «Я проверил — продолжить» action instead of an ambiguous switch.
- Saved RanobeLib folder access is validated on startup; revoked grants are discarded instead of failing later during import.

## 0.5.0

Usability, lightweight build, branded EPUB easter egg and update foundation.

- ReaderLB detects EPUB editions carrying the official «Дом Некроманта» translation credit/team link and shows the post-import easter egg «Приятного чтения, товарищ! (с) Некромант».
- Settings now credit developer dollar and link to https://t.me/domnekromanta.
- Added a lightweight GitHub Releases update checker: optional automatic daily checks, manual checks in Settings, APK download and Android PackageInstaller handoff.
- Update downloads verify package name and signing-certificate continuity before Android is asked to install them.
- Fixed the CI signing-path bug that made historical 0.3.1–0.4.2 test APKs use different debug certificates; current CI pins and verifies one explicit test certificate.
- Added a permanent release-signing workflow and guards against tag/version mismatches and wrong release certificates.
- Replaced the huge Material Icons Extended dependency with Material Icons Core.
- Release builds now enable R8/resource shrinking; the optimized test APK is about 2.3 MiB instead of roughly 55 MiB, with a 6 MiB CI size budget.
- Import is now a guided mobile flow: settings appear only after file analysis, chapter range is collapsed by default, contextual hints can be replayed, the bottom navigation is hidden during import, and the action shows the selected chapter count.
- The Library screen now scans the granted RanobeLib `book` directory and shows real local titles, chapter counts/ranges and covers instead of pretending import history is the library.
- EPUB/TXT files can now be opened or shared directly to ReaderLB from compatible Android file managers; ReaderLB jumps straight to analysis instead of making the user select the same file again.
- Android 12+ can use the platform's no-extra-confirmation self-update path when all system conditions are met; older/OEM flows still fall back to normal PackageInstaller confirmation.

## 0.4.2

Filename-range and import-completion patch.

- Underscore-only split filenames such as `главы_0_50_89.epub` are now interpreted as chapter 0 plus chapters 50–89, matching the real filename produced for the tested book.
- The intentional 1–49 gap no longer produces `CHAPTER_GAPS` or `SOURCE_RANGE_MISMATCH` warnings for that filename form.
- Successful imports now stay on the import screen so the completion result remains visible instead of immediately navigating away.
- Added an explicit "Открыть библиотеку" action after a successful import.
- Added a regression test for the exact `главы_0_50_89.epub` naming pattern.

## 0.4.1

Chapter-zero regression fix.

- A title page that merely mentions "Глава 0" can no longer replace the real chapter 0.
- Illustrated chapter 0 now keeps all of its embedded images instead of importing the title page as chapter 0.
- Split filenames such as `главы_0_50-89.epub` are understood as chapter 0 plus chapters 50–89, so ReaderLB no longer reports the intentional 1–49 gap as corruption.
- Added a regression test matching the real EPUB structure that exposed the bug.

## 0.4.0

EPUB illustration milestone.

- Embedded chapter illustrations are extracted from EPUB and preserved in reading order.
- ReaderLB writes the same local RanobeLib image structure observed in real downloaded chapters: image files live beside `data.txt` inside the chapter ZIP, and the document uses an `image` node whose `attrs.images[].image` value matches the image filename stem.
- JPEG, PNG, WebP and GIF chapter images are supported.
- Chapter-package verification checks that every image node has a real non-empty file and that no unreferenced image files are left in the ZIP.
- Missing/remote/unsupported EPUB images produce explicit warnings instead of being silently dropped.
- The import preview shows how many illustrations will be transferred.

## 0.3.1

Small usability patch after device testing.

- The import-screen title is now truly centered independently of the back button.
- The back arrow on the import screen is functional.
- The RanobeLib folder picker opens at the expected `Android/data/ru.libappc/files/book` location when the Android file provider accepts an initial URI.
- CI attempted to reuse one debug signing key for seamless updates. A runner-path mismatch was later found to make this ineffective for 0.3.1–0.4.2 artifacts; 0.5.0 fixes and verifies the signing path explicitly.
- Existing Android 11+ storage restrictions still mean ReaderLB cannot silently grant itself access to another app's `Android/data` directory.

## 0.3.0

Incremental-update milestone.

- Existing local RanobeLib titles are updated instead of duplicated when the generated title identity matches.
- Exact overlaps keep the already installed chapter ID, branch metadata and ZIP.
- Only missing chapter numbers are added; partial ranges such as 431–700 over an existing 1–653 title add only 654–700.
- Update writes are transactional: new ZIPs are staged and SHA-256 checked, metadata is backed up, and `info.json` is published last.
- A recovery journal handles process death during an update and either completes cleanup or restores the last known-good metadata.
- A committed update is accepted only when all newly added chapter ZIPs are present.
- Corrupt recovery journals stop the update before existing title data is changed.
- Update-planner regression coverage includes 3000-chapter libraries.
- Onboarding now uses the supplied three-panel artwork, sliced into three local assets.

## 0.2.0

Reliability milestone for EPUB → local RanobeLib import.

- EPUB 2.0 and EPUB 3.0 parsing.
- Namespace-prefixed OPF/container support.
- EPUB navigation/NCX-aware chapter numbering.
- Preserves RanobeLib-compatible numbers such as `0`, `0.5`, `001`, `2.91` and partial ranges such as 431–480.
- Detects missing chapters, duplicate chapter numbers and conflicting numbering sources.
- Detects filename-declared ranges that disagree with EPUB contents.
- Warns when chapter illustrations would be omitted.
- Warns when multiple TOC chapters point into one XHTML document.
- Keeps common service pages out of the chapter list without discarding numbered chapters whose title contains words such as “Справочник”.
- Package verifier checks `info.json`, `chapters.json`, every chapter ZIP and `data.txt` before export.
- Direct RanobeLib copy cleans up a newly created partial title after a failed write.
- `info.json` is copied last so RanobeLib does not index a half-written title.
- Stress coverage includes a 1200-chapter package.
- CI now requires unit tests, Android Lint and APK build to succeed.

### Known 0.2 limitations

- Updating an already existing local RanobeLib title is deliberately blocked; safe merge/update is the 0.3 milestone.
- Inline chapter images are detected but not yet transferred.
- EPUBs containing several logical chapters inside one XHTML file are detected and require confirmation; automatic splitting is not yet implemented.
- PDF and DOCX import are not part of 0.2.
