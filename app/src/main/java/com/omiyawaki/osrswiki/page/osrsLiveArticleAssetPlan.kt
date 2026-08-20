package com.omiyawaki.osrswiki.page

internal data class osrsLiveArticleAssetPlan(
    val high: List<String>,
    val low: List<String>
) {
    companion object {
        const val FIRST_VIEW_CAP = 48

        fun partition(
            requiredUrls: List<String>,
            firstViewUrls: List<String> = emptyList(),
            firstViewCap: Int = FIRST_VIEW_CAP
        ): osrsLiveArticleAssetPlan {
            val required = requiredUrls.distinct()
            val requiredSet = required.toHashSet()
            val cap = firstViewCap.coerceAtLeast(0)
            val high = LinkedHashSet<String>()
            for (url in firstViewUrls.distinct()) {
                if (high.size >= cap) {
                    break
                }
                if (url in requiredSet) {
                    high.add(url)
                }
            }
            val cappedHigh = high.toList()
            val highSet = cappedHigh.toHashSet()
            val low = required.filter { it !in highSet }
            return osrsLiveArticleAssetPlan(cappedHigh, low)
        }
    }
}
