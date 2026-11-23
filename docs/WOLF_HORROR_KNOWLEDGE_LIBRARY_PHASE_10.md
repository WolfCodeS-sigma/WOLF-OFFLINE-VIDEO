# Phase 10 — WOLF HORROR KNOWLEDGE LIBRARY

## Status and scope

Phase 10 adds a private, offline TXT knowledge library to WOLF OFFLINE VIDEO. The library has its own storage engine and Android screen. It is separate from project records, the scene renderer, video encoder, narration, sound effects, and the Phase 9 cinematic motion pass.

The implementation is local-only. It adds no network permission, API, account, cloud storage, paid service, model weights, or third-party dependency.

## User-facing capabilities

- Import one or multiple .txt files from Android's system document picker.
- Write a story directly in the library editor.
- Add the current text from the main film editor as a user-submitted library entry without changing the project story.
- Create and use built-in or user-entered categories, named folders, and multiple tags.
- Track content kind separately from category: personal account, fictional story, verified information, informational reference, user-submitted content, or app-authored story.
- Store source label, source date, imported file name, and library-added timestamp.
- Store summary separately from the original and current story text.
- Maintain separate fields for characters, places, events, related stories, themes, and keywords. These fields are user-entered metadata; this phase does not claim AI extraction.
- Edit story text and metadata, delete an entry, and view a bounded preview of the preserved original text.
- Search title, content, source metadata, categories, folders, tags, summaries, and structured metadata. Story-body terms are held in a fingerprinted index; current catalog fields are matched against current metadata to prevent stale results after renaming or reclassification.
- Exclude any entry from future story-reference retrieval without deleting it, and expose APIs to retrieve either all allowed references or an explicit selected subset.
- Save app-authored stories only when an explicit final-approved flag is supplied to the future Phase 11 hook.
- Turn automatic saving of final app-authored stories on or off.

## Storage model and recovery behavior

The library root is the Android app-private directory filesDir/wolf_horror_knowledge_library/. Each entry has its own directory under entries/:

- original.txt is the first imported or written text and is never overwritten by edits.
- content-<sha256>.txt is the current working version.
- index-<sha256>.json is a per-entry token index tied to that content fingerprint.
- metadata.json contains catalog fields and points to the current fingerprint.

Content is streamed from the document reader into a temporary file while the SHA-256 digest and token set are calculated. The staged entry is renamed into the entries directory only after its files and metadata have been written. Updates create a new content version and update the index before atomically replacing the metadata pointer; the original text remains untouched. Old working versions are cleaned after the metadata update. A missing or invalid index is rebuilt from that entry's saved text.

The app does not load all story bodies into memory for listing or searching. It loads metadata and one small per-entry index at a time. Imported story content is not held in a global in-memory collection.

## Text limits and search behavior

- TXT only for this initial import phase; UTF-8 is required. Invalid UTF-8 is rejected instead of silently corrupted.
- A text entry is limited to 4,000,000 UTF-16 code units and 16 MiB after UTF-8 storage.
- Indexing is streamed in bounded character chunks; the index stores up to 60,000 distinct tokens per entry.
- Search normalizes case, Arabic alef/hamza forms, common Arabic diacritics, tatweel, and alif-maqsura. Multiple query terms are matched with AND semantics.
- Large entries are not fully injected into the editor. The editor loads at most 60,000 characters; larger entries allow metadata-only editing. The original-text dialog shows at most 40,000 characters.
- The screen renders at most 50 result cards at a time and adds more on demand, avoiding a large view tree when the library grows.
- Duplicate detection uses an exact SHA-256 fingerprint of the UTF-8 text stored by the library. Exact repeated content is reported and not inserted a second time. This is not fuzzy or semantic similarity detection.

## Story-reference and authorship boundaries

The searchStoryReferences API returns only entries whose includeInStoryReferences flag is enabled. The user can keep an entry in the library while excluding it from future reference retrieval.

The saveFinalAppAuthoredStory API is the stable auto-save hook for the local story-authoring component planned in Phase 11. It requires an explicit final-approved flag, saves only text supplied to that API as an approved app-authored story, and respects the library auto-save setting. Phase 10 does not create an AI story generator and does not claim that an existing manual story approval is app-authored. User-written text can be added separately as user-submitted content.

Imported source labels and source types are provenance metadata, not independent verification. A personal account remains a personal account; it must not be presented as a verified fact. Users are responsible for importing material they own or are allowed to use. The library does not scrape websites or automatically copy protected sources.

## Verification

JVM unit tests cover:

1. Persisting imported story text and source metadata.
2. Editing the working copy while preserving the original text.
3. Metadata-only updates reflected in search without changing either story-text version; explicit reference-subset selection; invalid UTF-8 rejection.
4. Duplicate detection on insert and edit.
5. Reference opt-out behavior.
6. Deletion and removal from search.
7. Custom categories and folders.
8. App-authored auto-save and user setting.
9. Arabic normalization.
10. Tokenization across reader chunk boundaries.
11. Rebuilding a missing index.
12. Rejecting blank content and preserving valid entries after failed edits.

CI is limited to Android lint and JVM unit tests. It does not build an APK, run an emulator, perform instrumentation testing, or validate the screen on a physical phone. Those checks remain reserved for the explicitly approved final device-test stage.

## Compatibility boundary

Phase 10 adds WolfHorrorKnowledgeLibrary.kt, WolfHorrorKnowledgeLibraryActivity.kt, one library JVM test file, a non-exported manifest activity, and a small entry point from MainActivity. It does not modify OfflineProductionEngine, the scene render pipeline, Phase 2–9 image/motion engines, audio rendering, checkpoint state, or project storage schema.

The library is implemented as a standalone component. Integration with the future story AI/reference retrieval workflow remains a separate Phase 11 task.
