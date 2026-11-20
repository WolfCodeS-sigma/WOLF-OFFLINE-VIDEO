package com.wolfcodes.offline

/** Phase 14 orchestration contract for offline film production. */
object WolfFilmProductionCoordinator {
    const val MAX_SCENES = 12
    const val SHOTS_PER_SCENE = 3
    const val DEFAULT_CHAPTER_SECONDS = 300
    const val PLAN_VERSION = "shot-chapters-v1"

    val durationPresetsMinutes: List<Int> = listOf(5, 10, 15, 20, 25, 30)

    data class Plan(
        val framesPerSecond: Int,
        val totalFrames: Int,
        val sceneCount: Int,
        val shotCount: Int,
        val chapters: List<WolfLongFilmEngine.ChapterPlan>
    ) {
        val chapterCount: Int get() = chapters.size
        val totalDurationSeconds: Int
            get() = totalFrames / framesPerSecond.coerceAtLeast(1)
    }

    fun createPlan(
        framesPerScene: List<Int>,
        framesPerSecond: Int,
        introFrames: Int,
        chapterSeconds: Int = DEFAULT_CHAPTER_SECONDS
    ): Plan {
        require(framesPerScene.isNotEmpty()) { "لا توجد مشاهد في خطة الفيلم" }
        require(framesPerScene.size <= MAX_SCENES) { "تجاوزت خطة الفيلم الحد الأقصى البالغ 12 مشهدًا" }
        require(framesPerScene.all { it >= 0 } && framesPerScene.any { it > 0 }) {
            "ميزانية إطارات المشاهد غير صالحة"
        }
        require(framesPerSecond in 1..60) { "معدل الإطارات غير صالح" }
        require(chapterSeconds in 1..DEFAULT_CHAPTER_SECONDS) { "مدة فصل الإنتاج غير صالحة" }

        val chapters = WolfLongFilmEngine.planShotChapters(
            framesPerScene = framesPerScene,
            framesPerSecond = framesPerSecond,
            introFrames = introFrames,
            maxChapterSeconds = chapterSeconds
        )
        val expectedBodyFrames = framesPerScene.sum()
        val actualBodyFrames = chapters.sumOf { it.bodyFrameCount }
        val actualIntroFrames = chapters.sumOf { it.introFrameCount }
        require(actualBodyFrames == expectedBodyFrames) { "خطة الفصول أسقطت إطارات من الفيلم" }
        require(chapters.isNotEmpty() && chapters.map { it.number } == (1..chapters.size).toList()) {
            "ترتيب فصول الإنتاج غير صالح"
        }

        val chapterLimit = chapterSeconds * framesPerSecond
        chapters.forEachIndexed { index, chapter ->
            require(chapter.slices.isNotEmpty()) { "يوجد فصل إنتاج فارغ" }
            require(chapter.totalFrameCount <= chapterLimit) { "تجاوز أحد الفصول الحد الزمني الآمن" }
            if (index > 0) require(chapter.introFrameCount == 0) {
                "يجب أن تظهر المقدمة في بداية الفيلم فقط"
            }
            chapter.slices.forEach { slice ->
                require(slice.sceneIndex in framesPerScene.indices)
                require(slice.shotIndex in 0 until SHOTS_PER_SCENE)
                require(slice.frameCount > 0)
                require(slice.shotTotalFrames > 0)
                require(slice.shotStartFrame >= 0 &&
                    slice.shotStartFrame + slice.frameCount <= slice.shotTotalFrames)
                require(slice.startFrame >= 0 &&
                    slice.startFrame + slice.frameCount <= framesPerScene[slice.sceneIndex])
            }
        }
        require(actualIntroFrames in 0..introFrames.coerceAtLeast(0)) {
            "ميزانية المقدمة غير متسقة"
        }

        return Plan(
            framesPerSecond = framesPerSecond,
            totalFrames = expectedBodyFrames + actualIntroFrames,
            sceneCount = framesPerScene.size,
            shotCount = framesPerScene.size * SHOTS_PER_SCENE,
            chapters = chapters
        )
    }
}
