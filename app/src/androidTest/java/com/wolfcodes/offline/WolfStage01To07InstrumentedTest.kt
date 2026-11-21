package com.wolfcodes.offline

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-side regression tests for Android MediaCodec, MediaMuxer, MediaStore
 * and persistent chapter checkpoints. These execute on an Android emulator,
 * not on the host JVM where Android media APIs are unavailable.
 */
@RunWith(AndroidJUnit4::class)
class WolfStage01To07InstrumentedTest {

    @Test
    fun thirtySecondFilmRetainsRuntimeWithSevenSecondAudioAndUserStillImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureDirectory = File(context.cacheDir, "wolf-stage-01-07-device-tests").apply {
            if (!exists()) mkdirs()
        }
        val narrationFixture = File(fixtureDirectory, "seven-second-audio.m4a")
        val imageFixture = File(fixtureDirectory, "user-still.png")
        var exportedUri: Uri? = null

        try {
            narrationFixture.delete()
            imageFixture.delete()

            // A real AAC file is generated through the device's MediaCodec encoder.
            // The seven-second duration mirrors the short-narration regression case.
            WolfSoundFxEngine.renderAac(
                scenes = listOf("طرق بطيء على الباب ثم خطوات تقترب في ممر مظلم"),
                durationSeconds = 7,
                output = narrationFixture
            )
            assertTrue("The seven-second AAC fixture was not created", narrationFixture.isFile)
            assertTrue("The AAC fixture is empty", narrationFixture.length() > 0L)

            // This test image stands in for a selected still. It verifies the actual
            // image-to-encoder path while semantic segmentation remains fail-closed.
            val still = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            try {
                still.eraseColor(Color.rgb(24, 96, 184))
                imageFixture.outputStream().use { output ->
                    assertTrue(still.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            } finally {
                still.recycle()
            }

            assertFalse(
                "Color heuristics must not be promoted to semantic segmentation",
                WolfImageDepthSegmenter.isSemanticSegmentationReady()
            )

            val result = OfflineProductionEngine.render(
                context = context,
                story = "في منتصف الليل سمعت طرقًا بطيئًا على الباب. اقتربت الخطوات من الممر. انطفأ الضوء فجأة. ظهر ظل خلف النافذة.",
                seconds = 30,
                imageUris = listOf(Uri.fromFile(imageFixture)),
                audioUri = Uri.fromFile(narrationFixture),
                aspectRatio = "1:1",
                generateSoundEffects = false,
                includeWatermark = false,
                includeIntro = true,
                audioDurationIsAuthoritative = true,
                audioDurationMs = 7_000L,
                enableHorrorTypography = false,
                projectId = "wolf-stage-audit-${System.currentTimeMillis()}"
            )

            assertEquals("The requested film duration must be authoritative", 30, result.durationSeconds)
            assertNotNull("The exporter did not return a saved media URI", result.mediaUri)
            exportedUri = Uri.parse(requireNotNull(result.mediaUri))

            val report = WolfFilmQualityInspector.inspect(
                context = context,
                mediaReference = requireNotNull(result.mediaUri),
                expectedDurationSeconds = 30,
                requireAudio = true
            )
            assertTrue("Exported MP4 failed structural QA: ${report.summary}", report.passed)
            assertTrue("Expected the full video frame sequence, got ${report.videoSamples}", report.videoSamples >= 350L)
            assertTrue("The final MP4 contains no readable audio samples", report.audioSamples > 0L)
            assertNotNull("The exported MP4 duration is missing", report.durationSeconds)
            assertTrue(
                "The video stream did not retain a 30-second duration: ${report.durationSeconds}",
                requireNotNull(report.durationSeconds) in 29.0..31.0
            )

            val timings = readLastSampleTimes(context, requireNotNull(exportedUri))
            val video = requireNotNull(timings["video/avc"]) {
                "The final MP4 has no H.264 video samples"
            }
            val audio = requireNotNull(timings["audio/mp4a-latm"]) {
                "The final MP4 has no AAC audio samples"
            }

            assertTrue(
                "Video ended before the requested 30-second timeline: ${video.lastPresentationTimeUs} us",
                video.lastPresentationTimeUs in 29_500_000L..30_100_000L
            )
            assertTrue(
                "Audio fixture should end near seven seconds, not near the end of the film: ${audio.lastPresentationTimeUs} us",
                audio.lastPresentationTimeUs in 6_500_000L..7_500_000L
            )

            // The output must remain decodable after the mux operation, and the
            // blue-dominant centre pixel confirms the selected still reached video.
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, requireNotNull(exportedUri))
                val frame = retriever.getFrameAtTime(5_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                assertNotNull("Could not decode a frame from the final MP4", frame)
                try {
                    val pixel = requireNotNull(frame).getPixel(frame.width / 2, frame.height / 2)
                    assertTrue(
                        "The selected still image was not visible in the encoded frame",
                        Color.blue(pixel) > Color.red(pixel)
                    )
                } finally {
                    frame?.recycle()
                }
            } finally {
                retriever.release()
            }
        } finally {
            exportedUri?.let { uri ->
                if (uri.scheme == "content") {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
            narrationFixture.delete()
            imageFixture.delete()
        }
    }

    @Test(timeout = 600_000L)
    fun atmosphericFallbackWithoutUserMediaExportsDecodableSilentMp4() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var exportedUri: Uri? = null
        try {
            val result = OfflineProductionEngine.render(
                context = context,
                story = "في الليل هبت رياح باردة وارتفع الضباب حول المكان ثم ساد الصمت.",
                seconds = 5,
                imageUris = emptyList(),
                audioUri = null,
                aspectRatio = "1:1",
                generateSoundEffects = false,
                includeWatermark = false,
                includeIntro = false,
                enableHorrorTypography = false,
                projectId = "wolf-stage-fallback-${System.currentTimeMillis()}"
            )

            assertEquals("The silent fallback must preserve the requested runtime", 5, result.durationSeconds)
            assertNotNull("The fallback exporter did not save an MP4", result.mediaUri)
            exportedUri = Uri.parse(requireNotNull(result.mediaUri))

            val report = WolfFilmQualityInspector.inspect(
                context = context,
                mediaReference = requireNotNull(result.mediaUri),
                expectedDurationSeconds = 5,
                requireAudio = false
            )
            assertTrue("Atmospheric fallback MP4 failed structural QA: ${report.summary}", report.passed)
            assertFalse("The no-audio fallback unexpectedly added an audio track", report.hasAudio)
            assertTrue("Expected about 60 video frames, got ${report.videoSamples}", report.videoSamples >= 58L)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, requireNotNull(exportedUri))
                val frame = retriever.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                assertNotNull("The atmospheric fallback frame could not be decoded", frame)
                try {
                    val pixel = requireNotNull(frame).getPixel(frame.width / 2, frame.height / 2)
                    assertTrue("The fallback renderer returned a fully black frame", pixel != Color.BLACK)
                } finally {
                    frame?.recycle()
                }
            } finally {
                retriever.release()
            }
        } finally {
            exportedUri?.let { uri ->
                if (uri.scheme == "content") {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun colorHeuristicLayersAreRejectedByDepthComposer() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x in 12..51 && y in 12..51) Color.WHITE else Color.BLACK
        }
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        source.setPixels(pixels, 0, width, 0, 0, width, height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val layers = WolfImageDepthSegmenter.segment(source)
        try {
            assertTrue(layers.heuristicCandidateSeparated)
            assertFalse(layers.semanticValidationPassed)
            assertFalse(layers.safeForParallax)
            WolfSceneDepthComposer.renderImageLayers(
                canvas = Canvas(output),
                width = width,
                height = height,
                sceneIndex = 0,
                progress = 0.5f,
                layers = layers,
                source = Rect(0, 0, width, height),
                destination = RectF(0f, 0f, width.toFloat(), height.toFloat()),
                rotationDegrees = 0f
            )
        } finally {
            layers.recycle()
            source.recycle()
            output.recycle()
        }
    }

    @Test
    fun changedRenderFingerprintInvalidatesCompletedChapterOnAndroidStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectId = "wolf-stage-checkpoint-${System.currentTimeMillis()}"
        val plans = WolfLongFilmEngine.planChapters(
            framesPerScene = listOf(3_600),
            framesPerSecond = 12,
            introFrames = 0
        )
        val oldFingerprint = "storyboard-planner-v2"
        val newFingerprint = OfflineProductionEngine.STORYBOARD_RENDER_REVISION

        try {
            val initial = WolfLongFilmCheckpointStore.begin(
                context, projectId, oldFingerprint, 12, 3_600, plans
            )
            assertEquals(WolfLongFilmEngine.ChapterStatus.PENDING, initial.chapters.single().status)

            val chapterFile = WolfLongFilmCheckpointStore.chapterFile(
                context, projectId, oldFingerprint, 1
            )
            chapterFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            WolfLongFilmCheckpointStore.markCompleted(context, projectId, 1, chapterFile)

            val sameRender = WolfLongFilmCheckpointStore.begin(
                context, projectId, oldFingerprint, 12, 3_600, plans
            )
            assertEquals(
                "A completed chapter should be reusable only for the same render fingerprint",
                WolfLongFilmEngine.ChapterStatus.COMPLETED,
                sameRender.chapters.single().status
            )

            val changedRender = WolfLongFilmCheckpointStore.begin(
                context, projectId, newFingerprint, 12, 3_600, plans
            )
            assertEquals(
                "A changed render fingerprint must reset the old completed chapter",
                WolfLongFilmEngine.ChapterStatus.PENDING,
                changedRender.chapters.single().status
            )
            assertFalse("The obsolete chapter artifact should be removed", chapterFile.exists())
        } finally {
            WolfLongFilmCheckpointStore.clear(context, projectId)
        }
    }


