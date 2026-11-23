package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Single entry point for scene rendering.
 *
 * A valid user-selected image always remains the source for its shot.
 * Missing images use the procedural engine; failed depth analysis keeps the original
 * image as a still, and an actual image-rendering failure stops export instead of substituting.
 */
object WolfSceneRenderPipeline {
    enum class Backend { PROCEDURAL, UPLOADED_IMAGE }

    class PreparedShot internal constructor(
        val backend: Backend,
        internal var image: WolfUploadedImageEngine.PreparedImage?,
        internal var proceduralPlan: WolfProceduralSceneProductionEngine.ScenePlan?,
        private val caption: String,
        private val sceneIndex: Int
    ) {
        internal fun requireProceduralPlan(): WolfProceduralSceneProductionEngine.ScenePlan {
            return proceduralPlan ?: WolfProceduralSceneProductionEngine.plan(caption, sceneIndex)
                .also { proceduralPlan = it }
        }

        fun recycle() {
            image?.recycle()
            image = null
        }
    }

    internal fun shouldKeepUploadedImageSource(sourceAvailable: Boolean): Boolean =
        sourceAvailable

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
     * Exercises the real backend-dispatch state machine independently of Android Canvas.
     * The production render entry point supplies the actual image/procedural drawing calls.
     */
    internal fun renderPreparedShot(
        preparedShot: PreparedShot,
        renderProcedural: () -> Unit,
        renderUploadedImage: () -> WolfUploadedImageEngine.Result,
        onFallback: (String) -> Unit = {}
    ): Backend {
        if (preparedShot.backend == Backend.PROCEDURAL) {
            renderProcedural()
            return Backend.PROCEDURAL
        }

        val result = try {
            renderUploadedImage()
        } catch (failure: OutOfMemoryError) {
            reportFallback(onFallback, "ImageRenderOutOfMemory")
            WolfUploadedImageEngine.Result.SAFE_FRAME
        } catch (failure: Exception) {
            reportFallback(onFallback, failure.javaClass.simpleName)
            WolfUploadedImageEngine.Result.SAFE_FRAME
        }

        if (result == WolfUploadedImageEngine.Result.SAFE_FRAME) {
            reportFallback(onFallback, "ImageRenderSafeFrame")
            throw IllegalStateException(
                "تعذر رسم صورة المستخدم المحددة؛ أوقفنا التصدير بدل استبدالها بمشهد إجرائي"
            )
        }

        return Backend.UPLOADED_IMAGE
    }

    /**
     * Prepares one shot without eagerly planning procedural artwork for a valid image.
     * A null image selects the procedural route. A decoded image with failed analysis
     * remains selected and is rendered as a still without camera motion or parallax.
     */
    fun prepare(
        caption: String,
        sceneIndex: Int,
        source: Bitmap?,
        onFallback: (String) -> Unit = {}
    ): PreparedShot {
        require(caption.isNotBlank()) { "نص المشهد لا يمكن أن يكون فارغاً" }
        require(sceneIndex >= 0) { "رقم المشهد غير صالح" }

        if (source == null) {
            reportFallback(onFallback, "NoUploadedImage")
            return proceduralShot(caption, sceneIndex)
        }
        if (source.isRecycled || source.width <= 0 || source.height <= 0) {
            reportFallback(onFallback, "InvalidUserSelectedImage")
            throw IllegalStateException(
                "الصورة التي اختارها المستخدم غير صالحة؛ أوقفنا اللقطة بدل استبدالها بمشهد آخر"
            )
        }

        val prepared = try {
            WolfUploadedImageEngine.prepare(source)
        } catch (failure: OutOfMemoryError) {
            reportFallback(onFallback, "OutOfMemoryError")
            WolfUploadedImageEngine.PreparedImage(source, null, 0f, "OutOfMemoryError")
        } catch (failure: Exception) {
            reportFallback(onFallback, failure.javaClass.simpleName)
            WolfUploadedImageEngine.PreparedImage(source, null, 0f, failure.javaClass.simpleName)
        }

        // Failed or heuristic-only segmentation is not permission to replace a user image.
        // The image renderer keeps the original source as a still until semantic layers pass.
        val useImage = shouldKeepUploadedImageSource(sourceAvailable = true)
        if (!useImage) {
            prepared.recycle()
            reportFallback(onFallback, "UploadedImageUnavailable")
            throw IllegalStateException("تعذر الاحتفاظ بصورة المستخدم المحددة في مسار الإنتاج")
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
     * Renders through the selected backend. A real failure to render a selected image
     * aborts export rather than silently substituting a generated scene.
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
        return renderPreparedShot(
            preparedShot = preparedShot,
            renderProcedural = {
                renderProcedural(canvas, width, height, shotIndex, progress, preparedShot)
            },
            renderUploadedImage = {
                val image = preparedShot.image
                if (image == null) {
                    reportFallback(onFallback, "PreparedImageUnavailable")
                    WolfUploadedImageEngine.Result.SAFE_FRAME
                } else {
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
                }
            },
            onFallback = onFallback
        )
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
