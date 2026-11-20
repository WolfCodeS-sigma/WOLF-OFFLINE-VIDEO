# Phase 13 — WOLF STORY ORIGINALITY & REFERENCE REVIEW

## Purpose

Phase 13 adds a deterministic, on-device review step for completed stories. It checks exact normalized phrase overlap between the final story text and permitted entries from WOLF HORROR KNOWLEDGE LIBRARY, records which source versions were supplied to the local story model, and highlights repeated six-word phrases inside the story.

The feature is an editorial aid. It does not certify originality, decide copyright ownership, determine plagiarism, or replace human review.

## User workflow

1. The story studio loads only knowledge-library entries currently allowed for story references.
2. When a plan or chapter is generated, the studio records the local source ID, title, source category, source label, SHA-256 content fingerprint, and the generation step that used it.
3. The ledger stores source metadata and fingerprints only. It does not make a second copy of the source text.
4. After every chapter is complete, the user reviews or edits the final story and runs **فحص أصالة القصة والمراجع المحلية**.
5. The reviewer compares the exact current final text with recorded source versions that are still present and still permitted. The user may additionally select up to three currently permitted entries in the existing reference selector for the current review.
6. A missing, disallowed, or changed source version is reported as unavailable or changed; the reviewer does not silently compare it as though it were the same source.
7. The approval action is blocked until the current text has a matching review result. Any subsequent edit invalidates that result and requires another review.
8. The final confirmation displays the report and requires the user to acknowledge its limited scope before saving the story as approved.

Older sessions created before Phase 13 have no source-use ledger. They can still be reviewed against references explicitly selected in the current screen, but the report warns when no usable sources are available.

## Local comparison method

- Text is tokenized for Arabic and English without splitting on Arabic combining marks or tatweel.
- Arabic alef variants are normalized to alef, alef maqsura is normalized to ya, and English case and punctuation differences are ignored.
- The reviewer creates overlapping six-word shingles. Windows dominated by common Arabic or English stop words are excluded to reduce accidental matches.
- Matching shingle hashes are grouped into continuous runs. The report includes the source, approximate story coverage, matching window count, longest continuous run, and a few normalized phrase samples.
- Severity is a deterministic triage label based on overlap coverage and continuous-run length. It is not a probability of copying.
- Repeated meaningful six-word phrases within the story are listed separately from cross-reference overlap.

## Bounds and failure handling

- The reference ledger is private app storage under filesDir/wolf_story_originality_review; it is separate from the Phase 11 story checkpoint format.
- The ledger is versioned and uses pending/backup atomic replacement so an interrupted write can recover the prior record.
- A session may record at most 40 distinct source versions; each generation operation records no more than three.
- A review compares at most 20 references, at most 1,000,000 characters from any one reference, at most 200,000 words from the story, at most 200,000 words from a single reference, and at most 500,000 reference words in total.
- Every truncation or source-limit condition is disclosed in the report. The reviewer never labels a truncated or unexamined source as fully checked.
- The story text is bounded by the existing four-million-character final-story limit.
- A source is compared only when its current full-text SHA-256 matches the recorded fingerprint. Missing or changed contents are listed as unavailable instead.
- A ledger write failure does not discard a successfully generated plan or chapter. The user receives a warning, and the later review reports the sources it could actually verify.
- All library reads and analysis run on the existing single background worker. No network calls, new Android permissions, background service, cloud storage, external API, paid product, or new runtime dependency is added.

## What this review can and cannot detect

It can find exact or near-exact word-sequence reuse inside local permitted references, including cases where punctuation, case, Arabic diacritics, tatweel, or common alef forms differ.

It cannot reliably identify paraphrases, translated copying, similar plot structures, shared horror tropes, character archetypes, or material from websites and books that are not present in the local permitted library. A lack of matches means only that no qualifying phrase overlap was found in the references actually checked. A match can also be coincidental or arise from conventional wording. Every meaningful match must be reviewed in context by the user.

The report therefore says explicitly that it is not a certificate of originality and not a plagiarism verdict.

## Tests

WolfStoryOriginalityReviewEngineTest covers long exact overlap, Arabic normalization, unrelated sources, common-word filtering, changed source fingerprints, duplicate source contents, internal repeated phrases, analysis bounds, review fingerprints, ledger merging, interrupted-write recovery, path validation, and reference-count limits.
