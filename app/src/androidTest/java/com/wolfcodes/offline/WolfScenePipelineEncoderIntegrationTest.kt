package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-side integration coverage for the production frame -> SceneRenderPipeline ->
 * MediaCodec input Surface -> MediaMuxer MP4 path.
 *
 * These tests intentionally run on the Android 12 emulator while airplane mode is on.
 */
@RunWith(AndroidJUnit4::class)
class WolfScenePipelineEncoderIntegrationTest {

    @Test
    fun testNoImageSceneUsesProceduralPipelineAndProducesMp4Frames() {
        val context = targetContext()
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

    @Test
    fun testSelectedUserImageStaysOnUploadedPipelineThroughEncoder() {
        val context = targetContext()
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

    @Test
    fun imageFreeBeachSceneEncodesThroughTheProductionMp4Pipeline() {
        val context = targetContext()
        val output = File(context.cacheDir, "wolf-stage-06-c-beach-${System.nanoTime()}.mp4")
        val backendFrames = mutableListOf<WolfSceneRenderPipeline.Backend>()
        try {
            OfflineProductionEngine.encode(
                context = context,
                file = output,
                scenes = listOf("على الشاطئ المهجور تتراجع الأمواج تحت ضباب كثيف وصوت غامض يقترب"),
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
                sceneRenderContexts = listOf(
                    StoryDirectorEngine.SceneRenderContext(
                        sceneNumber = 1,
                        setting = "شاطئ أو ساحل",
                        mood = "رعب سمعي وترقب",
                        continuityNotes = "شاطئ مهجور ثابت مع أفق بحري",
                        continuityKey = 603,
                        event = StoryDirectorEngine.StoryEvent.SUSPENSE,
                        visualStyle = "رعب سينمائي مظلم",
                        horrorType = "رعب نفسي"
                    )
                ),
                onRenderBackend = { backendFrames += it }
            )
            assertEquals(TEST_FRAME_COUNT, backendFrames.size)
            assertTrue(backendFrames.all { it == WolfSceneRenderPipeline.Backend.PROCEDURAL })
            assertEncodedVideo(output)
        } finally {
            output.delete()
        }
    }

    @Test
    fun imageFreeBeachFrameSeparatesSkyWaterWetShoreAndSand() {
        val bitmap = renderImageFreeScene(
            "على الشاطئ المهجور تتراجع الأمواج تحت ضباب كثيف وصوت غامض يقترب"
        )
        try {
            val sky = bitmap.getPixel((bitmap.width * 0.18f).toInt(), (bitmap.height * 0.18f).toInt())
            val sea = bitmap.getPixel((bitmap.width * 0.18f).toInt(), (bitmap.height * 0.60f).toInt())
            val wetShore = bitmap.getPixel((bitmap.width * 0.18f).toInt(), (bitmap.height * 0.83f).toInt())
            val sand = bitmap.getPixel((bitmap.width * 0.18f).toInt(), (bitmap.height * 0.96f).toInt())
            assertTrue("Beach render must separate sky from sea", colorDistance(sky, sea) > 24)
            assertTrue("Beach render must separate sea from wet shore", colorDistance(sea, wetShore) > 18)
            assertTrue("Beach render must separate wet and dry sand", colorDistance(wetShore, sand) > 8)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun imageFreeOpenSeaFrameContainsHorizonAndLayeredWater() {
        val bitmap = renderImageFreeScene(
            "في البحر المفتوح ظهرت أضواء بعيدة تحت ضباب بارد ثم اختفت"
        )
        try {
            val sky = bitmap.getPixel((bitmap.width * 0.12f).toInt(), (bitmap.height * 0.20f).toInt())
            val horizonWater = bitmap.getPixel((bitmap.width * 0.12f).toInt(), (bitmap.height * 0.47f).toInt())
            val deepWater = bitmap.getPixel((bitmap.width * 0.12f).toInt(), (bitmap.height * 0.86f).toInt())
            assertTrue("Open-sea rendering must show a distinct horizon/water boundary", colorDistance(sky, horizonWater) > 20)
            assertTrue("Open-sea rendering must contain depth variation below the horizon", colorDistance(horizonWater, deepWater) > 12)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun imageFreeDesertAndForestFramesAreNotAtmosphereOnly() {
        val desert = renderImageFreeScene("عبرت الرياح كثبان الصحراء المهجورة وسط غبار الليل")
        val forest = renderImageFreeScene("في الغابة المظلمة تحركت الأشجار مع ضباب كثيف وخطوات بعيدة")
        try {
            val desertSky = desert.getPixel((desert.width * 0.14f).toInt(), (desert.height * 0.18f).toInt())
            val desertGround = desert.getPixel((desert.width * 0.14f).toInt(), (desert.height * 0.88f).toInt())
            assertTrue("Desert render must contain a separate dune/ground plane", colorDistance(desertSky, desertGround) > 22)

            val forestSky = forest.getPixel((forest.width * 0.50f).toInt(), (forest.height * 0.20f).toInt())
            val forestTreeLine = forest.getPixel((forest.width * 0.50f).toInt(), (forest.height * 0.58f).toInt())
            val forestGround = forest.getPixel((forest.width * 0.50f).toInt(), (forest.height * 0.91f).toInt())
            assertTrue("Forest render must contain a separate tree-line silhouette", colorDistance(forestSky, forestTreeLine) > 18)
            assertTrue("Forest render must distinguish ground from the sky", colorDistance(forestSky, forestGround) > 18)
        } finally {
            desert.recycle()
            forest.recycle()
        }
    }

    private fun renderImageFreeScene(caption: String): Bitmap {
        val bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888)
        val prepared = WolfSceneRenderPipeline.prepare(caption, 0, null)
        try {
            assertEquals(
                WolfSceneRenderPipeline.Backend.PROCEDURAL,
                WolfSceneRenderPipeline.render(
                    canvas = Canvas(bitmap),
                    width = TEST_WIDTH,
                    height = TEST_HEIGHT,
                    shotIndex = 0,
                    progress = 0.42f,
                    caption = caption,
                    sceneIndex = 0,
                    preparedShot = prepared
                )
            )
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        } finally {
            prepared.recycle()
        }
    }

    private fun colorDistance(first: Int, second: Int): Int =
        kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))

    /**
     * Resolve the application context through the AndroidX instrumentation API.
     * Keep both encoder scenarios on the same canonical context source.
     */
    private fun targetContext() =
        InstrumentationRegistry.getInstrumentation().targetContext

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
