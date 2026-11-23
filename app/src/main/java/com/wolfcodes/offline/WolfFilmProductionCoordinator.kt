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

    /** Keeps the requested film duration inclusive of the optional opening intro. */
    fun bodyDurationSeconds(
        requestedDurationSeconds: Int,
        introSeconds: Int,
        sceneCount: Int
    ): Int {
        require(requestedDurationSeconds in 1..1_800)
        require(introSeconds in 0..3)
        require(sceneCount in 1..MAX_SCENES)
        val minimumBody = sceneCount * 3
        val maximumBody = (1_800 - introSeconds).coerceAtLeast(minimumBody)
        val requestedBody = (requestedDurationSeconds - introSeconds).coerceAtLeast(1)
        return requestedBody.coerceAtLeast(minimumBody).coerceAtMost(maximumBody)
    }

    fun createPlan(
        framesPerScene: List<Int>,
        framesPerSecond: Int,
        introFrames: Int,
        chapterSeconds: Int = DEFAULT_CHAPTER_SECONDS,
        shotFramesPerScene: List<List<Int>>? = null
    ): Plan {
        require(framesPerScene.isNotEmpty()) { "لا توجد مشاهد في خطة الفيلم" }
        require(framesPerScene.size <= MAX_SCENES) { "تجاوزت خطة الفيلم الحد الأقصى البالغ 12 مشهدًا" }
        require(framesPerScene.all { it >= SHOTS_PER_SCENE }) {
            "يجب توفير إطارات كافية للقطات الثلاث في كل مشهد"
        }
        require(framesPerSecond in 1..60) { "معدل الإطارات غير صالح" }
        require(introFrames >= 0) { "عدد إطارات المقدمة غير صالح" }
        require(chapterSeconds in 1..DEFAULT_CHAPTER_SECONDS) { "مدة فصل الإنتاج غير صالحة" }
        if (shotFramesPerScene != null) {
            require(shotFramesPerScene.size == framesPerScene.size) {
                "عدد ميزانيات اللقطات لا يطابق عدد المشاهد"
            }
            shotFramesPerScene.forEachIndexed { index, budgets ->
                require(budgets.size == SHOTS_PER_SCENE && budgets.all { it > 0 } &&
                    budgets.sum() == framesPerScene[index]) {
                    "ميزانية إطارات اللقطات لا تساوي ميزانية المشهد"
                }
            }
        }

        val chapters = WolfLongFilmEngine.planShotChapters(
            framesPerScene = framesPerScene,
            framesPerSecond = framesPerSecond,
            introFrames = introFrames,
            maxChapterSeconds = chapterSeconds,
            shotFramesPerScene = shotFramesPerScene
        )
        val expectedBodyFramesLong = framesPerScene.sumOf { it.toLong() }
        require(expectedBodyFramesLong + introFrames.toLong() <= 1_800L * framesPerSecond) {
            "تجاوزت مدة الفيلم الحد الأقصى البالغ 30 دقيقة"
        }
        val expectedBodyFrames = expectedBodyFramesLong.toInt()
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

        val orderedSlices = chapters.flatMap { it.slices }
        val expectedOrder = orderedSlices.sortedWith(
            compareBy<WolfLongFilmEngine.SceneSlice> { it.sceneIndex }
                .thenBy { it.shotIndex }
                .thenBy { it.shotStartFrame }
        )
        require(orderedSlices == expectedOrder) { "ترتيب المشاهد واللقطات داخل الفصول غير متسق" }
        framesPerScene.forEachIndexed { sceneIndex, sceneFrames ->
            val expectedShotFrames = shotFramesPerScene?.getOrNull(sceneIndex) ?: run {
                val base = sceneFrames / SHOTS_PER_SCENE
                val extra = sceneFrames % SHOTS_PER_SCENE
                (0 until SHOTS_PER_SCENE).map { shotIndex ->
                    base + if (shotIndex < extra) 1 else 0
                }
            }
            var sceneOffset = 0
            for (shotIndex in 0 until SHOTS_PER_SCENE) {
                val shotFrames = expectedShotFrames[shotIndex]
                val segments = orderedSlices.filter {
                    it.sceneIndex == sceneIndex && it.shotIndex == shotIndex
                }
                require(segments.isNotEmpty()) { "لقطة مفقودة من خطة الفيلم" }
                require(segments.sumOf { it.frameCount } == shotFrames) {
                    "ميزانية إطارات اللقطة غير مكتملة"
                }
                require(segments.first().shotStartFrame == 0 &&
                    segments.zipWithNext().all { (a, b) ->
                        a.shotStartFrame + a.frameCount == b.shotStartFrame
                    }) { "نقطة استئناف اللقطة غير متصلة" }
                require(segments.all {
                    it.shotTotalFrames == shotFrames &&
                        it.startFrame == sceneOffset + it.shotStartFrame
                }) { "بيانات حركة اللقطة لا تطابق موضعها في المشهد" }
                sceneOffset += shotFrames
            }
        }
        require(actualIntroFrames == introFrames) {
            "لم تحتفظ خطة الفصول بجميع إطارات المقدمة المطلوبة"
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
