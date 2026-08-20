package com.omiyawaki.osrswiki.page

internal data class osrsLiveArticleAssetPlan(
    val high: List<String>,
    val low: List<String>
) {
    companion object {
        const val FIRST_SCREEN_LIMIT = 24

        fun partition(
            requiredUrls: List<String>,
            infoboxUrls: List<String> = emptyList(),
            firstScreenLimit: Int = FIRST_SCREEN_LIMIT
        ): osrsLiveArticleAssetPlan {
            val required = requiredUrls.distinct()
            val requiredSet = required.toHashSet()
            val high = LinkedHashSet<String>()
            infoboxUrls.forEach { url ->
                if (url in requiredSet) {
                    high.add(url)
                }
            }
            required.take(firstScreenLimit.coerceAtLeast(0)).forEach(high::add)
            val low = required.filter { it !in high }
            return osrsLiveArticleAssetPlan(high.toList(), low)
        }
    }
}
