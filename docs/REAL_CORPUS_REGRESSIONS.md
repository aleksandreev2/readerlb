# Real-corpus regression contracts

ReaderLB uses private/local EPUBs to discover structural edge cases, but no
copyrighted book text or private source EPUB is committed to this repository.

Only minimal synthetic fixtures that reproduce the structure are allowed in
tests.

## Covered structures

### Service sections using numbered technical filenames

A real 1192-chapter EPUB uses a spine shaped like:

- title/translation service pages;
- `sec0000.xhtml` — a glossary/reference section, not chapter 0;
- `sec0001.xhtml` through `sec1192.xhtml` — real chapters 1–1192;
- `sec1193.xhtml` — translator afterword, not chapter 1193.

Regression contract:

- weak technical filename numbers must not override clear service labels;
- `Справочник` / glossary-style pages stay out of chapter numbering;
- `Послесловие переводчика` / translator-afterword pages stay out of chapter
  numbering;
- real numbered chapters remain unchanged.

### Partial source range with a real internal gap

A real EPUB labelled as chapters 100–191 contains 88 actual chapter documents:
100–118 and 123–191.

Regression contract:

- chapter numbers are preserved;
- ReaderLB reports the actual missing interval 119–122;
- it must not renumber the file to a contiguous sequence;
- it must not warn about chapters outside the declared 100–191 source range.

### Large-book scale

The private corpus includes books above 1100 and 2600 chapters.

Regression contract:

- parser/package tests retain original chapter numbers;
- large package generation remains covered by a 1200-chapter fixture;
- Android parsing uses file-backed image assets rather than retaining archive
  image payloads for the whole book.

### Image-heavy chapter zero

A real-world failure mode involved a title/service page mentioning chapter 0
before the true chapter-zero XHTML containing illustrations.

Regression contract:

- the service/title page never shadows the real chapter 0;
- chapter 0 retains all embedded illustrations;
- file-backed parsing keeps archive image payload bytes out of `ParsedBook`
  memory.

## Privacy rule

Never commit source EPUBs, chapter prose, cover art, translator notes or other
private corpus contents solely for regression testing. Reduce every discovered
bug to the smallest synthetic fixture that reproduces the structure.
