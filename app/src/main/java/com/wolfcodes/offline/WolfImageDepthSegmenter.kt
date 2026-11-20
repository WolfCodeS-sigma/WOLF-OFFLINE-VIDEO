package com.wolfcodes.offline

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic, fully offline image cutout estimation.
 *
 * This is a border-background segmentation heuristic, not semantic AI.
 * It separates a foreground silhouette from a border-connected background
 * and assigns disconnected foreground components to independent depth planes.
 */
object WolfImageDepthSegmenter {

    /**
     * Runs image segmentation without allowing a segmentation-only failure to abort film rendering.
     * A null result tells the caller to keep and render the untouched source image while
     * the rest of the scene's procedural animation continues.
     */
    internal fun <T> attemptSegmentation(
        operation: () -> T,
        onFailure: (Exception) -> Unit = {}
    ): T? {
        return try {
            operation()
        } catch (failure: Exception) {
            runCatching { onFailure(failure) }
            null
        }
    }

    private const val MAX_ANALYSIS_DIMENSION = 960
    private const val MIN_COMPONENT_PIXELS = 64

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

    fun segment(source: Bitmap): Layers {
        require(!source.isRecycled && source.width > 0 && source.height > 0) {
            "الصورة غير صالحة لفصل طبقات العمق"
        }

        val scale = min(
            1f,
            MAX_ANALYSIS_DIMENSION.toFloat() / max(source.width, source.height).toFloat()
        )
        val width = max(1, (source.width * scale).toInt())
        val height = max(1, (source.height * scale).toInt())
        val working = if (width == source.width && height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }

        val allocatedLayers = mutableListOf<Bitmap>()
        try {
            val pixels = IntArray(width * height)
            working.getPixels(pixels, 0, width, 0, 0, width, height)
            val masks = analyze(width, height, pixels)
            val backgroundPixels = buildCleanPlate(width, height, pixels, masks.foregroundMask)
            val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also(allocatedLayers::add)
            val midground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also(allocatedLayers::add)
            val foreground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also(allocatedLayers::add)
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

            background.setPixels(backgroundPixels, 0, width, 0, 0, width, height)
            midground.setPixels(midPixels, 0, width, 0, 0, width, height)
            foreground.setPixels(frontPixels, 0, width, 0, 0, width, height)

            if (working !== source && !working.isRecycled) working.recycle()
            return Layers(
                background = background,
                midground = midground,
                foreground = foreground,
                sourceScale = width.toFloat() / source.width.toFloat(),
                confidence = masks.confidence,
                separated = masks.separated
            )
        } catch (error: Exception) {
            allocatedLayers.forEach { layer ->
                if (!layer.isRecycled) runCatching { layer.recycle() }
            }
            if (working !== source && !working.isRecycled) working.recycle()
            throw error
        }
    }

    internal fun analyze(width: Int, height: Int, pixels: IntArray): MaskResult {
        require(width > 2 && height > 2 && pixels.size == width * height)
        val border = ArrayList<Int>((width + height) * 2)
        for (x in 0 until width) {
            border.add(pixels[x])
            border.add(pixels[(height - 1) * width + x])
        }
        for (y in 1 until height - 1) {
            border.add(pixels[y * width])
            border.add(pixels[y * width + width - 1])
        }

        val backgroundColor = medianColor(border)
        val borderDistances = IntArray(border.size)
        for (i in border.indices) borderDistances[i] = colorDistance(border[i], backgroundColor)
        borderDistances.sort()
        val noise = borderDistances[borderDistances.size / 2]
        val threshold = (28 + noise * 2).coerceIn(26, 76)

        val candidate = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            candidate[i] = colorDistance(pixels[i], backgroundColor) > threshold
        }

        val connectedBackground = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0

        fun addBackground(index: Int) {
            if (!candidate[index] && !connectedBackground[index]) {
                connectedBackground[index] = true
                queue[tail++] = index
            }
        }

