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

## Local-library export checks

ReaderLB 1.1.0 adds the reverse local-library path. Automated coverage must keep verifying:

- ReaderLB-created local package -> TXT / EPUB / FB2 round-trip;
- exact chapter-number preservation, including `0`, `0.5` and `001`;
- corrupt, missing-`data.txt` and unsafe chapter ZIP rejection;
- missing illustration diagnostics instead of silent loss;
- 2600-chapter streamed EPUB export;
- mobile PDF pagination and illustration decoding;
- atomic MediaStore publication in `Downloads/ReaderLB`;
- deletion of partial MediaStore output after writer failure;
- API 29 and API 35 runtime execution.

Before a stable 1.1.0 tag, manually verify on the Redmi 9:

1. Export one ReaderLB-created title and one ordinary RanobeLib-downloaded title.
2. Export EPUB, PDF, FB2 and TXT, then open the produced files in compatible readers.
3. Export a range containing unusual chapter numbers where available and confirm the original numbering is preserved.
4. Cancel a long export midway and confirm no broken file remains in `Downloads/ReaderLB`.
5. Export a large title (target: roughly 2000–3000 chapters) with illustrations and confirm there is no OOM, frozen UI or unrecoverable slowdown.
6. Confirm the bottom sheet fits the small screen, progress is visible, and Open/Share work after completion.

The Redmi 9 large-book check is a stable-release gate, not something emulator CI can replace.

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
