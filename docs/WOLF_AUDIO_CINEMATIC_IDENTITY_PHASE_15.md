# Phase 15 — WOLF AUDIO & CINEMATIC IDENTITY

## Goal
Complete the offline film's sound and approved WOLF CODE S identity as one coherent MP4 output, while keeping all earlier production, story, image, motion, chapter and checkpoint engines intact.

## Narrator recording
- The approved story remains the source of truth. Each recording session is bound to the exact approved story revision by SHA-256; editing the story invalidates the take and its segment ledger.
- Recording is saved as separate AAC/M4A parts in app-private storage. Stopping a part commits it; starting again records the next part rather than deleting previous parts.
- The segment manifest is written durably before assembly. AAC parts are joined in order into one M4A file, with track-format validation, monotonic timestamps, a 30-minute total limit and a 256-part safety limit.
- If the app is interrupted after a segment is committed, opening the project can rebuild the combined take from registered parts. It also detects a valid AAC segment renamed immediately before a process interruption but not yet entered in the manifest. Incomplete temporary recorder files are not treated as completed parts.
- The manifest records the segment count and merged-file length; recovery rejects a stale combined file after a partially completed append and rebuilds it from the full segment list.
- The combined take can be previewed and must be explicitly approved before film production uses it. Re-recording explicitly clears the old parts.
- Existing single-take recordings are copied (not moved) into the segment ledger when the user continues recording; the previously approved take remains intact until the new combined take is fully assembled.
- Recovery only registers contiguous parts that keep the complete narration within the 30-minute limit. An over-limit orphan cannot invalidate the previously saved take.

## Narration/audio processing
- One shared timeline places the optional three-second opening signature before narration and includes both in final film duration.
- Narration duration is authoritative when an approved voice take is used; it is not allowed to shorten the video mux to narration-only duration. If the optional intro would push the combined film beyond 30 minutes, production rejects the plan rather than silently trimming the narration.
- Local PCM conversion applies DC/very-low-frequency rumble reduction, a gentle noise-floor gate, voice-aware sound-effect ducking and a final soft limiter.
- Narration is mixed with generated effects into one AAC track. Narration-only and effects-only exports use the same normalized mixer path.
- Existing automatic quiet-tail stop remains enabled after five seconds of detected silence. This is not claimed to be word-level speech recognition or automatic transcript correction.

## WOLF SOUND FX
- Procedural, deterministic, local non-verbal effects remain generated from Arabic scene cues and event timelines.
- Supported textures include wind, heartbeat, knocks, footsteps, door creak, breath, whisper texture, impact, static, shriek, growl, wolf howl and non-human laugh.
- The opening intro receives its own wolf-howl/wind bed; scene events remain bounded to their scene timeline.
- No downloaded samples, external sound libraries, music or network services are introduced.

## WOLF HORROR TYPOGRAPHY
- Existing short contextual phrases are rendered inside scene frames with restrained fade/glitch timing.
- They are not subtitles or narration transcripts. Cue timing remains scene-local, not word-level synchronized to speech.

## Cinematic identity
- `WolfCinematicIdentity` centralizes the approved strings: WOLF CODE S, شفرة الذئب S, and بصمة الذئب السينمائية.
- The opening signature retains the dark wolf silhouette, red moon, red eye accents and brand title.
- The watermark combines the wolf mark and WOLF CODE S text in the rendered frame.
- Both intro and watermark remain optional project settings and are rendered locally.

## Preservation and compatibility
- Based on Phase 14 head `c7d58027b7eb2b288071a09d9590fc2f55980702`.
- No prior phase files are removed. Phase 2–14 render, story, image, motion, chapter and checkpoint implementations are retained.
- Changes are additive except the audio-export handoff in `OfflineProductionEngine`, which now sends narration and/or generated SFX through the shared mixer and muxes against the full film timeline.
- No new runtime dependency, network permission, API, cloud storage, paid service, large model, APK, emulator or device test is introduced.
- The story fingerprint proves which approved text revision a recording is associated with; it does **not** prove that the speaker read every word correctly. No speech-to-text model is bundled or claimed.

## Verification
- JVM tests cover segment naming/duration bounds, story-revision invalidation, continuation approval rules, mixer ducking/noise gate, and intro/narration/video timeline.
- Android Lint and JVM Unit Tests run in GitHub Actions.
- Actual MediaRecorder, MediaMuxer, AAC codec and visual playback behavior still require a later device test, which remains deferred until the complete Offline app and explicit user approval.
