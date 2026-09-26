# Direct access to RanobeLib

ReaderLB's known library layout is
`/storage/emulated/0/Android/data/ru.libappc/files/book/<title>/`.
Title directories contain `info.json` and `chapters.json`. The privileged
service also checks removable volumes for the same package layout, and it
rejects a nonempty library with no valid title directory.

## Platform decision

- On Android 10, ReaderLB retains its system folder picker and persisted SAF
  permission path.
- On Android 11 and newer, Android excludes `Android/data` from
  `ACTION_OPEN_DOCUMENT_TREE`. `MANAGE_EXTERNAL_STORAGE` explicitly does not
  grant access to other applications' app-specific directories. ReaderLB
  does not request that permission.
- Shizuku's UserService runs as shell (UID 2000) when started using ADB, or
  root when started using root. ReaderLB binds a service with a narrow file
  interface and probes the actual RanobeLib directory before reporting a
  connection. Device vendors can restrict shell access, so a successful
  Shizuku authorization alone is never treated as proof of file access.
- Android 16's published behavior changes do not offer a new ordinary app
  permission for another app's `Android/data` directory. The same capability
  probe applies to API 30 through 36.

Sources: [Android document tree restrictions](https://developer.android.com/training/data-storage/shared/documents-files),
[all-files access limits](https://developer.android.com/training/data-storage/manage-all-files),
[Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16),
[Shizuku UserService and shell UID](https://github.com/RikkaApps/Shizuku-API),
[official Shizuku setup](https://shizuku.rikka.app/guide/setup/).

## Implementation

`ShizukuFileService` only accepts relative paths below the detected book
directory. It rejects traversal, absolute paths, symlinks and unsupported
file modes. It runs no user-supplied commands. `RanobeLibDocumentsProvider`
adapts this file interface to the existing document streams used by library
scanning, cover display, direct import, deletion and EPUB/PDF/FB2/TXT export.
The provider is protected by Android's signature-level `MANAGE_DOCUMENTS`
permission and is not advertised in the system picker. SAF remains the first choice when a
persisted permission exists. Portable output remains available at all times.

Shizuku's wireless-debugging service must be restarted after a device
reboot on unrooted devices. ReaderLB rechecks on resume and after a permission
result, then scans the library automatically when direct access becomes
available. Some vendor ROMs may deny shell access to the target directory;
the probe reports that case rather than claiming a connection.

The optional `ShizukuIntegrationAndroidTest` requires a running authorized
Shizuku and a `test-title` fixture under the known book directory. Run it
with instrumentation argument `readerlb_shizuku_fixture=true`; ordinary CI
runs skip this fixture-dependent test. It scans the title and verifies a
temporary file can be written, read, renamed and deleted through the live
UserService.