    @Test
    fun staleOfflineCleanupDeletesOnlyOwnedExpiredFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val offlineDirectory = File(context.cacheDir, "WOLF_OFFLINE").apply { mkdirs() }
        val nestedDirectory = File(offlineDirectory, "nested").apply { mkdirs() }
        val ownedPrefixes = listOf(
            "silent_wolf_offline_cleanup.mp4",
            "sfx_wolf_offline_cleanup.m4a",
            "normalized_wolf_offline_cleanup.mp4",
            "converted_wolf_offline_cleanup.m4a",
            "layered_wolf_offline_cleanup.m4a",
            "layered_wolf_offline_cleanup_voice.pcm",
            "layered_wolf_offline_cleanup_sfx.pcm",
            "layered_wolf_offline_cleanup_mix.pcm",
            "wolf_offline_cleanup.mp4"
        )
        val expiredFiles = ownedPrefixes.mapIndexed { index, name ->
            File(offlineDirectory, name).apply {
                writeBytes(byteArrayOf(index.toByte(), 1, 2, 3))
                setLastModified(now - 96L * 60L * 60L * 1000L)
            }
        }
        val recentOwned = File(offlineDirectory, "wolf_offline_recent_cleanup.mp4").apply {
            writeBytes(byteArrayOf(9, 8, 7))
            setLastModified(now)
        }
        val unrelatedCache = File(offlineDirectory, "user_selected_photo_cleanup.png").apply {
            writeBytes(byteArrayOf(6, 5, 4))
            setLastModified(now - 96L * 60L * 60L * 1000L)
        }
        val nestedOwned = File(nestedDirectory, "wolf_offline_nested_cleanup.mp4").apply {
            writeBytes(byteArrayOf(3, 2, 1))
            setLastModified(now - 96L * 60L * 60L * 1000L)
        }
        val projectRecord = File(context.filesDir, "wolf-project-cleanup-preservation-test.json").apply {
            writeText("preserve-me")
        }

