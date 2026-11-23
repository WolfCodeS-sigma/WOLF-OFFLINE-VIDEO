# Phase 9 — Historical cinematic subject-motion prototype

> **Status in the unified core:** archived; the old character/prop overlay engine is not part of the production render path.

The former Phase 9 proposal described figure/ghost rigs, swinging arms, door hinges, curtains, branches, and moving shadows. The subject/prop overlay engine and its test were removed from the unified tree so that no generated geometric person or ghost can be painted over a selected user image.

## Current production motion

The active `WolfProceduralMotionEngine` is the Stage environment-only implementation. It adds atmospheric fog, particles, wind-driven branches, rain, environmental pulses, and restrained signal distortion to shots without user images. It does not synthesize people, ghosts, or creatures.

For user-selected images, the current segmentation implementation has no semantic validator. The selected image therefore remains a still without camera movement, parallax, or procedural overlays.

## Re-entry requirements

A future subject-motion engine must use approved assets or validated semantic masks, prove occlusion and image-boundary safety, remain deterministic across chapter recovery, and pass visual regression tests before being connected to production. The former prototype is not evidence that these requirements are met.
