package com.wolfcodes.offline

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Surface
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private object WolfFilamentRuntime {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        Utils.init()
        Gltfio.init()
        initialized = true
    }
}

private class WolfFilamentMuxerState {
    var videoTrack = -1
    var started = false
    var endOfStream = false
}

class HorrorVideoExporter(private val context: Context) {

    fun exportToFile(
        outputFile: File,
        durationSeconds: Int = 5,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        modelAssetPath: String = "wolf-filament-poc/industrial_corridor_test.glb",
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): File {
        require(durationSeconds in 1..30) { "مدة اختبار Filament يجب أن تكون من ثانية إلى 30 ثانية" }
        require(width in 320..1920 && width % 2 == 0) { "عرض الفيديو يجب أن يكون زوجيًا وبين 320 و1920" }
        require(height in 320..1080 && height % 2 == 0) { "ارتفاع الفيديو يجب أن يكون زوجيًا وبين 320 و1080" }
        require(fps in 12..60) { "معدل الإطارات يجب أن يكون بين 12 و60 إطارًا في الثانية" }
        require(outputFile.parentFile?.exists() == true || outputFile.parentFile?.mkdirs() == true) {
            "تعذر إنشاء مجلد ملف الفيديو المؤقت"
        }

        val rawFile = File(
            outputFile.parentFile,
            outputFile.nameWithoutExtension + ".filament-raw.mp4"
        )
        outputFile.delete()
        rawFile.delete()

        try {
            onProgress(1, "تهيئة Google Filament ومحرك الترميز")
            encodeFilamentFrames(
                rawFile = rawFile,
                durationSeconds = durationSeconds,
                width = width,
                height = height,
                fps = fps,
                modelAssetPath = modelAssetPath,
                onProgress = onProgress
            )
            onProgress(96, "ضبط توقيت إطارات MP4")
            normalizeVideoTimestamps(rawFile, outputFile, fps)

            val report = WolfFilmQualityInspector.inspect(
                context = context,
                mediaReference = outputFile.absolutePath,
                expectedDurationSeconds = durationSeconds,
                requireAudio = false
            )
            check(report.passed) { report.summary }
            val expectedFrames = durationSeconds.toLong() * fps.toLong()
            check(report.videoSamples >= expectedFrames - 2L) {
                "عدد إطارات Filament أقل من المطلوب: " + report.videoSamples +
                    " من " + expectedFrames
            }
            val actualDuration = report.durationSeconds
                ?: throw IllegalStateException("تعذر قراءة مدة فيديو Filament")
            check(actualDuration in (durationSeconds - 0.2)..(durationSeconds + 0.2)) {
                "مدة فيديو Filament الفعلية غير مطابقة: " + actualDuration + " ثانية"
            }

            onProgress(100, "اكتمل تصدير Filament وفحص MP4")
            return outputFile
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        } finally {
            rawFile.delete()
        }
    }

