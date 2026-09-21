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
