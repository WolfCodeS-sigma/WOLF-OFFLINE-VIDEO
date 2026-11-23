# WOLF IMAGE MOTION — Unified failure policy

## Accepted production rules

### No image was selected
Use the environment-only procedural renderer. It may add atmospheric backgrounds, haze, particles, branches, rain, environmental pulses, and restrained signal distortion. It must not invent a person, ghost, creature, face, door, building, furniture, or prop from primitive geometry.

### A user image was selected
Keep that image as the source for the shot. If semantic segmentation is unavailable or fails validation, render the original aspect-matched image as a still. Do not apply camera zoom, pan, parallax, or procedural subject overlays.

### The selected image cannot be decoded or drawn
Stop export with a clear error. Do not replace the image with a different procedural scene. This protects the user's chosen content from silent substitution.

## Semantic depth gate

The current segmenter is a bounded border/color heuristic, not a semantic model. Confidence or a separated foreground mask alone is insufficient. `semanticValidationPassed` remains false until an actual semantic validator is integrated and tested; `safeForParallax` requires both separation and semantic validation.

## Recovery and continuity

- The no-image procedural route may continue with available environmental effects when a subject cannot be generated; it must not claim the subject was rendered.
- A selected image that is preserved as a still remains selected for every frame of its shot.
- A real selected-image render failure is an export failure, not an image-to-procedural fallback.
- Canvas save/restore and bitmap lifecycle protections remain mandatory.

## Regression expectations

- Heuristic color segmentation cannot enable depth-aware motion.
- An image with no semantic depth layers remains an original still.
- A failed selected-image render never invokes the procedural renderer.
- An absent user image selects the environment-only procedural backend.
