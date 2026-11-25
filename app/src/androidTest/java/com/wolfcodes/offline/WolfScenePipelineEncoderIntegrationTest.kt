package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.test.InstrumentationTestCase
import java.io.File

/**
 * Device-side integration coverage for the production frame -> SceneRenderPipeline ->
 * MediaCodec input Surface -> MediaMuxer MP4 path.
 *
 * These tests intentionally run on the Android 12 emulator while airplane mode is on.
 */
class WolfScenePipelineEncoderIntegrationTest : InstrumentationTestCase() {

    fun testNoImageSceneUsesProceduralPipelineAndProducesMp4Frames() {
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "wolf-stage-06-b-procedural-${System.nanoTime()}.mp4")
        val normalized = File(context.cacheDir, "wolf-stage-06-b-procedural-normalized-${System.nanoTime()}.mp4")
        val backendFrames = mutableListOf<WolfSceneRenderPipeline.Backend>()

        try {
            OfflineProductionEngine.encode(
                context = context,
                file = output,
                scenes = listOf("تحركت أشجار الغابة مع صوت خطوات بعيدة"),
                w = TEST_WIDTH,
                h = TEST_HEIGHT,
                fps = TEST_FPS,
                framesPerScene = listOf(TEST_FRAME_COUNT),
                sceneImageUris = listOf(listOf<Uri?>(null, null, null)),
                includeWatermark = false,
                typographyCues = listOf(null),
                introSeconds = 0,
                onSceneProgress = null,
                bitRate = 600_000,
                shotFrameBudgets = listOf(listOf(4, 4, 4)),
                shotPlansPerScene = listOf(testShotPlans()),
                sceneRenderContexts = listOf(testSceneContext()),
                onRenderBackend = { backendFrames += it }
            )

            assertEquals(TEST_FRAME_COUNT, backendFrames.size)
            assertTrue(backendFrames.all { it == WolfSceneRenderPipeline.Backend.PROCEDURAL })
            OfflineProductionEngine.normalizeVideoTimestamps(output, normalized, TEST_FPS)
            assertEncodedVideo(normalized)
        } finally {
            output.delete()
            normalized.delete()
        }
    }

    fun testSelectedUserImageStaysOnUploadedPipelineThroughEncoder() {
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "wolf-stage-06-b-uploaded-${System.nanoTime()}.mp4")
        val normalized = File(context.cacheDir, "wolf-stage-06-b-uploaded-normalized-${System.nanoTime()}.mp4")
        val imageFile = File(context.cacheDir, "wolf-stage-06-b-image-${System.nanoTime()}.png")
        val backendFrames = mutableListOf<WolfSceneRenderPipeline.Backend>()
        val source = Bitmap.createBitmap(96, 160, Bitmap.Config.ARGB_8888)

        try {
            Canvas(source).apply {
                drawColor(android.graphics.Color.rgb(18, 22, 32))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(130, 24, 42)
                }
                drawCircle(48f, 78f, 32f, paint)
                paint.color = android.graphics.Color.rgb(210, 190, 150)
                drawRect(12f, 118f, 84f, 148f, paint)
            }
            imageFile.outputStream().use { stream ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }

            val imageUri = Uri.fromFile(imageFile)
            OfflineProductionEngine.encode(
                context = context,
                file = output,
                scenes = listOf("ظل ثابت في الغابة خلف الأشجار"),
                w = TEST_WIDTH,
                h = TEST_HEIGHT,
                fps = TEST_FPS,
                framesPerScene = listOf(TEST_FRAME_COUNT),
                sceneImageUris = listOf(listOf(imageUri, imageUri, imageUri)),
                includeWatermark = false,
                typographyCues = listOf(null),
                introSeconds = 0,
                onSceneProgress = null,
                bitRate = 600_000,
                shotFrameBudgets = listOf(listOf(4, 4, 4)),
                shotPlansPerScene = listOf(testShotPlans()),
                sceneRenderContexts = listOf(testSceneContext()),
                onRenderBackend = { backendFrames += it }
            )

            assertEquals(TEST_FRAME_COUNT, backendFrames.size)
            assertTrue(backendFrames.all { it == WolfSceneRenderPipeline.Backend.UPLOADED_IMAGE })
            OfflineProductionEngine.normalizeVideoTimestamps(output, normalized, TEST_FPS)
            assertEncodedVideo(normalized)
        } finally {
            source.recycle()
            output.delete()
            normalized.delete()
            imageFile.delete()
        }
    }

    private fun testShotPlans(): List<StoryDirectorEngine.ShotPlan> =
        (0 until 3).map { index ->
            StoryDirectorEngine.ShotPlan(
                number = index + 1,
                type = "تأسيس المشهد",
                camera = "لقطة 50mm ثابتة",
                composition = "تكوين مركزي متوازن",
                lighting = "إضاءة ليلية محدودة",
                motion = "تثبيت",
                durationSeconds = 1,
                prompt = "اختبار مسار الرندر الموحد"
            )
        }

    private fun testSceneContext() = StoryDirectorEngine.SceneRenderContext(
        sceneNumber = 1,
        setting = "غابة",
        mood = "توتر",
        continuityNotes = "بيئة غابة ثابتة للاختبار",
        continuityKey = 106,
        event = StoryDirectorEngine.StoryEvent.SUSPENSE,
        visualStyle = "رعب سينمائي مظلم",
        horrorType = "رعب نفسي"
    )

    private fun assertEncodedVideo(file: File) {
        assertTrue("MP4 output was not created", file.isFile && file.length() > 0L)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }
            val track = videoTrack ?: throw AssertionError("Encoded MP4 has no video track")
            val format = extractor.getTrackFormat(track)
            assertEquals(TEST_WIDTH, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(TEST_HEIGHT, format.getInteger(MediaFormat.KEY_HEIGHT))

            extractor.selectTrack(track)
            var sampleCount = 0
            var previousPtsUs = -1L
            while (extractor.sampleTime >= 0L) {
                val currentPtsUs = extractor.sampleTime
                assertTrue(
                    "Video PTS must increase: previous=$previousPtsUs current=$currentPtsUs",
                    currentPtsUs > previousPtsUs
                )
                previousPtsUs = currentPtsUs
                sampleCount++
                if (!extractor.advance()) break
            }
            assertTrue("Expected at least $TEST_FRAME_COUNT encoded samples, got $sampleCount",
                sampleCount >= TEST_FRAME_COUNT)
            assertTrue("Encoded video timeline is shorter than expected: $previousPtsUs",
                previousPtsUs >= 750_000L)
        } finally {
            extractor.release()
        }
    }

    private companion object {
        const val TEST_WIDTH = 320
        const val TEST_HEIGHT = 568
        const val TEST_FPS = 12
        const val TEST_FRAME_COUNT = 12
    }
}
