package com.wolfcodes.offline

import android.Manifest
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
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
 * End-to-end Android coverage for the approved manual narration workflow.
 *
 * The scenario uses a fictional horror story only. It records two short AAC takes,
 * verifies that quiet time does not dismiss the Teleprompter, manually stops each
 * take, checks the merged M4A samples, recreates the Activity, reopens the saved
 * project, and appends a second segment without losing the first one.
 */
@RunWith(AndroidJUnit4::class)
class WolfTeleprompterAndroidTest {

    @Test
    fun manualTeleprompterKeepsApprovedStoryAndMergesAacSegmentsAfterProjectReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO
        )

        val story = buildString {
            append("عند الثالثة بعد منتصف الليل، سمع الحارس طرقات بطيئة تصعد من قبو المستشفى المهجور. ")
            append("كان الممر خاليًا، لكن ظلًا طويلًا ظهر تحت الباب الحديدي ثم اختفى عندما اقترب.")
            repeat(12) { index ->
                append("\n\n")
                append("في الجولة التالية، عاد الحارس إلى الممر نفسه. ")
                append("كان المصباح يومض فوق غرفة الأرشيف، وظهرت آثار أقدام مبتلة تتجه نحو السرداب. ")
                append("توقف الحارس ليستمع، فصدر الطرق هذه المرة من خلفه، رغم أن الباب ظل مغلقًا. ")
                append("لم يرَ أحدًا في الممر، لكن الظل تحرك عكس اتجاه الضوء في نهاية الرواق رقم ")
                append(index + 1)
                append(".")
            }
        }
        val title = "WOLF 6-G voice ${System.nanoTime()}"
        var projectId: String? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity {
                (findWindowView(WolfUiTestTags.PROJECT_TITLE) as EditText).setText(title)
                (findWindowView(WolfUiTestTags.PROJECT_STORY) as EditText).setText(story)
                (findWindowView(WolfUiTestTags.PROJECT_DURATION) as EditText).setText("300")
                (findWindowView(WolfUiTestTags.STORY_APPROVE) as Button).performClick()
            }

            val project = awaitProject(context, title)
            projectId = project.id

            startRecordingAndAwaitTeleprompter(scenario)
            assertManualTeleprompterAndRtl(scenario, story)

            // The emulator's input may be quiet. The recorder must remain active
            // until the user presses Stop; speech detection is not an auto-stop rule.
            SystemClock.sleep(1_700L)
            scenario.onActivity {
                val stop = findWindowView(WolfUiTestTags.TELEPROMPTER_STOP) as? Button
                assertNotNull("The manual stop action must remain available during quiet time", stop)
                assertTrue("The recording must not auto-stop during a short quiet interval", stop!!.isEnabled)
                val progress = findWindowView(WolfUiTestTags.TELEPROMPTER_PROGRESS) as TextView
                val elapsedSeconds = Regex("التسجيل مستمر — (\\d+) ثانية")
                    .find(progress.text.toString())?.groupValues?.get(1)?.toIntOrNull() ?: 0
                assertTrue("The Teleprompter must show at least one elapsed second", elapsedSeconds >= 1)
            }

            stopRecording(scenario)
            awaitMainStatus(scenario) { it.contains("يوجد تسجيل من 1 أجزاء") }

            val firstSession = requireNotNull(WolfStoryVoiceStore.read(context, project.id))
            val firstDurationMs = firstSession.audioDurationMs
            val firstTake = requireNotNull(firstSession.voiceFilePath).let(::File)
            val firstMergedLength = firstTake.length()
            assertEquals(1, WolfVoiceSegmentStore.segmentCount(context, project.id))
            assertTrue(firstDurationMs >= WolfVoiceRecordingPolicy.MIN_RECORDING_DURATION_MS)
            assertTrue(firstTake.isFile && firstMergedLength > 1024L)
            assertAacTrackHasIncreasingSamples(firstTake)

            // Recreate the Activity and reopen the persisted project before recording
            // the next part. This exercises the same recovery/import path used on reopen.
            scenario.recreate()
            scenario.onActivity {
                val openProject = findWindowView(WolfUiTestTags.PROJECT_OPEN_PREFIX + project.id) as? Button
                assertNotNull("The saved narration project must remain in the project list", openProject)
                openProject!!.performClick()
            }
            startRecordingAndAwaitTeleprompter(scenario)
            scenario.onActivity {
                val script = findWindowView(WolfUiTestTags.TELEPROMPTER_SCRIPT) as TextView
                assertEquals("Reopened projects must show the exact approved script", story, script.text.toString())
                assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, script.textDirection)
            }

            SystemClock.sleep(1_700L)
            stopRecording(scenario)
            awaitMainStatus(scenario) { it.contains("يوجد تسجيل من 2 أجزاء") }

            val finalSession = requireNotNull(WolfStoryVoiceStore.read(context, project.id))
            val mergedFile = requireNotNull(finalSession.voiceFilePath).let(::File)
            assertEquals(2, finalSession.takeNumber)
            assertEquals(2, WolfVoiceSegmentStore.segmentCount(context, project.id))
            assertTrue("The second take must extend, not replace, the previous recording",
                finalSession.audioDurationMs > firstDurationMs)
            assertEquals(
                WolfStoryVoiceStateMachine.storyFingerprint(story),
                finalSession.recordingStoryHash
            )
            assertFalse("A saved recording remains unapproved until the user reviews it", finalSession.voiceApproved)
            assertTrue(mergedFile.isFile && mergedFile.length() > firstMergedLength)
            assertAacTrackHasIncreasingSamples(mergedFile)
        } finally {
            scenario.close()
            val id = projectId ?: WolfProjectStore.list(context).firstOrNull { it.title == title }?.id
            if (id != null) {
                WolfStoryVoiceStore.clearProject(context, id)
                WolfProjectStore.delete(context, id)
            }
        }
    }

    private fun startRecordingAndAwaitTeleprompter(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity {
                if (findWindowView(WolfUiTestTags.TELEPROMPTER_STOP) == null) {
                    // Retrying is safe while project recovery is pending: MainActivity
                    // rejects overlapping starts until the persisted take is restored.
                    (findWindowView(WolfUiTestTags.VOICE_RECORD) as? Button)?.performClick()
                }
                val stop = findWindowView(WolfUiTestTags.TELEPROMPTER_STOP)
                val scroll = findWindowView(WolfUiTestTags.TELEPROMPTER_SCROLL) as? ScrollView
                ready = stop is Button && scroll != null &&
                    scroll.height > 0 && scroll.canScrollVertically(1)
            }
            if (ready) return
            SystemClock.sleep(200L)
        }
        throw AssertionError("The manual Teleprompter dialog did not appear after recording started")
    }

    private fun assertManualTeleprompterAndRtl(
        scenario: ActivityScenario<MainActivity>,
        expectedStory: String
    ) {
        var manualScrollY = 0
        scenario.onActivity {
            val script = findWindowView(WolfUiTestTags.TELEPROMPTER_SCRIPT) as? TextView
            assertNotNull("The complete approved story must be visible in the Teleprompter", script)
            assertEquals(expectedStory, script!!.text.toString())
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, script.textDirection)
            assertEquals(View.TEXT_ALIGNMENT_VIEW_START, script.textAlignment)
            assertEquals("The story must be laid out from the RTL start edge", Gravity.START, script.gravity)

            val scroll = findWindowView(WolfUiTestTags.TELEPROMPTER_SCROLL) as? ScrollView
            assertNotNull("The story must use a scrollable manual reading surface", scroll)
            assertTrue(scroll!!.isVerticalScrollBarEnabled)
            assertTrue("The long horror script must be vertically scrollable", scroll.canScrollVertically(1))
            scroll.scrollTo(0, 220)
            assertTrue("The user must be able to move the story manually", scroll.scrollY > 0)
            manualScrollY = scroll.scrollY

            val progress = findWindowView(WolfUiTestTags.TELEPROMPTER_PROGRESS) as? TextView
            assertNotNull(progress)
            assertTrue(progress!!.text.contains("التسجيل مستمر"))
        }

        // Wait away from the UI thread; recording progress callbacks must continue
        // while the manually chosen script position remains fixed.
        SystemClock.sleep(600L)
        scenario.onActivity {
            val scroll = findWindowView(WolfUiTestTags.TELEPROMPTER_SCROLL) as ScrollView
            assertEquals("The script must not auto-scroll while recording", manualScrollY, scroll.scrollY)
        }
    }

    private fun stopRecording(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            val stop = findWindowView(WolfUiTestTags.TELEPROMPTER_STOP) as? Button
            assertNotNull("The manual stop-and-save button must be visible", stop)
            stop!!.performClick()
        }
    }

    private fun awaitMainStatus(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 20_000L,
        predicate: (String) -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity {
                latest = (findWindowView(WolfUiTestTags.MAIN_STATUS) as? TextView)
                    ?.text?.toString().orEmpty()
            }
            if (predicate(latest)) return
            SystemClock.sleep(100L)
        }
        throw AssertionError("Timed out waiting for MainActivity status. Last status: $latest")
    }

    private fun awaitProject(context: android.content.Context, title: String): WolfProject {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val project = WolfProjectStore.list(context).firstOrNull { it.title == title }
            if (project != null) return project
            SystemClock.sleep(100L)
        }
        throw AssertionError("The recording project was not saved")
    }

    private fun findWindowView(tag: String): View? {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "Window views must be inspected on the Android main thread"
        }
        return WindowInspector.getGlobalWindowViews().asReversed()
            .firstNotNullOfOrNull { root -> findTaggedView(root, tag) }
    }

    private fun findTaggedView(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findTaggedView(root.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    private fun assertAacTrackHasIncreasingSamples(file: File) {
        assertTrue("The merged narration file must exist and be non-empty", file.isFile && file.length() > 1024L)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            }
            assertNotNull("The merged M4A must contain an AAC track", audioTrack)
            extractor.selectTrack(audioTrack!!)
            var samples = 0
            var previousPtsUs = -1L
            while (extractor.sampleTime >= 0L) {
                val ptsUs = extractor.sampleTime
                assertTrue("AAC sample timestamps must increase", ptsUs > previousPtsUs)
                previousPtsUs = ptsUs
                samples++
                if (!extractor.advance()) break
            }
            assertTrue("The merged AAC track must contain encoded samples", samples > 1)
        } finally {
            extractor.release()
        }
    }
}
