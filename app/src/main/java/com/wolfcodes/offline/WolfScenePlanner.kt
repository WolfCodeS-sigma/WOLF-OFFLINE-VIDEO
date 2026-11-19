package com.wolfcodes.offline

object WolfScenePlanner {
    fun split(story: String): List<String> {
        val normalized = story.trim().replace("\r\n", "\n")
        require(normalized.isNotBlank()) { "القصة فارغة" }
        val paragraphs = normalized.split(Regex("\\n\\s*\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val units = if (paragraphs.size > 1) paragraphs else
            normalized.split(Regex("(?<=[.!؟?؛])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = units.flatMap { part ->
            if (part.length <= 520) listOf(part) else part.chunked(420)
        }
        if (chunks.size <= 12) return chunks.ifEmpty { listOf(normalized) }
        return chunks.take(11) + chunks.drop(11).joinToString("\n")
    }

    fun allocateFrames(scenes: List<String>, totalFrames: Int): List<Int> {
        require(scenes.isNotEmpty())
        require(totalFrames >= scenes.size) { "عدد الإطارات لا يكفي لتمثيل كل المشاهد" }
        val weights = scenes.map { 1.0 + it.length.coerceAtMost(500) / 500.0 }
        val sum = weights.sum()
        val exact = weights.map { totalFrames * it / sum }
        val result = exact.map { it.toInt() }.toMutableList()
        var remainder = totalFrames - result.sum()
        exact.indices.sortedByDescending { exact[it] - result[it] }.forEach { index ->
            if (remainder > 0) {
                result[index] += 1
                remainder -= 1
            }
        }
        check(result.sum() == totalFrames && result.all { it > 0 })
        return result
    }
}