        try {
            val expectedReleasedBytes = expiredFiles.sumOf { it.length() }
            val result = WolfMemoryManager.cleanupStaleOfflineTemps(
                context = context,
                now = now,
                maxAgeMs = 72L * 60L * 60L * 1000L
            )

            expiredFiles.forEach { file ->
                assertFalse("Expired WOLF temporary file was not deleted: ${file.name}", file.exists())
            }
            assertTrue("Cleanup did not report every expired WOLF temporary file", result.removedFiles >= expiredFiles.size)
            assertTrue("Cleanup reported fewer released bytes than the test fixtures", result.releasedBytes >= expectedReleasedBytes)
            assertTrue("A recent WOLF temporary file was deleted", recentOwned.exists())
            assertTrue("An unrelated cached user asset was deleted", unrelatedCache.exists())
            assertTrue("Cleanup unexpectedly traversed nested directories", nestedOwned.exists())
            assertTrue("A persistent project record was deleted", projectRecord.exists())
        } finally {
            expiredFiles.forEach { it.delete() }
            recentOwned.delete()
            unrelatedCache.delete()
            nestedOwned.delete()
            nestedDirectory.delete()
            projectRecord.delete()
        }
    }

    @Test(timeout = 300_000L)
    fun legacyOfflineVideoExporterStillProducesAReadableFiveSecondMp4() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var outputUri: Uri? = null
        try {
            val project = WolfProjectStore.newProject(
                story = "دخلت الغرفة المظلمة. سمعت صوتًا خلف الباب.",
                sourceUrl = "",
                aspectRatio = "16:9",
                durationSeconds = 5,
                visualStyle = "رعب سينمائي مظلم",
                horrorType = "رعب نفسي",
                soundEffectsEnabled = false,
                imageUris = emptyList(),
                audioUri = null,
                includeIntro = false,
                includeWatermark = false
            )
            val exported = WolfOfflineVideoExporter(context).export(project) { _, _ -> }
            outputUri = exported.uri

            assertEquals(5, exported.durationSeconds)
            val report = WolfFilmQualityInspector.inspect(
                context = context,
                mediaReference = exported.uri.toString(),
                expectedDurationSeconds = 5,
                requireAudio = false
            )
            assertTrue("Legacy exporter MP4 failed structural QA: ${report.summary}", report.passed)
            assertFalse("Legacy silent exporter unexpectedly added audio", report.hasAudio)
            assertTrue("Legacy exporter produced too few video samples: ${report.videoSamples}", report.videoSamples >= 115L)
        } finally {
            outputUri?.let { uri ->
                if (uri.scheme == "content") {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
        }
    }

    @Test
    fun mainActivityCanLaunchAndCompleteItsInitialScreenLifecycle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            assertNotNull("MainActivity did not launch", activity)
            assertFalse("MainActivity finished during initial screen setup", activity.isFinishing)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private data class TrackTiming(
        val samples: Long,
        val lastPresentationTimeUs: Long
    )

    private fun readLastSampleTimes(context: android.content.Context, mediaUri: Uri): Map<String, TrackTiming> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, mediaUri, null)
            val timings = linkedMapOf<String, TrackTiming>()
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue

                extractor.selectTrack(trackIndex)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var sampleCount = 0L
                var lastPresentationTimeUs = -1L
                while (extractor.sampleTrackIndex >= 0) {
                    if (extractor.sampleTrackIndex == trackIndex) {
                        sampleCount++
                        lastPresentationTimeUs = extractor.sampleTime
                    }
                    if (!extractor.advance()) break
                }
                extractor.unselectTrack(trackIndex)
                timings[mime] = TrackTiming(sampleCount, lastPresentationTimeUs)
            }
            return timings
        } finally {
            extractor.release()
        }
    }
}
