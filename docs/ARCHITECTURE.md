# Architecture

ReaderLB is a single-module Android application. The architecture is deliberately
small: the app performs all book conversion locally and uses Android's Storage
Access Framework for user-authorized filesystem access.

## Main flows

### Import

```text
EPUB / TXT / ZIP URI
        ↓
ImportRepository
        ↓
EpubArchiveParser / PlainTextParser
        ↓
ParsedBook
        ↓
RanobeLibPackageBuilder
        ↓
verify generated package
        ↓
RanobeLibExporter
        ↓
user-selected RanobeLib book tree
```

The parser never writes directly into RanobeLib storage. A complete package is
built and verified first.

### Existing-title update

```text
new ParsedBook
      +
existing local title
      ↓
RanobeLibUpdatePlanner
      ↓
update plan
      ↓
RanobeLibUpdateTransaction
      ↓
temporary/staged writes
      ↓
commit or recovery
```

The update path is append-oriented. Existing chapters are not silently
overwritten.

### Portable ReaderLB package

`ReaderLbTransferManager` creates and verifies `.readerlb.zip` packages used
to move an already prepared local title between devices.

A package contains a versioned ReaderLB manifest plus the exact title directory
that will be installed. Paths are validated before extraction and the generated
RanobeLib package is verified again before installation.

### Local library

`RanobeLibLibraryScanner` reads the user-authorized book directory and produces
lightweight local-title summaries for the Home and Library screens.

Large `chapters.json` files are streamed instead of loading the entire chapter
model into memory.

### Local-library export

```text
user-authorized RanobeLib title
        ↓
RanobeLibLocalBookReader
        ↓
LocalExportBook / LocalExportChapter / LocalExportBlock
        ↓
LocalBookEpubWriter / LocalBookPdfWriter / LocalBookFb2Writer / LocalBookTxtWriter
        ↓
MediaStore pending item
        ↓
Downloads/ReaderLB
```

The reverse reader loads title metadata and the chapter index first, then opens
individual chapter ZIPs only when a writer reaches that chapter. Illustration
payloads are copied on demand. PDF images are temporarily spilled to cache and
decoded with sampling so the whole image set is never retained on the Java heap.

Chapter archives are treated as untrusted input: entry names are leaf-only,
duplicate entries are rejected, entry counts and document/image reads are
bounded, and unknown document nodes produce diagnostics instead of disappearing
silently.

Output publication is transactional at the MediaStore boundary. A download
starts as `IS_PENDING=1`, becomes visible only after the format writer succeeds,
and is deleted if writing fails or is cancelled.

## Updates

`UpdateManager` treats GitHub Releases as the stable update source.

Before handing an APK to Android it checks:

- release/version ordering;
- downloaded SHA-256 when supplied by the release;
- Android package name;
- signing compatibility.

Android's PackageInstaller remains the authority that performs the actual
installation.

## Release notifications

Firebase Cloud Messaging is optional and disabled until the user opts in.

`ReleaseMessagingService` receives release data messages and creates the local
Android notification. Tapping it opens ReaderLB's update screen; ReaderLB still
queries GitHub Releases before offering an update.

FCM is therefore a notification transport, not the update source.

## Storage boundaries

ReaderLB has no broad filesystem permission. Persistent access is represented by
a URI permission granted by the user through Android's Storage Access Framework.

The important trust boundaries are:

1. untrusted imported EPUB/TXT/ZIP content;
2. user-authorized RanobeLib storage;
3. GitHub Releases metadata and APK downloads;
4. optional Firebase release messages.

Parsing, ZIP extraction and update code should treat all externally supplied
names, paths and metadata as untrusted.

## Source layout

- `app/src/main/java/com/readerlb/app/MainActivity.kt` — Compose application
  shell and current screen orchestration.
- `importer/` — parsers, package building, validation, export and update
  transactions.
- `storage/` — preferences, history and local RanobeLib library scanner.
- `export/` — reverse local-title reader, common export model and EPUB/PDF/FB2/TXT writers.
- `update/` — GitHub release discovery, APK verification and installation.
- `notifications/` — opt-in FCM release notification handling.
- `ui/theme/` — application theme.

The UI is currently concentrated in `MainActivity.kt`. Future UI refactoring
should preserve importer/storage boundaries rather than moving filesystem logic
into composables.
