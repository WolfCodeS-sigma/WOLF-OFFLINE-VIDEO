package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Single entry point for scene rendering.
 *
 * Uploaded images are selected only when their analysis produced usable depth layers.
 * Missing, invalid, or unanalyzable images are routed to the established procedural engine.
 * Runtime image-render failures are also retried through the procedural renderer.
 */
object WolfSceneRenderPipeline {
    enum class Backend { PROCEDURAL, UPLOADED_IMAGE }

    class PreparedShot internal constructor(
        var backend: Backend,
        internal var image: WolfUploadedImageEngine.PreparedImage?,
        internal var proceduralPlan: WolfProceduralSceneProductionEngine.ScenePlan?,
        private val caption: String,
        private val sceneIndex: Int
    ) {
        internal fun requireProceduralPlan(): WolfProceduralSceneProductionEngine.ScenePlan {
            return proceduralPlan ?: WolfProceduralSceneProductionEngine.plan(caption, sceneIndex)
                .also { proceduralPlan = it }
        }

        internal fun switchToProcedural() {
            image?.recycle()
            image = null
            backend = Backend.PROCEDURAL
            requireProceduralPlan()
        }

        fun recycle() {
            image?.recycle()
            image = null
        }
    }

    internal fun shouldUseUploadedImageRoute(
        sourceAvailable: Boolean,
        hasUsableDepthLayers: Boolean,
        analysisFailed: Boolean
    ): Boolean = sourceAvailable && hasUsableDepthLayers && !analysisFailed

    /**
     * Fallback reporting is best-effort. A diagnostics callback must never interrupt
     * the production route it is reporting, including the no-image recovery path.
     */
    private fun reportFallback(onFallback: (String) -> Unit, reason: String) {
        try {
            onFallback(reason)
        } catch (_: OutOfMemoryError) {
            // Rendering recovery takes priority over optional diagnostics.
        } catch (_: Exception) {
            // Rendering recovery takes priority over optional diagnostics.
        }
    }

    /**
     * Prepares one shot without eagerly planning procedural artwork for a valid image.
     * A null image or an unsuccessful analysis selects the procedural route immediately.
     */
    fun prepare(
        caption: String,
        sceneIndex: Int,
        source: Bitmap?,
        onFallback: (String) -> Unit = {}
    ): PreparedShot {
        require(caption.isNotBlank()) { "نص المشهد لا يمكن أن يكون فارغاً" }
        require(sceneIndex >= 0) { "رقم المشهد غير صالح" }

        if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
            reportFallback(onFallback, if (source == null) "NoUploadedImage" else "InvalidUploadedImage")
            return proceduralShot(caption, sceneIndex)
        }

        val prepared = try {
            WolfUploadedImageEngine.prepare(source)
        } catch (failure: OutOfMemoryError) {
            reportFallback(onFallback, "OutOfMemoryError")
            null
        } catch (failure: Exception) {
            reportFallback(onFallback, failure.javaClass.simpleName)
            null
        }

        val useImage = prepared != null && shouldUseUploadedImageRoute(
            sourceAvailable = true,
            hasUsableDepthLayers = prepared.layers != null,
            analysisFailed = prepared.warning != null
        )
        if (!useImage) {
            prepared?.recycle()
            reportFallback(onFallback, prepared?.warning ?: "NoUsableDepthLayers")
            return proceduralShot(caption, sceneIndex)
        }

        return PreparedShot(
            backend = Backend.UPLOADED_IMAGE,
            image = prepared,
            proceduralPlan = null,
            caption = caption,
            sceneIndex = sceneIndex
        )
    }

    /**
     * Renders through the selected backend. A failed uploaded-image frame is retried
     * with the procedural scene plan before control returns to the video encoder.
     */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        shotIndex: Int,
        progress: Float,
        caption: String,
        sceneIndex: Int,
        preparedShot: PreparedShot,
        onFallback: (String) -> Unit = {}
    ): Backend {
        if (preparedShot.backend == Backend.PROCEDURAL) {
            WolfProceduralSceneProductionEngine.render(
                canvas = canvas,
                width = width,
                height = height,
                plan = preparedShot.requireProceduralPlan(),
                shotIndex = shotIndex,
                progress = progress
            )
            return Backend.PROCEDURAL
        }

        val image = preparedShot.image
        if (image == null) {
            reportFallback(onFallback, "PreparedImageUnavailable")
            preparedShot.switchToProcedural()
            renderProcedural(canvas, width, height, shotIndex, progress, preparedShot)
            return Backend.PROCEDURAL
        }

        val result = try {
            WolfUploadedImageEngine.render(
                canvas = canvas,
                width = width,
                height = height,
                sceneIndex = sceneIndex,
                shotIndex = shotIndex,
                progress = progress,
                caption = caption,
                prepared = image
            )
        } catch (failure: OutOfMemoryError) {
            reportFallback(onFallback, "ImageRenderOutOfMemory")
            WolfUploadedImageEngine.Result.SAFE_FRAME
        } catch (failure: Exception) {
            reportFallback(onFallback, failure.javaClass.simpleName)
            WolfUploadedImageEngine.Result.SAFE_FRAME
        }

        if (result == WolfUploadedImageEngine.Result.SAFE_FRAME) {
            reportFallback(onFallback, "ImageRenderSafeFrame")
            preparedShot.switchToProcedural()
            renderProcedural(canvas, width, height, shotIndex, progress, preparedShot)
            return Backend.PROCEDURAL
        }

        return Backend.UPLOADED_IMAGE
    }

    private fun renderProcedural(
        canvas: Canvas,
        width: Int,
        height: Int,
        shotIndex: Int,
        progress: Float,
        preparedShot: PreparedShot
    ) {
        WolfProceduralSceneProductionEngine.render(
            canvas = canvas,
            width = width,
            height = height,
            plan = preparedShot.requireProceduralPlan(),
            shotIndex = shotIndex,
            progress = progress
        )
    }

    private fun proceduralShot(caption: String, sceneIndex: Int): PreparedShot {
        return PreparedShot(
            backend = Backend.PROCEDURAL,
            image = null,
            proceduralPlan = WolfProceduralSceneProductionEngine.plan(caption, sceneIndex),
            caption = caption,
            sceneIndex = sceneIndex
        )
    }
}
