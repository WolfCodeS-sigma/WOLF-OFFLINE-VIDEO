package com.wolfcodes.offline

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HorrorFilamentUiInstrumentedTest {

    @Test(timeout = 600_000L)
    fun debugUiExportsAndPublishesAReadableFiveSecondFilamentMp4() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchedActivity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val activity = launchedActivity as? MainActivity
            ?: throw AssertionError(
                "تم تشغيل نشاط غير متوقع بدلاً من MainActivity"
            )
        var outputUri: Uri? = null

        try {
            val exportButton = activity.findViewById<Button>(
                MainActivity.FILAMENT_EXPORT_BUTTON_ID
            )
            assertNotNull("زر اختبار Filament غير موجود في واجهة Debug", exportButton)

            activity.filamentPrototypeResultUri = null
            activity.filamentPrototypeReport = null
            activity.filamentPrototypeError = null

            instrumentation.runOnMainSync {
                requireNotNull(exportButton).performClick()
            }

            val deadline = SystemClock.elapsedRealtime() + 540_000L
            while (
                activity.filamentPrototypeResultUri == null &&
                activity.filamentPrototypeError == null &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(250L)
            }

            val error = activity.filamentPrototypeError
            assertTrue(
                "تصدير Filament فشل: " + (error ?: "انتهت مهلة الاختبار"),
                error == null
            )

            outputUri = activity.filamentPrototypeResultUri
            assertNotNull("واجهة التطبيق لم تحفظ فيديو Filament في المعرض", outputUri)

            val report = activity.filamentPrototypeReport
            assertNotNull("لم تُرفق نتيجة فحص MP4 بعد التصدير", report)
            val quality = requireNotNull(report)
            assertTrue("فشل فحص MP4: " + quality.summary, quality.passed)
            assertEquals(1280, quality.videoWidth)
            assertEquals(720, quality.videoHeight)
            assertTrue(
                "عدد إطارات Filament أقل من 148 إطارًا: " + quality.videoSamples,
                quality.videoSamples >= 148L
            )
            val duration = requireNotNull(quality.durationSeconds)
            assertTrue(
                "مدة الفيديو ليست خمس ثوانٍ: " + duration,
                duration in 4.8..5.2
            )

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, requireNotNull(outputUri))
                val frame = retriever.getFrameAtTime(
                    2_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                assertNotNull("تعذر فك إطار من فيديو Filament النهائي", frame)
                frame?.recycle()
            } finally {
                retriever.release()
            }
        } finally {
            outputUri?.let { uri ->
                if (uri.scheme == "content") {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
            instrumentation.runOnMainSync {
                activity.finish()
            }
        }
    }
}
