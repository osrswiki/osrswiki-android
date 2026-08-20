package com.omiyawaki.osrswiki.page

internal class osrsLiveArticleAssetQueue(
    private val isCached: (String) -> Boolean = { false }
) {
    private val high = ArrayDeque<String>()
    private val low = ArrayDeque<String>()
    private val queued = HashSet<String>()
    private val inFlight = HashSet<String>()
    private val done = HashSet<String>()

    @Synchronized
    fun load(highUrls: List<String>, lowUrls: List<String>) {
        high.clear()
        low.clear()
        queued.clear()
        inFlight.clear()
        done.clear()
        highUrls.forEach { enqueue(high, it) }
        lowUrls.forEach { enqueue(low, it) }
    }

    @Synchronized
    fun promote(urls: Collection<String>) {
        urls.forEach { url ->
            if (url.isBlank() || url in done || url in inFlight) {
                return@forEach
            }
            if (low.remove(url)) {
                queued.remove(url)
                enqueueFront(high, url)
                return@forEach
            }
            if (high.remove(url)) {
                queued.remove(url)
                enqueueFront(high, url)
            }
        }
    }

    @Synchronized
    fun takeHigh(): String? = takeFrom(high)

    @Synchronized
    fun takeLow(): String? = takeFrom(low)

    @Synchronized
    fun complete(url: String) {
        inFlight.remove(url)
        done.add(url)
    }

    @Synchronized
    fun cancel() {
        high.clear()
        low.clear()
        queued.clear()
        inFlight.clear()
    }

    @get:Synchronized
    val isIdle: Boolean
        get() = high.isEmpty() && low.isEmpty() && inFlight.isEmpty()

    private fun enqueue(deque: ArrayDeque<String>, url: String) {
        if (url.isBlank() || url in queued || url in done || url in inFlight) {
            return
        }
        queued.add(url)
        deque.addLast(url)
    }

    private fun enqueueFront(deque: ArrayDeque<String>, url: String) {
        if (url.isBlank() || url in done || url in inFlight) {
            return
        }
        queued.add(url)
        deque.addFirst(url)
    }

    private fun takeFrom(deque: ArrayDeque<String>): String? {
        while (true) {
            val url = deque.removeFirstOrNull() ?: return null
            queued.remove(url)
            if (url in done || url in inFlight) {
                continue
            }
            if (isCached(url)) {
                done.add(url)
                continue
            }
            inFlight.add(url)
            return url
        }
    }
}
