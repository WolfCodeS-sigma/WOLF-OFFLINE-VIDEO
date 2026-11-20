package com.wolfcodes.offline

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic, fully offline foreground/depth estimation.
 *
 * This is a lightweight border-background heuristic, not semantic AI. It uses
 * robust border statistics, 8-connected flood fill and component filtering.
 * Ambiguous images deliberately return an unusable result so the Phase 6
 * router can keep the source image or select the procedural renderer.
 */
object WolfImageDepthSegmenter {

    internal fun <T> attemptSegmentation(
        operation: () -> T,
        onFailure: (Exception) -> Unit = {}
    ): T? = try {
        operation()
    } catch (failure: Exception) {
        runCatching { onFailure(failure) }
        null
    }

    // Bound both dimensions and total working pixels to avoid large temporary
    // masks/queues on memory-constrained Android devices.
    private const val MAX_ANALYSIS_DIMENSION = 768
    private const val MAX_ANALYSIS_PIXELS = 262_144
    private const val MIN_COMPONENT_PIXELS = 48
    private const val MIN_FOREGROUND_RATIO = 0.008f
    private const val MAX_FOREGROUND_RATIO = 0.86f

    data class Layers(
        val background: Bitmap,
        val midground: Bitmap,
        val foreground: Bitmap,
        val sourceScale: Float,
        val confidence: Float,
        val separated: Boolean
    ) {
        fun recycle() {
            if (!background.isRecycled) background.recycle()
            if (!midground.isRecycled) midground.recycle()
            if (!foreground.isRecycled) foreground.recycle()
        }
    }

    internal data class MaskResult(
        val foregroundMask: BooleanArray,
        val midgroundMask: BooleanArray,
        val foregroundPlaneMask: BooleanArray,
        val confidence: Float,
        val separated: Boolean
    )

    private data class BackgroundModel(val color: Int, val threshold: Int, val consistency: Float)

    fun segment(source: Bitmap): Layers {
        require(!source.isRecycled && source.width > 2 && source.height > 2) {
            "الصورة غير صالحة لفصل طبقات العمق"
        }

        val dimensionScale = min(1f, MAX_ANALYSIS_DIMENSION.toFloat() /
            max(source.width, source.height).toFloat())
        val pixelScale = sqrt(MAX_ANALYSIS_PIXELS.toFloat() /
            (source.width.toFloat() * source.height.toFloat())).coerceAtMost(1f)
        val scale = min(dimensionScale, pixelScale)
        val width = max(3, (source.width * scale).toInt())
        val height = max(3, (source.height * scale).toInt())
        val working = if (width == source.width && height == source.height) source
            else Bitmap.createScaledBitmap(source, width, height, true)

        val allocated = mutableListOf<Bitmap>()
        try {
            val pixels = IntArray(width * height)
            working.getPixels(pixels, 0, width, 0, 0, width, height)
            val masks = analyze(width, height, pixels)
            val cleanPlate = buildCleanPlate(width, height, pixels, masks.foregroundMask)
            val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { allocated.add(it) }
            val midground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { allocated.add(it) }
            val foreground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { allocated.add(it) }
            val midPixels = IntArray(pixels.size)
            val frontPixels = IntArray(pixels.size)

            for (index in pixels.indices) {
                if (masks.midgroundMask[index]) {
                    val alpha = edgeAlpha(index, width, height, masks.midgroundMask)
                    midPixels[index] = (pixels[index] and 0x00FFFFFF) or (alpha shl 24)
                }
                if (masks.foregroundPlaneMask[index]) {
                    val alpha = edgeAlpha(index, width, height, masks.foregroundPlaneMask)
                    frontPixels[index] = (pixels[index] and 0x00FFFFFF) or (alpha shl 24)
                }
            }
            background.setPixels(cleanPlate, 0, width, 0, 0, width, height)
            midground.setPixels(midPixels, 0, width, 0, 0, width, height)
            foreground.setPixels(frontPixels, 0, width, 0, 0, width, height)

            return Layers(
                background, midground, foreground,
                width.toFloat() / source.width.toFloat(),
                masks.confidence, masks.separated
            )
        } catch (error: Throwable) {
            allocated.forEach { bitmap -> if (!bitmap.isRecycled) runCatching { bitmap.recycle() } }
            throw error
        } finally {
            if (working !== source && !working.isRecycled) working.recycle()
        }
    }

    internal fun analyze(width: Int, height: Int, pixels: IntArray): MaskResult {
        require(width > 2 && height > 2 && pixels.size == width * height)
        val model = estimateBorderBackground(width, height, pixels)
        val candidate = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            val alpha = pixels[i] ushr 24
            candidate[i] = alpha > 20 && colorDistance(pixels[i], model.color) > model.threshold
        }

