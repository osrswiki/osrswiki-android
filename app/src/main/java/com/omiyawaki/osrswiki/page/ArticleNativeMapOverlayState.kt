package com.omiyawaki.osrswiki.page

/** Latest document-space bounds reported for one article map placeholder. */
internal data class ArticleNativeMapBounds(
    val top: Float,
    val start: Float,
    val width: Float,
    val height: Float
)

/**
 * Keeps article-map geometry and desired visibility independent from native View creation.
 *
 * Collapsible callbacks can arrive before a newly opened map has a measurable DOM rectangle.
 * Conversely, font/image/layout changes can remeasure a map after its native overlay exists.
 * Storing both facts by stable placeholder id lets either ordering converge on the same state.
 */
internal class ArticleNativeMapOverlayState {
    data class Record(
        val bounds: ArticleNativeMapBounds? = null,
        val desiredVisible: Boolean? = null
    )

    private val records = linkedMapOf<String, Record>()

    fun recordMeasurement(
        id: String,
        bounds: ArticleNativeMapBounds,
        initiallyVisible: Boolean
    ): Record {
        val current = records[id]
        return Record(
            bounds = bounds,
            desiredVisible = current?.desiredVisible ?: initiallyVisible
        ).also { records[id] = it }
    }

    fun recordDesiredVisibility(id: String, visible: Boolean): Record {
        val current = records[id] ?: Record()
        return current.copy(desiredVisible = visible).also { records[id] = it }
    }

    fun record(id: String): Record? = records[id]

    fun remove(id: String) {
        records.remove(id)
    }

    fun clear() {
        records.clear()
    }
}
