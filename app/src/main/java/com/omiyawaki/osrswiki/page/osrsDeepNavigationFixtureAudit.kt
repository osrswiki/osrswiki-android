package com.omiyawaki.osrswiki.page

data class osrsDeepNavigationFixtureAuditResult(
    val status: String,
    val seed: Int,
    val startOffset: Int,
    val startCount: Int,
    val targetDepth: Int,
    val completedStarts: Int,
    val forwardTransitions: Int,
    val backTransitions: Int,
    val mismatchCount: Int,
    val firstMismatch: String?,
    val elapsedMilliseconds: Long,
    val finalActiveTitle: String?,
    val finalActiveUrl: String?
) {
    val passed: Boolean
        get() = status == "pass" &&
            completedStarts == startCount &&
            forwardTransitions == startCount * targetDepth &&
            backTransitions == startCount * targetDepth &&
            mismatchCount == 0

    val accessibilityLabel: String
        get() = listOf(
            "status=$status",
            "seed=$seed",
            "startOffset=$startOffset",
            "startCount=$startCount",
            "targetDepth=$targetDepth",
            "completedStarts=$completedStarts",
            "forwardTransitions=$forwardTransitions",
            "backTransitions=$backTransitions",
            "mismatches=$mismatchCount",
            "elapsedMs=$elapsedMilliseconds",
            "finalActiveTitle=${finalActiveTitle ?: "nil"}",
            "finalActive=${finalActiveUrl ?: "nil"}",
            "firstMismatch=${firstMismatch ?: "nil"}"
        ).joinToString(separator = ";")
}

object osrsDeepNavigationFixtureAudit {
    const val DEFAULT_SEED = 20260709
    const val DEFAULT_START_OFFSET = 0
    const val DEFAULT_START_COUNT = 10_000
    const val DEFAULT_DEPTH = 100

    fun sampleOrdinal(seed: Int, sequence: Int): Int {
        return seed + sequence
    }

    fun articlePath(sampleOrdinal: Int, depth: Int): String {
        return "osrsDeepNavigationFixture/$sampleOrdinal/$depth"
    }

    fun articleTitle(sampleOrdinal: Int, depth: Int): String {
        return "osrs Deep Navigation Fixture $sampleOrdinal Layer $depth"
    }

    fun articleUrl(sampleOrdinal: Int, depth: Int): String {
        return "https://oldschool.runescape.wiki/w/${articlePath(sampleOrdinal, depth)}"
    }
}
