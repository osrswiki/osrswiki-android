package com.omiyawaki.osrswiki.page.preemptive

import android.graphics.Rect
import android.view.View
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class VisibleRowDwellTracker<K>(
    private val scope: CoroutineScope,
    private val dwellMillis: Long,
    private val onDwell: (K) -> ArticlePrewarmLease
) {
    private data class Entry(
        var dwellJob: Job? = null,
        var lease: ArticlePrewarmLease? = null
    )

    private val entries = LinkedHashMap<K, Entry>()

    init {
        require(dwellMillis >= 0L)
    }

    fun updateVisible(visibleKeys: Set<K>) {
        (entries.keys - visibleKeys).forEach(::remove)
        (visibleKeys - entries.keys).forEach { key ->
            val entry = Entry()
            entries[key] = entry
            entry.dwellJob = scope.launch {
                delay(dwellMillis)
                if (entries[key] === entry) {
                    entry.lease = onDwell(key)
                }
            }
        }
    }

    fun clear() {
        entries.keys.toList().forEach(::remove)
    }

    fun retryVisible() {
        val visibleKeys = entries.keys.toSet()
        clear()
        updateVisible(visibleKeys)
    }

    private fun remove(key: K) {
        val entry = entries.remove(key) ?: return
        entry.dwellJob?.cancel()
        entry.lease?.cancel()
    }
}

internal object VisibleRowViewportPolicy {
    fun intersectsViewport(
        isShown: Boolean,
        viewportLeft: Int,
        viewportTop: Int,
        viewportRight: Int,
        viewportBottom: Int,
        childLeft: Int,
        childTop: Int,
        childRight: Int,
        childBottom: Int
    ): Boolean = isShown &&
        viewportRight > viewportLeft && viewportBottom > viewportTop &&
        childRight > childLeft && childBottom > childTop &&
        childLeft < viewportRight && childRight > viewportLeft &&
        childTop < viewportBottom && childBottom > viewportTop
}

/**
 * Starts text-only prewarm after an article row remains actually attached for a short dwell, and
 * releases speculative ownership as soon as the row leaves the viewport or lifecycle.
 */
internal class VisibleArticlePrewarmBinder(
    private val recyclerView: RecyclerView,
    lifecycleOwner: LifecycleOwner,
    scope: CoroutineScope,
    private val candidatesAt: (adapterPosition: Int, rowView: View) -> Set<ArticlePrewarmRequest>,
    onDwell: (ArticlePrewarmRequest) -> ArticlePrewarmLease,
    observeEnvironmentChanges: (((() -> Unit) -> ArticlePrewarmEnvironmentSubscription))? = null,
    dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    private val additionalCandidates: () -> Set<ArticlePrewarmRequest> = { emptySet() }
) : DefaultLifecycleObserver {
    private val lifecycle = lifecycleOwner.lifecycle
    private val hostedOnDwell: (ArticlePrewarmRequest) -> ArticlePrewarmLease = { request ->
        com.omiyawaki.osrswiki.page.osrsPreparedArticleWebViewStore.rememberHost(recyclerView.context)
        onDwell(request)
    }
    private val dwellTracker = VisibleRowDwellTracker(scope, dwellMillis, hostedOnDwell)
    private var started = false
    private var observedAdapter: RecyclerView.Adapter<*>? = null
    private val environmentSubscription = observeEnvironmentChanges?.invoke {
        recyclerView.post {
            if (started) {
                dwellTracker.retryVisible()
                refresh()
            }
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = refresh()
    }
    private val childAttachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) = refreshPosted()
        override fun onChildViewDetachedFromWindow(view: View) = refreshPosted()
    }
    private val adapterObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = refreshPosted()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = refreshPosted()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = refreshPosted()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = refreshPosted()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = refreshPosted()
    }

    init {
        lifecycle.addObserver(this)
        recyclerView.addOnScrollListener(scrollListener)
        recyclerView.addOnChildAttachStateChangeListener(childAttachListener)
        syncAdapterObserver()
    }

    override fun onStart(owner: LifecycleOwner) {
        started = true
        refreshPosted()
    }

    override fun onStop(owner: LifecycleOwner) {
        started = false
        dwellTracker.clear()
    }

    fun refresh() {
        syncAdapterObserver()
        if (!started) {
            dwellTracker.clear()
            return
        }
        val viewport = Rect()
        if (!recyclerView.getGlobalVisibleRect(viewport)) {
            dwellTracker.clear()
            return
        }
        val visible = recyclerView.children.flatMap { child ->
            val childBounds = Rect()
            if (!child.getGlobalVisibleRect(childBounds) || !VisibleRowViewportPolicy.intersectsViewport(
                    isShown = child.isShown,
                    viewportLeft = viewport.left,
                    viewportTop = viewport.top,
                    viewportRight = viewport.right,
                    viewportBottom = viewport.bottom,
                    childLeft = childBounds.left,
                    childTop = childBounds.top,
                    childRight = childBounds.right,
                    childBottom = childBounds.bottom
                )
            ) {
                return@flatMap emptyList()
            }
            val position = recyclerView.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) emptyList() else candidatesAt(position, child)
        }.toSet()
        dwellTracker.updateVisible(visible + additionalCandidates())
    }

    fun clear() = dwellTracker.clear()

    fun dispose() {
        lifecycle.removeObserver(this)
        recyclerView.removeOnScrollListener(scrollListener)
        recyclerView.removeOnChildAttachStateChangeListener(childAttachListener)
        observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
        observedAdapter = null
        environmentSubscription?.dispose()
        dwellTracker.clear()
    }

    private fun refreshPosted() {
        recyclerView.post(::refresh)
    }

    private fun syncAdapterObserver() {
        val current = recyclerView.adapter
        if (current === observedAdapter) return
        observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
        observedAdapter = current
        current?.registerAdapterDataObserver(adapterObserver)
    }

    companion object {
        const val DEFAULT_DWELL_MILLIS = 300L
    }
}
