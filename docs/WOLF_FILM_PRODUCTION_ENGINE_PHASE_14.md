# Phase 14 — WOLF FILM PRODUCTION ENGINE

## Scope

Phase 14 completes the offline film-production orchestration while preserving the existing scene, image, motion, story, voice, and sound engines. The work is additive and starts from the accepted Phase 13 branch.

- Story segmentation remains bounded to at most 12 scenes.
- Each scene receives exactly three shot slots.
- Film duration presets are 5, 10, 15, 20, 25, and 30 minutes; custom duration remains available within the same range.
- Long films are split into production chapters of at most approximately five minutes.
- Chapters are planned at shot boundaries where possible. If a single shot exceeds the chapter limit, it is split into contiguous frame ranges with its original shot index, local offset, and original shot duration preserved.
- A resumed segment continues the original camera/motion progress instead of restarting the shot animation.
- Chapter checkpoints retain the existing atomic local storage format and now serialize optional shot metadata. Old checkpoint records remain readable because missing shot fields default to legacy values.
- Each shot segment is rendered to its own deterministic local artifact. A segment is reused only when its encoded frame count matches the plan; a missing or incomplete segment alone is rendered again. This lets a failed chapter resume at the failed shot rather than repeating its successful shots.
- Completed chapter artifacts are reused when the production fingerprint and plan match. Interrupted or failed chapters can be retried without re-rendering completed chapters or valid shot segments.
- Shot segments are merged into each chapter in story order; chapters are then merged in chapter order. Both merge levels verify the exact expected frame count before accepting the output.
- Storage preflight accounts for overlapping shot-segment, chapter, merged, and final-export files.
- Existing image-render failure fallback, procedural scene rendering, reduced-resolution export fallback, audio mixing, and quality inspection remain in place.

## Compatibility boundary

No previous phase is deleted or replaced. The change extends the long-film engine, its checkpoint serialization, the chapter path in the offline production engine, and the existing main-screen duration controls. Non-chapter rendering keeps its original path. No new dependency, network permission, cloud service, paid API, model download, or background service is added.

## Verification

CI is limited to Android lint and JVM unit tests. Added tests cover exact frame conservation, 12-scene/36-shot planning, shot-resume offsets, bounded chapter duration, sequential chapter numbering, intro placement, invalid plans, and additional storage headroom for shot-level recovery artifacts. No APK, emulator, instrumentation, or phone runtime test is part of this phase.
