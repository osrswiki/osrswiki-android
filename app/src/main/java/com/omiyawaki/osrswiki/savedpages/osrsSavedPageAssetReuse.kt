package com.omiyawaki.osrswiki.savedpages

/**
 * URL-keyed reuse across snapshot generations. Wiki media URLs are cache-busted, so an
 * unchanged URL can keep its prior bytes without a GET.
 */
object osrsSavedPageAssetReuse {
    data class Partition(
        val reusedUrls: List<String>,
        val fetchUrls: List<String>
    )

    fun partition(requiredUrls: Collection<String>, priorUrls: Set<String>): Partition {
        val reused = ArrayList<String>()
        val fetch = ArrayList<String>()
        val seen = LinkedHashSet<String>()
        requiredUrls.forEach { url ->
            if (!seen.add(url)) {
                return@forEach
            }
            if (url in priorUrls) {
                reused += url
            } else {
                fetch += url
            }
        }
        return Partition(reused, fetch)
    }

    data class Counts(val reused: Int, val fetched: Int)
}