        // Border-connected background is flooded with 8-connectivity. Diagonal
        // gaps therefore do not create false foreground islands.
        val connectedBackground = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0
        fun seed(index: Int) {
            if (!candidate[index] && !connectedBackground[index]) {
                connectedBackground[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until width) {
            seed(x)
            seed((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            seed(y * width)
            seed(y * width + width - 1)
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                if (!candidate[next] && !connectedBackground[next]) {
                    connectedBackground[next] = true
                    queue[tail++] = next
                }
            }
        }

        val foregroundMask = BooleanArray(pixels.size) {
            !connectedBackground[it] && (pixels[it] ushr 24) > 20
        }
        val foregroundCount = foregroundMask.count { it }
        val ratio = foregroundCount.toFloat() / pixels.size.toFloat()
        if (ratio !in MIN_FOREGROUND_RATIO..MAX_FOREGROUND_RATIO) {
            return emptyResult(pixels.size)
        }

        val visited = BooleanArray(pixels.size)
        val midMask = BooleanArray(pixels.size)
        val frontMask = BooleanArray(pixels.size)
        val componentQueue = IntArray(pixels.size)
        var largestSize = 0
        var largestPixels = IntArray(0)
        val minimumComponent = max(MIN_COMPONENT_PIXELS, (foregroundCount * 0.001f).toInt())

        for (start in pixels.indices) {
            if (!foregroundMask[start] || visited[start]) continue
            var componentHead = 0
            var componentTail = 0
            componentQueue[componentTail++] = start
            visited[start] = true
            while (componentHead < componentTail) {
                val index = componentQueue[componentHead++]
                val x = index % width
                val y = index / width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (foregroundMask[next] && !visited[next]) {
                        visited[next] = true
                        componentQueue[componentTail++] = next
                    }
                }
            }
            if (componentTail > largestSize) {
                largestSize = componentTail
                largestPixels = componentQueue.copyOf(componentTail)
            }
            // Ignore sub-threshold components instead of promoting isolated noise
            // into the primary subject plane.
            if (componentTail >= minimumComponent) {
                for (i in 0 until componentTail) frontMask[componentQueue[i]] = true
            }
        }

        // Without one coherent component, the apparent foreground is only noise.
        if (largestSize < minimumComponent) return emptyResult(pixels.size)

        // The largest coherent subject is the primary midground plane.
        for (index in largestPixels) {
            midMask[index] = true
            frontMask[index] = false
        }

        val assigned = midMask.any { it } || frontMask.any { it }
        val coverageScore = min(1f, ratio / 0.08f) *
            min(1f, (1f - ratio) / 0.12f)
        val confidence = (coverageScore * model.consistency).coerceIn(0f, 1f)
        return MaskResult(
            foregroundMask, midMask, frontMask,
            if (assigned) confidence else 0f,
            assigned && confidence >= 0.12f
        )
    }

    private fun emptyResult(size: Int) = MaskResult(
        BooleanArray(size), BooleanArray(size), BooleanArray(size), 0f, false
    )

    private fun estimateBorderBackground(width: Int, height: Int, pixels: IntArray): BackgroundModel {
        // Sample a bounded number of border pixels to keep analysis cost predictable.
        val samples = ArrayList<Int>(min(4096, (width + height) * 2))
        val stride = max(1, (width + height) * 2 / 4096)
        var ordinal = 0
        fun add(index: Int) {
            if (ordinal++ % stride == 0 && (pixels[index] ushr 24) > 20) samples.add(pixels[index])
        }
        for (x in 0 until width) {
            add(x)
            add((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            add(y * width)
            add(y * width + width - 1)
        }
        if (samples.size < 8) {
            val fallback = pixels.firstOrNull { (it ushr 24) > 20 } ?: 0xFF000000.toInt()
            return BackgroundModel(fallback, 30, 0.2f)
        }
        val background = medianColor(samples)
        val distances = IntArray(samples.size) { colorDistance(samples[it], background) }
        distances.sort()
        val p75 = distances[(distances.size * 3 / 4).coerceIn(0, distances.lastIndex)]
        val p90 = distances[(distances.size * 9 / 10).coerceIn(0, distances.lastIndex)]
        val threshold = (24 + p75 + p90 / 3).coerceIn(24, 92)
        val consistency = (1f - (p75 / 150f)).coerceIn(0.18f, 1f)
        return BackgroundModel(background, threshold, consistency)
    }

    private fun medianColor(colors: List<Int>): Int {
        val red = IntArray(colors.size)
        val green = IntArray(colors.size)
        val blue = IntArray(colors.size)
        for (i in colors.indices) {
            red[i] = (colors[i] ushr 16) and 0xFF
            green[i] = (colors[i] ushr 8) and 0xFF
            blue[i] = colors[i] and 0xFF
        }
        red.sort(); green.sort(); blue.sort()
        val middle = colors.size / 2
        return (0xFF shl 24) or (red[middle] shl 16) or
            (green[middle] shl 8) or blue[middle]
    }

    private fun colorDistance(first: Int, second: Int): Int {
        val red = abs(((first ushr 16) and 0xFF) - ((second ushr 16) and 0xFF))
        val green = abs(((first ushr 8) and 0xFF) - ((second ushr 8) and 0xFF))
        val blue = abs((first and 0xFF) - (second and 0xFF))
        // Weighted RGB distance is cheap and deterministic; alpha is handled separately.
        return (red * 3 + green * 4 + blue * 2) / 3
    }

    private fun buildCleanPlate(
        width: Int, height: Int, source: IntArray, foreground: BooleanArray
    ): IntArray {
        val nearest = IntArray(source.size) { -1 }
        val queue = IntArray(source.size)
        var head = 0
        var tail = 0
        for (index in source.indices) {
            // Transparent holes are not valid clean-plate seeds; propagate the
            // nearest visible background color through them instead.
            if (!foreground[index] && (source[index] ushr 24) > 20) {
                nearest[index] = index
                queue[tail++] = index
            }
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                if (nearest[next] == -1) {
                    nearest[next] = nearest[index]
                    queue[tail++] = next
                }
            }
        }
        return IntArray(source.size) { index ->
            val from = nearest[index].takeIf { it >= 0 } ?: index
            source[from] or (0xFF shl 24)
        }
    }

    private fun edgeAlpha(index: Int, width: Int, height: Int, mask: BooleanArray): Int {
        val x = index % width
        val y = index / width
        var boundary = false
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height && !mask[ny * width + nx]) {
                boundary = true
            }
        }
        return if (boundary) 192 else 255
    }
}
