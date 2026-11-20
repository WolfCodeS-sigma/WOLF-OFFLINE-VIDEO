# Phase 12 — WOLF STORY LENGTH & STRUCTURE ENGINE

## Purpose
Add a deterministic, fully offline length and narrative-structure layer to the Phase 11 story studio. It guides the local model and reports measurable text length without pretending to semantically judge story quality.

## Features
- Unicode-aware Arabic and English word counting; combining marks and Arabic tatweel do not split words.
- Deterministic word budgets for each planned chapter. Allocations sum exactly to the requested total; short stories group all eight narrative beats across the available chapters, including a compressed complete arc for a one-chapter story.
- Eight ordered narrative beats: hook, setup, inciting incident, rising action, midpoint, crisis, climax, and resolution.
- Configurable per-chapter tolerance, with explicit minimum and maximum word ranges.
- Plan-level and chapter-level prompt guidance passed into the existing Phase 11 local model.
- Quantitative review report: actual/target words, completion percentage, chapter progress, per-chapter under/over-length findings, and estimated narration/reading time at 120–160 words per minute.
- Debounced live report in the existing story studio; word analysis runs on the existing background worker and stale queued reports are skipped to keep the UI responsive.

## Boundaries
- No model, dependency, network permission, paid service, API, cloud service, or background service added.
- No changes to Phase 2–10 render, motion, sound, voice, project, checkpoint, or knowledge-library implementations.
- Phase 11 model import, local inference, resumable sessions, library retrieval, and explicit approval/save workflow remain in place.
- This is a quantitative planner and structural beat schedule. It cannot verify that a model actually wrote a convincing hook, twist, climax, or coherent character arc. Human review remains required.
- Estimated duration is a rough range based on 120–160 words per minute, not a guaranteed video duration.
- CI is limited to Android lint and JVM unit tests; no APK, emulator, instrumentation, or device test.

## Tests
Covers Arabic/English tokenization, diacritics/tatweel, deterministic exact-sum allocations, all eight beats, input bounds, prompt guidance, whole-text reporting, chapter-level under-length detection, and aggregate chapter reports.
