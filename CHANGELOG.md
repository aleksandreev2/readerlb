# Changelog

## 0.3.0-dev

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
