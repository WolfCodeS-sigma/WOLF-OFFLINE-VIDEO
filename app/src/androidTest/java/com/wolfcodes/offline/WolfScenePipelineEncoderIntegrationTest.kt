package com.wolfcodes.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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
                scenes = listOf("ظهر ظل يلاحق الحارس بين أشجار الغابة المهجورة مع خطوات تقترب"),
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
                scenes = listOf("ظل أسود يراقب الحارس من بين أشجار المقبرة المهجورة"),
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
    fun animatedWolfIntroChangesAcrossThreeSecondsAndTransitionsIntoHorrorFilm() {
        val context = targetContext()
        val output = File(context.cacheDir, "wolf-stage-06-e-intro-${System.nanoTime()}.mp4")
        val normalized = File(context.cacheDir, "wolf-stage-06-e-intro-normalized-${System.nanoTime()}.mp4")
        var openingFrame: Bitmap? = null
        var middleFrame: Bitmap? = null
        var signatureFrame: Bitmap? = null
        var closingFrame: Bitmap? = null
        var firstStoryFrame: Bitmap? = null
        val introSeconds = WolfCinematicIdentity.INTRO_DURATION_SECONDS
        val introFrameCount = WolfCinematicIdentity.introFrameCount(introSeconds, TEST_FPS)

        try {
            OfflineProductionEngine.encode(
                context = context,
                file = output,
                scenes = listOf("طارد ظل أسود الحارس داخل ممر المستشفى المهجور حتى اختفى خلف الباب الحديدي"),
                w = TEST_WIDTH,
                h = TEST_HEIGHT,
                fps = TEST_FPS,
                framesPerScene = listOf(TEST_FRAME_COUNT),
                sceneImageUris = listOf(listOf<Uri?>(null, null, null)),
                includeWatermark = false,
                typographyCues = listOf(null),
                introSeconds = introSeconds,
                onSceneProgress = null,
                bitRate = 600_000,
                shotFrameBudgets = listOf(listOf(4, 4, 4)),
                shotPlansPerScene = listOf(testShotPlans()),
                sceneRenderContexts = listOf(testSceneContext())
            )

            OfflineProductionEngine.normalizeVideoTimestamps(output, normalized, TEST_FPS)
            assertEncodedVideo(
                normalized,
                minimumSamples = introFrameCount + TEST_FRAME_COUNT,
                minimumLastPtsUs = introSeconds * 1_000_000L + 750_000L
            )

            val opening = frameAt(normalized, 500_000L)
            openingFrame = opening
            val middle = frameAt(normalized, 1_500_000L)
            middleFrame = middle
            val signature = frameAt(normalized, 2_750_000L)
            signatureFrame = signature
            val closing = frameAt(normalized, 2_950_000L)
            closingFrame = closing
            val firstStory = frameAt(normalized, 3_250_000L)
            firstStoryFrame = firstStory

            assertTrue(
                "The wolf mark must visibly animate during the opening",
                countDifferentSamples(opening, middle) > 12
            )
            assertTrue(
                "The animated identity must continue changing through the signature reveal",
                countDifferentSamples(middle, signature) > 12
            )
            assertTrue(
                "The final black transition must separate the intro from the first horror scene",
                countDifferentSamples(closing, firstStory) > 12
            )
        } finally {
            openingFrame?.recycle()
            middleFrame?.recycle()
            signatureFrame?.recycle()
            closingFrame?.recycle()
            firstStoryFrame?.recycle()
            output.delete()
            normalized.delete()
        }
    }

    @Test
    fun storyDirectorTrackingMotionChangesFramesInTheEncodedProductionPath() {
        val context = targetContext()
        val stillOutput = File(context.cacheDir, "wolf-stage-06-d-camera-hold-${System.nanoTime()}.mp4")
        val stillNormalized = File(context.cacheDir, "wolf-stage-06-d-camera-hold-normalized-${System.nanoTime()}.mp4")
        val movingOutput = File(context.cacheDir, "wolf-stage-06-d-camera-track-${System.nanoTime()}.mp4")
        val movingNormalized = File(context.cacheDir, "wolf-stage-06-d-camera-track-normalized-${System.nanoTime()}.mp4")
        var stillFrame: Bitmap? = null
        var movingFrame: Bitmap? = null
        try {
            encodeCameraIntentVideo(
                context, stillOutput,
                motion = "تثبيت الكاميرا",
                camera = "لقطة واسعة بعدسة 24mm وكاميرا ثابتة"
            )
            encodeCameraIntentVideo(
                context, movingOutput,
                motion = "تتبّع جانبي بطيء يحافظ على اتجاه الحركة",
                camera = "لقطة واسعة بعدسة 24mm مع تتبّع جانبي مضبوط"
            )
            OfflineProductionEngine.normalizeVideoTimestamps(stillOutput, stillNormalized, TEST_FPS)
            OfflineProductionEngine.normalizeVideoTimestamps(movingOutput, movingNormalized, TEST_FPS)
            assertEncodedVideo(stillNormalized)
            assertEncodedVideo(movingNormalized)

            // Same deterministic horror scene and same presentation time; only the
            // StoryDirector camera command differs.
            val still = frameAt(stillNormalized, 750_000L)
            stillFrame = still
            val moving = frameAt(movingNormalized, 750_000L)
            movingFrame = moving
            assertTrue(
                "StoryDirector tracking must alter visible pixels in the real encoded MP4",
                countDifferentSamples(still, moving) > 12
            )
        } finally {
            stillFrame?.recycle()
            movingFrame?.recycle()
            stillOutput.delete()
            stillNormalized.delete()
            movingOutput.delete()
            movingNormalized.delete()
        }
    }

    @Test
    fun unverifiedUserPhotoKeepsAllEdgesAndStaysMotionlessInTheEncodedMp4() {
        val context = targetContext()
        val output = File(context.cacheDir, "wolf-stage-06-d-photo-${System.nanoTime()}.mp4")
        val normalized = File(context.cacheDir, "wolf-stage-06-d-photo-normalized-${System.nanoTime()}.mp4")
        val imageFile = File(context.cacheDir, "wolf-stage-06-d-photo-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(90, 160, Bitmap.Config.ARGB_8888)
        var earlyFrame: Bitmap? = null
        var lateFrame: Bitmap? = null
        var expectedFrame: Bitmap? = null
        val backendFrames = mutableListOf<WolfSceneRenderPipeline.Backend>()
        try {
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            Canvas(source).apply {
                drawColor(Color.rgb(35, 42, 52))
                markerPaint.color = Color.rgb(235, 38, 42)
                drawRect(0f, 0f, 12f, 12f, markerPaint)
                markerPaint.color = Color.rgb(35, 220, 72)
                drawRect(78f, 0f, 90f, 12f, markerPaint)
                markerPaint.color = Color.rgb(42, 82, 232)
                drawRect(0f, 148f, 12f, 160f, markerPaint)
                markerPaint.color = Color.rgb(235, 220, 36)
                drawRect(78f, 148f, 90f, 160f, markerPaint)
            }
            val expected = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888)
            expectedFrame = expected
            WolfUploadedImageEngine.drawOriginalStill(Canvas(expected), TEST_WIDTH, TEST_HEIGHT, source)
            imageFile.outputStream().use { stream ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            val imageUri = Uri.fromFile(imageFile)
            val cameraShot = StoryDirectorEngine.ShotPlan(
                number = 1,
                type = "لقطة تتبّع",
                camera = "لقطة واسعة بعدسة 24mm مع تتبّع جانبي مضبوط",
                composition = "حافظ على اتجاه الحركة نحو اليمين",
                lighting = "",
                motion = "تتبّع جانبي بطيء يحافظ على اتجاه الحركة",
                durationSeconds = 1,
                prompt = "عينة اختبار رعب خيالي غير قرآني"
            )
            OfflineProductionEngine.encode(
                context = context,
                file = output,
                scenes = listOf("تحرك ظل غامض نحو اليمين داخل ممر مستشفى مهجور"),
                w = TEST_WIDTH,
                h = TEST_HEIGHT,
                fps = TEST_FPS,
                framesPerScene = listOf(TEST_FRAME_COUNT),
                sceneImageUris = listOf(listOf(imageUri, null, null)),
                includeWatermark = true,
                typographyCues = listOf(
                    WolfHorrorTypographyEngine.Cue(
                        text = "لا تفتح الباب",
                        startFrame = 0,
                        endFrameExclusive = TEST_FRAME_COUNT,
                        placement = WolfHorrorTypographyEngine.Placement.UPPER_LEFT,
                        motion = WolfHorrorTypographyEngine.Motion.WHISPER_FADE
                    )
                ),
                introSeconds = 0,
                onSceneProgress = null,
                bitRate = 600_000,
                shotFrameBudgets = listOf(listOf(TEST_FRAME_COUNT)),
                shotPlansPerScene = listOf(listOf(cameraShot)),
                sceneRenderContexts = listOf(
                    StoryDirectorEngine.SceneRenderContext(
                        sceneNumber = 1,
                        setting = "ممر مستشفى مهجور",
                        mood = "ترقب وخوف",
                        continuityNotes = "الممر نفسه والباب في نهاية الكادر",
                        continuityKey = 604,
                        event = StoryDirectorEngine.StoryEvent.SUSPENSE,
                        visualStyle = "رعب سينمائي مظلم",
                        horrorType = "رعب نفسي"
                    )
                ),
                onRenderBackend = { backendFrames += it }
            )
            assertEquals(TEST_FRAME_COUNT, backendFrames.size)
            assertTrue(backendFrames.all { it == WolfSceneRenderPipeline.Backend.UPLOADED_IMAGE })
            OfflineProductionEngine.normalizeVideoTimestamps(output, normalized, TEST_FPS)
            assertEncodedVideo(normalized)

            val early = frameAt(normalized, 250_000L)
            earlyFrame = early
            val late = frameAt(normalized, 750_000L)
            lateFrame = late
            assertColorNear(early, 20, 20, Color.rgb(235, 38, 42), "top-left source edge")
            assertColorNear(early, 300, 20, Color.rgb(35, 220, 72), "top-right source edge")
            assertColorNear(early, 20, 548, Color.rgb(42, 82, 232), "bottom-left source edge")
            assertColorNear(early, 300, 548, Color.rgb(235, 220, 36), "bottom-right source edge")
            assertTrue(
                "Typography and watermark must not alter an unverified original photo",
                meanSampledColorDistance(early, expected) < 12.0
            )
            assertTrue(
                "An unverified user photo must not receive camera, parallax or atmospheric motion",
                meanSampledColorDistance(early, late) < 8.0
            )
        } finally {
            earlyFrame?.recycle()
            lateFrame?.recycle()
            expectedFrame?.recycle()
            source.recycle()
            output.delete()
            normalized.delete()
            imageFile.delete()
        }
    }

    @Test
    fun imageFreeBeachSceneKeepsTheSelectedMonochromeStyle() {
        val bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888)
        val caption = "على الشاطئ المهجور تتراجع الأمواج تحت ضباب كثيف وصوت غامض يقترب"
        val plan = WolfProceduralSceneProductionEngine.plan(caption, 0)
        val context = StoryDirectorEngine.SceneRenderContext(
            sceneNumber = 1,
            setting = "شاطئ أو ساحل",
            mood = "رعب سمعي وترقب",
            continuityNotes = "شاطئ مهجور ثابت مع أفق بحري",
            continuityKey = 604,
            event = StoryDirectorEngine.StoryEvent.SUSPENSE,
            visualStyle = "أبيض وأسود",
            horrorType = "رعب نفسي"
        )
        try {
            WolfProceduralSceneProductionEngine.render(
                canvas = Canvas(bitmap),
                width = TEST_WIDTH,
                height = TEST_HEIGHT,
                plan = plan,
                shotIndex = 0,
                progress = 0.42f,
                sceneRenderContext = context
            )
            val sample = bitmap.getPixel((bitmap.width * 0.18f).toInt(), (bitmap.height * 0.60f).toInt())
            assertTrue("Monochrome sea scene must keep neutral RGB channels",
                kotlin.math.abs(Color.red(sample) - Color.green(sample)) <= 5 &&
                    kotlin.math.abs(Color.green(sample) - Color.blue(sample)) <= 5)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun imageFreeBeachSceneEncodesThroughTheProductionMp4Pipeline() {
        val context = targetContext()
        val output = File(context.cacheDir, "wolf-stage-06-c-beach-${System.nanoTime()}.mp4")
        val normalized = File(context.cacheDir, "wolf-stage-06-c-beach-normalized-${System.nanoTime()}.mp4")
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
            // Match the production post-encode PTS normalization path used by the encoder tests.
            OfflineProductionEngine.normalizeVideoTimestamps(output, normalized, TEST_FPS)
            assertEncodedVideo(normalized)
        } finally {
            output.delete()
            normalized.delete()
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
            "في البحر المفتوح ظهرت أضواء بعيدة تحت ضباب بارد ثم صعد ظل غريق من الماء واختفى"
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
        val desert = renderImageFreeScene("طارد ظل أسود الحارس عبر كثبان الصحراء المهجورة وسط غبار الليل")
        val forest = renderImageFreeScene("في الغابة المظلمة تبع ظل بلا وجه الحارس بين الأشجار وسط ضباب كثيف وخطوات بعيدة")
        try {
            val desertSky = desert.getPixel((desert.width * 0.14f).toInt(), (desert.height * 0.18f).toInt())
            val desertGround = desert.getPixel((desert.width * 0.14f).toInt(), (desert.height * 0.88f).toInt())
            assertTrue("Desert render must contain a separate dune/ground plane", colorDistance(desertSky, desertGround) > 22)

            val forestSky = forest.getPixel((forest.width * 0.50f).toInt(), (forest.height * 0.20f).toInt())
            val treeLineY = (forest.height * 0.58f).toInt()
            val treeLineLuminance = (0 until forest.width step 2).map { x ->
                val pixel = forest.getPixel(x, treeLineY)
                (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            }
            val treeLineContrast = treeLineLuminance.maxOrNull()!! - treeLineLuminance.minOrNull()!!
            val forestGround = forest.getPixel((forest.width * 0.50f).toInt(), (forest.height * 0.91f).toInt())
            assertTrue(
                "Forest render must show contrast between tree silhouettes and backlit mist",
                treeLineContrast > 18
            )
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

    private fun encodeCameraIntentVideo(
        context: android.content.Context,
        file: File,
        motion: String,
        camera: String
    ) {
        val caption = "تحرك الظل نحو اليمين داخل ممر المستشفى المهجور ثم اختفى خلف الباب"
        val shot = StoryDirectorEngine.ShotPlan(
            number = 1,
            type = "لقطة كاميرا",
            camera = camera,
            composition = "اتجاه الحركة نحو اليمين مع الحفاظ على استمرارية الممر",
            lighting = "",
            motion = motion,
            durationSeconds = 1,
            prompt = "عينة رعب خيالي غير قرآني لاختبار حركة الكاميرا"
        )
        OfflineProductionEngine.encode(
            context = context,
            file = file,
            scenes = listOf(caption),
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
            shotFrameBudgets = listOf(listOf(TEST_FRAME_COUNT)),
            shotPlansPerScene = listOf(listOf(shot)),
            sceneRenderContexts = listOf(
                StoryDirectorEngine.SceneRenderContext(
                    sceneNumber = 1,
                    setting = "ممر مستشفى مهجور",
                    mood = "ترقب وخوف",
                    continuityNotes = "ممر واحد وباب ثابت في نهاية المسار",
                    continuityKey = 604,
                    event = StoryDirectorEngine.StoryEvent.CHASE,
                    visualStyle = "رعب سينمائي مظلم",
                    horrorType = "رعب نفسي"
                )
            )
        )
    }

    private fun frameAt(file: File, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw AssertionError("Could not decode an MP4 frame at $timeUs us")
        } finally {
            retriever.release()
        }
    }

    private fun countDifferentSamples(first: Bitmap, second: Bitmap): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var different = 0
        for (y in 8 until first.height step 3) {
            for (x in 8 until first.width step 3) {
                if (colorDistance(first.getPixel(x, y), second.getPixel(x, y)) > 24) different++
            }
        }
        return different
    }

    private fun meanSampledColorDistance(first: Bitmap, second: Bitmap): Double {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var total = 0L
        var samples = 0L
        for (y in 8 until first.height step 8) {
            for (x in 8 until first.width step 8) {
                total += colorDistance(first.getPixel(x, y), second.getPixel(x, y))
                samples++
            }
        }
        return total.toDouble() / samples.coerceAtLeast(1L).toDouble()
    }

    private fun assertColorNear(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        expected: Int,
        label: String
    ) {
        val actual = bitmap.getPixel(x, y)
        assertTrue(
            "$label was cropped or changed: expected=$expected actual=$actual",
            colorDistance(actual, expected) < 150
        )
    }

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
                prompt = "عينة رعب خيالي لاختبار مسار الرندر الموحد"
            )
        }

    private fun testSceneContext() = StoryDirectorEngine.SceneRenderContext(
        sceneNumber = 1,
        setting = "غابة",
        mood = "توتر",
        continuityNotes = "غابة مقبرة مهجورة ثابتة للاختبار",
        continuityKey = 106,
        event = StoryDirectorEngine.StoryEvent.SUSPENSE,
        visualStyle = "رعب سينمائي مظلم",
        horrorType = "رعب نفسي"
    )

    private fun assertEncodedVideo(
        file: File,
        minimumSamples: Int = TEST_FRAME_COUNT,
        minimumLastPtsUs: Long = 750_000L
    ) {
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
            assertTrue("Expected at least $minimumSamples encoded samples, got $sampleCount",
                sampleCount >= minimumSamples)
            assertTrue("Encoded video timeline is shorter than expected: $previousPtsUs",
                previousPtsUs >= minimumLastPtsUs)
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