        for (x in 0 until width) {
            addBackground(x)
            addBackground((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            addBackground(y * width)
            addBackground(y * width + width - 1)
        }

        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            if (x > 0) addBackground(index - 1)
            if (x + 1 < width) addBackground(index + 1)
            if (index >= width) addBackground(index - width)
            if (index + width < pixels.size) addBackground(index + width)
        }

        val foregroundMask = BooleanArray(pixels.size) { !connectedBackground[it] }
        val foregroundCount = foregroundMask.count { it }
        val foregroundRatio = foregroundCount.toFloat() / pixels.size.toFloat()
        val validRatio = foregroundRatio in 0.012f..0.82f
        if (!validRatio) {
            return MaskResult(
                foregroundMask = BooleanArray(pixels.size),
                midgroundMask = BooleanArray(pixels.size),
                foregroundPlaneMask = BooleanArray(pixels.size),
                confidence = 0f,
                separated = false
            )
        }

        val visited = BooleanArray(pixels.size)
        val midMask = BooleanArray(pixels.size)
        val frontMask = BooleanArray(pixels.size)
        var largestComponent = 0
        var largestPixels = IntArray(0)
        val componentQueue = IntArray(pixels.size)
        val minimumComponent = max(MIN_COMPONENT_PIXELS, (foregroundCount * 0.0015f).toInt())

        for (start in pixels.indices) {
            if (!foregroundMask[start] || visited[start]) continue
            var componentHead = 0
            var componentTail = 0
            componentQueue[componentTail++] = start
            visited[start] = true
            while (componentHead < componentTail) {
                val index = componentQueue[componentHead++]
                val x = index % width
                if (x > 0) enqueueForeground(index - 1, foregroundMask, visited, componentQueue, componentTail).also { componentTail = it }
                if (x + 1 < width) enqueueForeground(index + 1, foregroundMask, visited, componentQueue, componentTail).also { componentTail = it }
                if (index >= width) enqueueForeground(index - width, foregroundMask, visited, componentQueue, componentTail).also { componentTail = it }
                if (index + width < pixels.size) enqueueForeground(index + width, foregroundMask, visited, componentQueue, componentTail).also { componentTail = it }
            }

            if (componentTail > largestComponent) {
                largestComponent = componentTail
                largestPixels = componentQueue.copyOf(componentTail)
            }
            if (componentTail >= minimumComponent) {
                val target = if (componentTail == largestComponent) midMask else frontMask
                for (i in 0 until componentTail) target[componentQueue[i]] = true
            } else {
                for (i in 0 until componentTail) midMask[componentQueue[i]] = true
            }
        }

        if (largestPixels.isNotEmpty()) {
            for (index in largestPixels) {
                midMask[index] = true
                frontMask[index] = false
            }
        }

        val totalAssigned = midMask.count { it } + frontMask.count { it }
        val borderConsistency = (1f - noise / 100f).coerceIn(0f, 1f)
        val confidence = (
            min(1f, foregroundRatio / 0.12f) *
                min(1f, (1f - foregroundRatio) / 0.18f) *
                borderConsistency
            ).coerceIn(0f, 1f)
        return MaskResult(
            foregroundMask = foregroundMask,
            midgroundMask = midMask,
            foregroundPlaneMask = frontMask,
            confidence = if (totalAssigned == 0) 0f else confidence,
            separated = totalAssigned > 0 && confidence >= 0.30f
        )
    }

    private fun enqueueForeground(
        index: Int,
        foreground: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (foreground[index] && !visited[index]) {
            visited[index] = true
            queue[tail] = index
            return tail + 1
        }
        return tail
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
        red.sort()
        green.sort()
        blue.sort()
        val middle = colors.size / 2
        return (0xFF shl 24) or (red[middle] shl 16) or (green[middle] shl 8) or blue[middle]
    }

    private fun colorDistance(first: Int, second: Int): Int {
        val red = abs(((first ushr 16) and 0xFF) - ((second ushr 16) and 0xFF))
        val green = abs(((first ushr 8) and 0xFF) - ((second ushr 8) and 0xFF))
        val blue = abs((first and 0xFF) - (second and 0xFF))
        return (red * 3 + green * 4 + blue * 2) / 3
    }

    private fun buildCleanPlate(
        width: Int,
        height: Int,
        source: IntArray,
        foreground: BooleanArray
    ): IntArray {
        val nearest = IntArray(source.size)
        val queue = IntArray(source.size)
        var head = 0
        var tail = 0
        for (index in source.indices) {
            if (!foreground[index]) {
                nearest[index] = index
                queue[tail++] = index
            } else {
                nearest[index] = -1
            }
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            if (x > 0) propagate(index - 1, index, nearest, queue, tail).also { tail = it }
            if (x + 1 < width) propagate(index + 1, index, nearest, queue, tail).also { tail = it }
            if (index >= width) propagate(index - width, index, nearest, queue, tail).also { tail = it }
            if (index + width < source.size) propagate(index + width, index, nearest, queue, tail).also { tail = it }
        }
        return IntArray(source.size) { index ->
            val sourceIndex = nearest[index].takeIf { it >= 0 } ?: index
            source[sourceIndex] or (0xFF shl 24)
        }
    }

    private fun propagate(
        target: Int,
        from: Int,
        nearest: IntArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (nearest[target] == -1) {
            nearest[target] = nearest[from]
            queue[tail] = target
            return tail + 1
        }
        return tail
    }

    private fun edgeAlpha(index: Int, width: Int, height: Int, mask: BooleanArray): Int {
        val x = index % width
        val boundary = (x > 0 && !mask[index - 1]) ||
            (x + 1 < width && !mask[index + 1]) ||
            (index >= width && !mask[index - width]) ||
            (index + width < width * height && !mask[index + width])
        return if (boundary) 188 else 255
    }
}
