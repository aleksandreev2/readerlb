# ReaderLB testing policy

The importer must never silently renumber, drop, or invent chapters.

## Regression tests

Every EPUB parser bug gets a permanent synthetic regression fixture. CI runs the parser tests before building the APK.

Current fixtures cover:

- EPUB 2.0 and EPUB 3.0;
- normal OPF tags and namespace-prefixed OPF/container tags;
- `chNNNN.xhtml` and `chapterNNNN.xhtml`;
- partial ranges such as 431–480 without renumbering to 1–50;
- service pages mixed into the spine;
- `secNNNN.xhtml` books whose real chapter number is in the chapter text;
- non-Russian/non-English chapter headings;
- missing chapter numbers / gaps;
- mismatch between filename chapter number and chapter text;
- filename-declared ranges that do not match the EPUB contents;
- fallback numbering only when the EPUB contains no reliable numbering.

## Android runtime tests

For changes that can affect app startup, storage integration, Android APIs,
notifications or installation flows, CI executes the instrumented suite on:

- API 29 — ReaderLB's minimum supported Android version;
- API 35 — a modern scoped-storage and notification-permission environment.

A runtime failure is treated as a real regression until the test or application
behavior is understood. CI setup failures such as transient emulator downloads
should be fixed in the workflow rather than hidden by disabling the test.

Before the release candidate, verify these flows on a small screen and on Android 11+:

1. Open a corrupt EPUB, see the analysis error, and use “Повторить анализ”. Then choose a valid EPUB and confirm that the preview appears.
2. Cancel analysis of a large EPUB, confirm that progress stops, and check that temporary EPUB and image files are removed from app cache.
3. Remove the saved RanobeLib folder permission, restart ReaderLB, and confirm that import offers a portable ZIP without opening a blocked `Android/data` folder picker.
4. With an existing persisted RanobeLib permission, restart ReaderLB and confirm that direct import and the local library still work.
5. On Redmi 9 dimensions, check the empty import screen, preview, warning, error, library, settings, and update controls for clipping and reachable actions.
6. Install a release-signed update over the previous release-signed build; verify SHA-256, certificate identity, and the in-app updater result.

## Portable package checks

Portable `.readerlb.zip` regression coverage includes:

- manifest detection independent of filename suffix and ZIP entry ordering;
- ordinary ZIP fallback when no ReaderLB manifest exists;
- path traversal / foreign-path rejection once a ReaderLB manifest is present;
- duplicate-entry rejection;
- bounded manifest size;
- package extraction into the expected title directory;
- first/last chapter metadata matching the actual chapter list.

## Corpus checks

Before parser releases, use a varied local corpus rather than a single successful EPUB. The private corpus itself is not committed to this public repository.

A corpus check should verify at minimum:

1. detected chapter count;
2. first/last chapter number;
3. gaps and duplicate numbers;
4. title/author/cover extraction;
5. exclusion of title/translation/navigation/reference pages;
6. compatibility with both EPUB 2 and EPUB 3;
7. namespaced XML;
8. books with many embedded images;
9. partial chapter ranges.

PDF is a separate import pipeline and should have its own corpus/tests when PDF import is implemented.
