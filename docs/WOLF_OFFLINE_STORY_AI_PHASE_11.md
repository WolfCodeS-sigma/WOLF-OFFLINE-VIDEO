# Phase 11 — WOLF OFFLINE STORY AI

## Purpose
A standalone Arabic horror-story authoring studio using an on-device language model. It creates an outline (logline, characters, secrets, ending, continuity notes and chapter titles), then writes one chapter per user action so long stories can be resumed safely.

## Local model runtime
- Uses Google's LiteRT-LM Android Kotlin runtime, pinned to version 0.18.0.
- The user imports a compatible .litertlm model file. The app copies it to app-private storage; model weights are never bundled in the APK and are never downloaded by the app.
- Inference is on-device. No Internet permission, API key, account, cloud endpoint, paid API, remote model call, or background service is added.
- Engine initialization and generation run on a single background executor. One owner controls the engine and conversation and closes them in dependency order.
- CPU is the default backend. GPU/NPU are not forced because hardware support varies.
- Renaming a GGUF or another model file to .litertlm does not convert it. Only a genuinely compatible LiteRT-LM model can load.

## Writing workflow
1. The user enters title, premise, horror type, setting, protagonist, tone, ending direction, and a target of 500–50,000 words.
2. The local model creates a structured plan with logline, characters, secrets, ending, continuity summary and chapter titles.
3. The user generates one chapter per action. Each accepted chapter is checkpointed locally before success is shown.
4. Continuity notes and a bounded excerpt of the previous chapter are supplied to the next chapter prompt.
5. The complete story text is editable and requires explicit user approval.
6. Only the approved final text is passed to Phase 10's saveFinalAppAuthoredStory API. The existing library auto-save preference is respected.

## Horror coverage and originality
The UI supports psychological horror, supernatural horror, mystery/investigation, hallucination/perception, realistic personal-account style, and folklore. Prompts require original writing, gradual escalation, coherent motivations, a planned ending and no copying from references. Reference retrieval uses only Phase 10 entries whose reference opt-in is enabled; up to three contextual excerpts are supplied. This is lexical retrieval, not embedding search. It does not guarantee plagiarism detection or factual verification. Personal accounts and fiction must not be presented as verified facts.

## Long-story behavior and resource limits
- The target is divided into roughly 800-word chapters (1–63 chapters across the supported range).
- Target word count is an aspiration, not a guaranteed exact count. Output depends on the chosen model, its context/token limits, and device capacity.
- Each chapter is bounded to 100,000 characters and checkpointed. A short or failed chapter is rejected and is not appended.
- A 50,000-word work requires many separate generation actions and may exceed the practical speed or memory capacity of lower-end phones. There is no cloud fallback.
- Model loading may be slow or fail on unsupported or memory-constrained devices. Failure is reported; the app does not silently substitute a fake AI or an online model.
- No model weights are bundled. The user must obtain a compatible model file and import it locally. Model license and use terms remain the user's responsibility.

## Compatibility boundary
Phase 11 is stacked on the accepted Phase 10 branch. It adds a separate story-AI activity and engine, a focused JVM test file, one LiteRT-LM dependency, one non-exported manifest activity and one additive entry point from MainActivity. The Phase 10 library is consumed through its public APIs. No Phase 2–10 rendering, motion, audio, project, checkpoint or library implementation file is edited or deleted.

## Verification boundary
CI is limited to Android lint and JVM unit tests. It does not establish that a model loads on the user's phone, benchmark generation speed, or prove physical-device memory suitability. No APK, emulator, instrumentation or device test is performed before the user explicitly approves the final offline device-test stage.