    fun publishToMediaStore(
        sourceFile: File,
        displayName: String = "WOLF_FILAMENT_POC_" + System.currentTimeMillis() + ".mp4"
    ): Uri {
        require(sourceFile.isFile && sourceFile.length() > 0L) {
            "ملف Filament غير موجود أو فارغ"
        }
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "حفظ اختبار Filament في المعرض يتطلب Android 10 أو أحدث"
        }

        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.endsWith(".mp4", ignoreCase = true)) it else "$it.mp4" }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/WOLF CODE S"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("تعذر إنشاء ملف Filament في معرض الفيديو")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("تعذر فتح ملف Filament للحفظ")
            val published = resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                },
                null,
                null
            )
            check(published == 1) { "تعذر إنهاء حفظ فيديو Filament في المعرض" }
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun encodeFilamentFrames(
        rawFile: File,
        durationSeconds: Int,
        width: Int,
        height: Int,
        fps: Int,
        modelAssetPath: String,
        onProgress: (Int, String) -> Unit
    ) {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var encoderStarted = false
        val muxerState = WolfFilamentMuxerState()
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()
            encoderStarted = true
            muxer = MediaMuxer(
                rawFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val surface = requireNotNull(inputSurface)
            val activeMuxer = requireNotNull(muxer)
            renderFilamentScene(
                surface = surface,
                durationSeconds = durationSeconds,
                width = width,
                height = height,
                fps = fps,
                modelAssetPath = modelAssetPath
            ) { frameIndex, frameCount ->
                drainEncoder(
                    encoder = encoder,
                    muxer = activeMuxer,
                    state = muxerState,
                    bufferInfo = bufferInfo,
                    waitForEndOfStream = false
                )
                if (frameIndex % fps == 0 || frameIndex == frameCount) {
                    val percent = (5 + frameIndex * 88 / frameCount).coerceIn(5, 94)
                    onProgress(
                        percent,
                        "رندر Filament: الإطار " + frameIndex + " من " + frameCount
                    )
                }
            }

            encoder.signalEndOfInputStream()
            drainEncoder(
                encoder = encoder,
                muxer = activeMuxer,
                state = muxerState,
                bufferInfo = bufferInfo,
                waitForEndOfStream = true
            )
            check(muxerState.endOfStream) { "لم يرسل مرمّز الفيديو علامة نهاية الإطارات" }
            check(muxerState.started && muxerState.videoTrack >= 0) {
                "لم يتم إنشاء مسار فيديو MP4 من Filament"
            }
            activeMuxer.stop()
            muxerState.started = false
        } finally {
            runCatching { inputSurface?.release() }
            if (encoderStarted) {
                runCatching { encoder.stop() }
            }
            runCatching { encoder.release() }
            if (muxerState.started) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun renderFilamentScene(
        surface: Surface,
        durationSeconds: Int,
        width: Int,
        height: Int,
        fps: Int,
        modelAssetPath: String,
        onFrame: (Int, Int) -> Unit
    ) {
        WolfFilamentRuntime.initialize()

        var engine: Engine? = null
        var renderer: Renderer? = null
        var scene: Scene? = null
        var view: View? = null
        var camera: Camera? = null
        var swapChain: SwapChain? = null
        var materialProvider: UbershaderProvider? = null
        var assetLoader: AssetLoader? = null
        var resourceLoader: ResourceLoader? = null
        var modelAsset: FilamentAsset? = null
        var cameraEntity = 0
        var lightEntity = 0

        try {
            val activeEngine = Engine.create()
            engine = activeEngine
            val activeRenderer = activeEngine.createRenderer()
            renderer = activeRenderer
            val activeScene = activeEngine.createScene()
            scene = activeScene
            val activeView = activeEngine.createView()
            view = activeView

            cameraEntity = EntityManager.get().create()
            val activeCamera = activeEngine.createCamera(cameraEntity)
            camera = activeCamera

            activeView.scene = activeScene
            activeView.camera = activeCamera
            activeView.viewport = Viewport(0, 0, width, height)
            activeView.setShadowingEnabled(true)
            activeView.fogOptions = View.FogOptions().apply {
                enabled = true
                distance = 2.2f
                maximumOpacity = 0.94f
                height = 0.4f
                heightFalloff = 0.18f
                color = floatArrayOf(0.035f, 0.041f, 0.052f)
                density = 0.085f
                inScatteringStart = 0.6f
                inScatteringSize = 5.0f
                fogColorFromIbl = false
            }

            activeRenderer.setDisplayInfo(
                Renderer.DisplayInfo().apply {
                    refreshRate = 0.0f
                }
            )
            activeRenderer.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                discard = true
                clearColor = doubleArrayOf(0.004, 0.005, 0.008, 1.0)
            }

            materialProvider = UbershaderProvider(activeEngine)
            assetLoader = AssetLoader(
                activeEngine,
                requireNotNull(materialProvider),
                EntityManager.get()
            )
            resourceLoader = ResourceLoader(activeEngine)

            val loadedAsset = requireNotNull(assetLoader).createAsset(
                readBundledAsset(modelAssetPath)
            ) ?: throw IllegalStateException("تعذر قراءة مجسم GLB المحلي: " + modelAssetPath)
            modelAsset = loadedAsset

            loadedAsset.resourceUris.forEach { resourceUri ->
                if (!resourceUri.startsWith("data:", ignoreCase = true)) {
                    val resourcePath = resolveResourcePath(modelAssetPath, resourceUri)
                    requireNotNull(resourceLoader).addResourceData(
                        resourceUri,
                        readBundledAsset(resourcePath)
                    )
                }
            }
            requireNotNull(resourceLoader).loadResources(loadedAsset)
            loadedAsset.releaseSourceData()
            activeScene.addEntities(loadedAsset.entities)

            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SPOT)
                .color(0.72f, 0.82f, 1.0f)
                .intensity(4_200.0f)
                .direction(0.0f, -0.16f, -1.0f)
                .position(0.0f, 2.85f, 4.2f)
                .spotLightCone(0.30f, 0.62f)
                .castShadows(true)
                .build(activeEngine, lightEntity)
            activeScene.addEntity(lightEntity)

            activeCamera.setProjection(
                52.0,
                width.toDouble() / height.toDouble(),
                0.1,
                100.0,
                Camera.Fov.VERTICAL
            )

            val frameCount = durationSeconds * fps
            val frameIntervalNanos = 1_000_000_000L / fps.toLong()
            val presentationOrigin = System.nanoTime()

            swapChain = activeEngine.createSwapChain(surface)
            val activeSwapChain = requireNotNull(swapChain)

            for (frameIndex in 0 until frameCount) {
                val progress = if (frameCount <= 1) {
                    1.0
                } else {
                    frameIndex.toDouble() / (frameCount - 1).toDouble()
                }
                val cameraZ = 5.25 - 0.85 * progress
                activeCamera.lookAt(
                    0.0,
                    1.62,
                    cameraZ,
                    0.0,
                    1.52,
                    -9.5,
                    0.0,
                    1.0,
                    0.0
                )

                val actualFrameStart = System.nanoTime()
                val presentationTime = presentationOrigin + frameIndex * frameIntervalNanos
                activeRenderer.beginFrame(activeSwapChain, actualFrameStart)
                activeRenderer.setPresentationTime(presentationTime)
                activeRenderer.setDesiredPresentationTime(presentationTime)
                activeRenderer.render(activeView)
                activeRenderer.endFrame()
                activeEngine.flushAndWait()
                onFrame(frameIndex + 1, frameCount)
            }
        } finally {
            val activeEngine = engine
            if (activeEngine != null) {
                runCatching { activeEngine.flushAndWait() }

                val loadedAsset = modelAsset
                if (loadedAsset != null) {
                    runCatching {
                        scene?.removeEntities(loadedAsset.entities)
                        assetLoader?.destroyAsset(loadedAsset)
                    }
                }

                runCatching { resourceLoader?.destroy() }
                runCatching { assetLoader?.destroy() }
                runCatching { materialProvider?.destroyMaterials() }
                runCatching { materialProvider?.destroy() }

                runCatching { view?.let(activeEngine::destroyView) }
                runCatching { scene?.let(activeEngine::destroyScene) }
                runCatching { renderer?.let(activeEngine::destroyRenderer) }
                runCatching { swapChain?.let(activeEngine::destroySwapChain) }

                if (cameraEntity != 0) {
                    runCatching { activeEngine.destroyCameraComponent(cameraEntity) }
                    runCatching { activeEngine.destroyEntity(cameraEntity) }
                }
                if (lightEntity != 0) {
                    runCatching { activeEngine.getLightManager().destroy(lightEntity) }
                    runCatching { activeEngine.destroyEntity(lightEntity) }
                }
                runCatching { activeEngine.destroy() }
            }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        state: WolfFilamentMuxerState,
        bufferInfo: MediaCodec.BufferInfo,
        waitForEndOfStream: Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + 90_000L

        while (!state.endOfStream) {
            val outputIndex = encoder.dequeueOutputBuffer(
                bufferInfo,
                if (waitForEndOfStream) 10_000L else 0L
            )

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEndOfStream) return
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        throw IllegalStateException("انتهت مهلة انتظار اكتمال ترميز Filament")
                    }
                    SystemClock.sleep(5L)
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!state.started) { "تغير تنسيق مرمّز Filament أكثر من مرة" }
                    state.videoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    state.started = true
                }

                outputIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        ?: throw IllegalStateException("تعذرت قراءة إطار الفيديو المشفر")

                    if (
                        bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        check(state.started && state.videoTrack >= 0) {
                            "وصلت عينة فيديو قبل تهيئة مسار MP4"
                        }
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(state.videoTrack, outputBuffer, bufferInfo)
                    }

                    val isEndOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (isEndOfStream) {
                        state.endOfStream = true
                    }
                }
            }
        }
    }

    private fun normalizeVideoTimestamps(
        inputFile: File,
        outputFile: File,
        fps: Int
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var completed = false

        try {
            extractor.setDataSource(inputFile.absolutePath)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: throw IllegalStateException("ملف Filament الخام لا يحتوي مسار فيديو")

            val sourceFormat = extractor.getTrackFormat(videoTrack)
            val activeMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = activeMuxer
            val outputTrack = activeMuxer.addTrack(sourceFormat)
            activeMuxer.start()
            muxerStarted = true
            extractor.selectTrack(videoTrack)

            val capacity = if (sourceFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                sourceFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    .coerceIn(256 * 1024, 12 * 1024 * 1024)
            } else {
                4 * 1024 * 1024
            }
            val sampleBuffer = ByteBuffer.allocateDirect(capacity)
            val bufferInfo = MediaCodec.BufferInfo()
            var frameIndex = 0L

            while (true) {
                sampleBuffer.clear()
                val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) break

                val flags = extractor.sampleFlags
                check(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "تعذر ضبط عينة فيديو مشفرة"
                }
                check(flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) {
                    "تعذر ضبط عينة فيديو مجزأة"
                }

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = frameIndex * 1_000_000L / fps.toLong()
                bufferInfo.flags = if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }

                sampleBuffer.position(0)
                sampleBuffer.limit(sampleSize)
                activeMuxer.writeSampleData(outputTrack, sampleBuffer, bufferInfo)
                frameIndex++

                if (!extractor.advance()) break
            }

            check(frameIndex > 0L) { "ملف Filament الخام لا يحتوي إطارات قابلة للتصدير" }
            activeMuxer.stop()
            muxerStarted = false
            completed = true
        } finally {
            runCatching { extractor.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            if (!completed || !outputFile.isFile || outputFile.length() == 0L) {
                outputFile.delete()
            }
        }
    }

    private fun readBundledAsset(assetPath: String): ByteBuffer {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        check(bytes.isNotEmpty()) { "ملف أصل Filament فارغ: " + assetPath }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }

    private fun resolveResourcePath(modelAssetPath: String, resourceUri: String): String {
        val decodedUri = Uri.decode(resourceUri).replace('\\', '/')
        require(!decodedUri.startsWith("/") && !decodedUri.contains(":")) {
            "مسار مورد GLTF خارجي غير مسموح: " + resourceUri
        }

        val baseDirectory = modelAssetPath.substringBeforeLast('/', "")
        val combinedPath = if (baseDirectory.isBlank()) {
            decodedUri
        } else {
            baseDirectory + "/" + decodedUri
        }

        val segments = mutableListOf<String>()
        combinedPath.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    require(segments.isNotEmpty()) {
                        "مورد GLTF يحاول الخروج من مجلد الأصول"
                    }
                    segments.removeAt(segments.lastIndex)
                }
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }
}
