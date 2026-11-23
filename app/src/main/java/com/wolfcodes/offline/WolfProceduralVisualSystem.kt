package com.wolfcodes.offline

import android.graphics.Canvas

/**
 * Legacy entry point retained for project compatibility.
 * It delegates to the atmospheric-only fallback and environmental effects;
 * it never fabricates a human, ghost, building, or prop silhouette.
 */
class WolfProceduralVisualSystem {
    @Suppress("UNUSED_PARAMETER")
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        text: String,
        scene: Int,
        progress: Float,
        time: Float
    ) {
        if (width <= 0 || height <= 0) return
        WolfSceneArtEngine.draw(canvas, width, height, text, scene, progress)
        WolfProceduralMotionEngine.renderDepthAware(
            canvas = canvas,
            width = width,
            height = height,
            caption = text,
            sceneIndex = scene,
            shotIndex = 0,
            progress = progress
        )
    }
}
