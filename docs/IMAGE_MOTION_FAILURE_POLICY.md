# WOLF IMAGE MOTION — Failure-Tolerant Production Policy

## Accepted production rule

A failure to isolate one image element must never freeze the horror scene or abort the film export.

The renderer follows a graceful-degradation rule:

1. Attempt to isolate image elements into independently composited depth layers.
2. If segmentation succeeds with usable layers, composite those layers with their own depth-aware motion.
3. If segmentation reports that the result is not sufficiently separated, render the untouched original image instead of using empty or misleading masks.
4. If segmentation throws an exception, keep the original decoded bitmap alive, record a local diagnostic warning, and render that original image.
5. In either fallback case, continue the rest of the scene pipeline: procedural environmental motion, atmospheric effects, typography cues, watermark when enabled, and all remaining shots/scenes.
6. A segmentation-only failure must not be escalated into a film-export failure. Genuine independent failures such as codec, storage, or resource-safety failures retain their existing error handling.

## Horror-direction principle

The scene must remain active even when a particular subject cannot be isolated. Horror motion can be carried by any available element: a moving chair, opening door, shifting curtain, swaying tree, moving shadow, flickering light, drifting fog, rain, particles, or camera/depth movement.

Do not claim that a failed-to-isolate ghost or object has been independently animated. Preserve it in the original image and continue animating the elements and effects that are actually available. The intended visual result is a living horror scene, not a frozen frame and not a falsely reported object animation.

## Current implementation boundary

The current offline segmenter estimates up to three depth planes and can separate disconnected foreground components heuristically. This policy makes its failure non-fatal; it does not claim that every object is already detected and independently rigged. More granular object recognition, per-object masks, and independent motion remain a separate implementation task and must be completed and verified before claiming full object-level animation.

## Regression expectations

- A thrown segmentation exception returns a fallback signal rather than propagating into film export.
- The caller keeps the original bitmap available for rendering.
- A non-separated result uses the original image.
- Procedural motion and scene rendering continue after either segmentation fallback.
- Successful segmentation continues to use the separated depth layers.
