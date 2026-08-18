package com.omiyawaki.osrswiki.page.preemptive

import com.omiyawaki.osrswiki.page.DownloadResult
import com.omiyawaki.osrswiki.util.log.L
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** A stable article identity. An authoritative MediaWiki id wins over a display-title alias. */
internal data class ArticlePreparationKey(
    val pageId: Int?,
    val normalizedTitle: String?
) {
    fun matches(other: ArticlePreparationKey): Boolean {
        if (pageId != null && other.pageId != null) {
            return pageId == other.pageId
        }
        return normalizedTitle != null &&
            other.normalizedTitle != null &&
            normalizedTitle == other.normalizedTitle
    }

    fun logValue(): String = pageId?.let { "id:$it" } ?: "title:${normalizedTitle.orEmpty()}"
}

/** Theme-independent request used by list dwell prewarming and foreground page loads. */
data class ArticlePrewarmRequest(
    val pageId: Int? = null,
    val title: String? = null
) {
    internal val key = ArticlePreparationKey(
        pageId = pageId?.takeIf { it > 0 },
        normalizedTitle = normalizeTitle(title)
    )

    init {
        require(key.pageId != null || key.normalizedTitle != null) {
            "An article prewarm request needs a positive page id or a non-blank title."
        }
    }

    companion object {
        fun fromWikiUrl(url: String?, fallbackTitle: String? = null): ArticlePrewarmRequest? {
            val decodedTitle = url?.let(::titleFromWikiUrl)
            val title = decodedTitle?.takeIf { it.isNotBlank() }
                ?: fallbackTitle?.takeIf { it.isNotBlank() }
                ?: return null
            return ArticlePrewarmRequest(title = title)
        }

        private fun titleFromWikiUrl(url: String): String? {
            val withoutFragment = url.substringBefore('#')
            val encodedTitle = when {
                "/w/" in withoutFragment -> withoutFragment.substringAfter("/w/").substringBefore('?')
                "title=" in withoutFragment -> withoutFragment.substringAfter("title=").substringBefore('&')
                else -> null
            } ?: return null
            return runCatching {
                URLDecoder.decode(encodedTitle.replace('_', ' '), Charsets.UTF_8.name())
            }.getOrNull()
        }

        private fun normalizeTitle(title: String?): String? = title
            ?.trim()
            ?.replace('_', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotEmpty() }
            // MediaWiki commonly canonicalizes the first character, but remaining case and all
            // diacritics are meaningful article identity and must never be folded together.
            ?.replaceFirstChar { first -> first.uppercaseChar() }
    }
}

internal data class PreparedArticleLookup(
    val value: DownloadResult,
    val ageMillis: Long
)

/**
 * Small synchronized access-order cache. Entries are keyed by both requested and canonical
 * article identity, expire by monotonic time, and retain only theme-independent prepared HTML.
 */
internal class PreparedArticleCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Entry(
        val keys: Set<ArticlePreparationKey>,
        val value: DownloadResult,
        val storedAtMillis: Long,
        val generation: Long
    )

    private val nextEntryId = AtomicLong(0L)
    private val nextWriteGeneration = AtomicLong(0L)
    private val entries = LinkedHashMap<Long, Entry>(maxEntries, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(ttlMillis > 0)
    }

    @Synchronized
    fun get(request: ArticlePrewarmRequest): PreparedArticleLookup? {
        val now = clockMillis()
        removeExpiredLocked(now)
        val matchingId = entries.entries.firstOrNull { cacheEntry ->
            cacheEntry.value.keys.any { it.matches(request.key) }
        }?.key
            ?: return null
        val entry = entries[matchingId] ?: return null // access updates LRU order
        return PreparedArticleLookup(entry.value, (now - entry.storedAtMillis).coerceAtLeast(0L))
    }

    fun put(request: ArticlePrewarmRequest, value: DownloadResult): Boolean =
        put(request, value, reserveGeneration())

    @Synchronized
    fun put(
        request: ArticlePrewarmRequest,
        value: DownloadResult,
        generation: Long
    ): Boolean {
        require(generation > 0L)
        val now = clockMillis()
        removeExpiredLocked(now)
        val canonicalKey = ArticlePreparationKey(
            pageId = value.parseResult.pageid.takeIf { it > 0 } ?: request.key.pageId,
            normalizedTitle = ArticlePrewarmRequest(
                pageId = value.parseResult.pageid.takeIf { it > 0 },
                title = value.parseResult.title.ifBlank { request.title }
            ).key.normalizedTitle ?: request.key.normalizedTitle
        )
        val newIdentity = linkedSetOf(canonicalKey, request.key)
        val replacedEntries = entries.values.filter { cacheEntry ->
            cacheEntry.keys.any { stored -> newIdentity.any(stored::matches) }
        }
        // A force-refresh can start after a speculative read yet finish first. Completion order
        // must not let the older payload replace that newer successful generation. If the newer
        // operation fails it writes nothing, so an older success can still populate an empty cache.
        if (replacedEntries.any { it.generation > generation }) return false
        val aliases = linkedSetOf<ArticlePreparationKey>().apply {
            // Canonical and most-recent requested identities are never displaced by redirect
            // history. Retain a small bounded tail so alias1 -> canonical followed by alias2 ->
            // canonical does not make alias1 miss again.
            addAll(newIdentity)
            replacedEntries.asReversed().forEach { addAll(it.keys) }
        }.take(MAX_KEYS_PER_ENTRY).toSet()
        entries.entries.removeAll { it.value in replacedEntries }
        entries[nextEntryId.incrementAndGet()] = Entry(aliases, value, now, generation)
        while (entries.size > maxEntries) {
            val eldestId = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldestId)
        }
        return true
    }

    internal fun reserveGeneration(): Long = nextWriteGeneration.incrementAndGet()

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun invalidate(request: ArticlePrewarmRequest) {
        entries.entries.removeAll { cacheEntry ->
            cacheEntry.value.keys.any { it.matches(request.key) }
        }
    }

    @Synchronized
    internal fun sizeForTests(): Int {
        removeExpiredLocked(clockMillis())
        return entries.size
    }

    private fun removeExpiredLocked(now: Long) {
        entries.entries.removeAll { now - it.value.storedAtMillis >= ttlMillis }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 12
        const val DEFAULT_TTL_MILLIS = 5 * 60 * 1_000L
        internal const val MAX_KEYS_PER_ENTRY = 8
    }
}

internal enum class ArticlePrewarmSuppression {
    NONE,
    APP_BACKGROUND,
    NETWORK_UNAVAILABLE,
    POWER_SAVE,
    THERMAL,
    DEBUG_DISABLED
}

internal data class ArticlePrewarmSignals(
    val appInForeground: Boolean,
    val networkAvailable: Boolean,
    val networkConstrained: Boolean,
    val powerSave: Boolean,
    val thermallyConstrained: Boolean,
    val debugDisabled: Boolean = false
)

internal data class ArticlePrewarmDecision(
    val suppression: ArticlePrewarmSuppression,
    val maxConcurrent: Int
) {
    val allowsPrewarm: Boolean get() = suppression == ArticlePrewarmSuppression.NONE
}

internal object ArticlePrewarmPolicy {
    fun evaluate(signals: ArticlePrewarmSignals): ArticlePrewarmDecision {
        val suppression = when {
            signals.debugDisabled -> ArticlePrewarmSuppression.DEBUG_DISABLED
            !signals.appInForeground -> ArticlePrewarmSuppression.APP_BACKGROUND
            !signals.networkAvailable -> ArticlePrewarmSuppression.NETWORK_UNAVAILABLE
            signals.powerSave -> ArticlePrewarmSuppression.POWER_SAVE
            signals.thermallyConstrained -> ArticlePrewarmSuppression.THERMAL
            else -> ArticlePrewarmSuppression.NONE
        }
        return ArticlePrewarmDecision(
            suppression = suppression,
            maxConcurrent = if (signals.networkConstrained) 1 else 2
        )
    }
}

internal fun interface ArticlePrewarmEnvironmentProvider {
    fun currentDecision(): ArticlePrewarmDecision
}

internal fun interface ArticlePrewarmLease {
    fun cancel()
}

internal fun interface ArticlePrewarmEnvironmentSubscription {
    fun dispose()
}

private object EmptyArticlePrewarmLease : ArticlePrewarmLease {
    override fun cancel() = Unit
}

internal fun interface ArticlePrewarmEventSink {
    fun record(message: String)
}

/**
 * Process-owned priority scheduler for prepared article text. It coalesces matching foreground and
 * speculative callers, promotes queued speculative work when tapped, and never makes the shared
 * operation a child of an individual screen.
 */
internal class ArticlePreparationCoordinator(
    private val processScope: CoroutineScope,
    private val cache: PreparedArticleCache,
    private val environmentProvider: ArticlePrewarmEnvironmentProvider,
    private val prepare: suspend (
        request: ArticlePrewarmRequest,
        forceNetwork: Boolean,
        reportHtmlProgress: (Int) -> Unit
    ) -> DownloadResult,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val eventSink: ArticlePrewarmEventSink = ArticlePrewarmEventSink {
        L.d("ArticlePrewarmTiming: $it")
    }
) {
    private enum class Priority { PREWARM, FOREGROUND }
    private enum class State { QUEUED, RUNNING, CANCELING, FINISHED }

    private data class InFlight(
        val token: Long,
        val request: ArticlePrewarmRequest,
        val forceNetwork: Boolean,
        val cacheGeneration: Long,
        val queuedAtMillis: Long,
        val result: CompletableDeferred<DownloadResult> = CompletableDeferred(),
        val htmlProgress: MutableStateFlow<Int?> = MutableStateFlow(null),
        var priority: Priority,
        var state: State = State.QUEUED,
        var prewarmInterests: Int = 0,
        var foregroundWaiters: Int = 0,
        var startedAtMillis: Long? = null,
        var job: Job? = null,
        var cancelingSpeculation: Boolean = false,
        var usesForegroundOverlap: Boolean = false
    )

    private val lock = Any()
    private val nextToken = AtomicLong(0L)
    private val entries = LinkedHashMap<Long, InFlight>()
    private val queue = mutableListOf<InFlight>()
    private var activeCount = 0
    private var cancelingSpeculationCount = 0
    private var foregroundOverlapInUse = false

    fun peekPrepared(request: ArticlePrewarmRequest): DownloadResult? =
        cache.get(request)?.value

    fun requestPrewarm(request: ArticlePrewarmRequest): ArticlePrewarmLease {
        cache.get(request)?.let { cached ->
            eventSink.record("cache_hit kind=prewarm key=${request.key.logValue()} ageMs=${cached.ageMillis}")
            return EmptyArticlePrewarmLease
        }
        val decision = environmentProvider.currentDecision()
        if (!decision.allowsPrewarm) {
            eventSink.record("suppressed key=${request.key.logValue()} reason=${decision.suppression}")
            return EmptyArticlePrewarmLease
        }

        val entry = synchronized(lock) {
            val existing = findMatchingLocked(request, forceNetwork = false)
            if (existing != null) {
                existing.prewarmInterests += 1
                eventSink.record("coalesced kind=prewarm key=${request.key.logValue()} state=${existing.state}")
                existing
            } else {
                InFlight(
                    token = nextToken.incrementAndGet(),
                    request = request,
                    forceNetwork = false,
                    cacheGeneration = cache.reserveGeneration(),
                    queuedAtMillis = clockMillis(),
                    priority = Priority.PREWARM,
                    prewarmInterests = 1
                ).also {
                    entries[it.token] = it
                    queue += it
                    eventSink.record("queued kind=prewarm key=${request.key.logValue()}")
                }
            }.also { drainLocked() }
        }
        val canceled = AtomicBoolean(false)
        return ArticlePrewarmLease {
            if (canceled.compareAndSet(false, true)) {
                cancelPrewarmInterest(entry.token)
            }
        }
    }

    suspend fun awaitForeground(
        request: ArticlePrewarmRequest,
        forceNetwork: Boolean,
        onHtmlProgress: suspend (Int) -> Unit
    ): DownloadResult {
        if (!forceNetwork) {
            cache.get(request)?.let { cached ->
                eventSink.record("cache_hit kind=foreground key=${request.key.logValue()} ageMs=${cached.ageMillis}")
                return cached.value
            }
        }

        val entry = synchronized(lock) {
            val existing = findMatchingLocked(request, forceNetwork)
            if (existing != null) {
                existing.foregroundWaiters += 1
                if (existing.priority == Priority.PREWARM) {
                    existing.priority = Priority.FOREGROUND
                    eventSink.record("promoted key=${request.key.logValue()} state=${existing.state}")
                } else {
                    eventSink.record("coalesced kind=foreground key=${request.key.logValue()} state=${existing.state}")
                }
                existing
            } else {
                InFlight(
                    token = nextToken.incrementAndGet(),
                    request = request,
                    forceNetwork = forceNetwork,
                    cacheGeneration = cache.reserveGeneration(),
                    queuedAtMillis = clockMillis(),
                    priority = Priority.FOREGROUND,
                    foregroundWaiters = 1
                ).also {
                    entries[it.token] = it
                    queue += it
                    eventSink.record("queued kind=foreground key=${request.key.logValue()} force=$forceNetwork")
                }
            }.also { drainLocked() }
        }

        return coroutineScope {
            val progressJob = launch {
                entry.htmlProgress.filterNotNull().collect(onHtmlProgress)
            }
            try {
                entry.result.await()
            } finally {
                progressJob.cancel()
                synchronized(lock) {
                    entry.foregroundWaiters = (entry.foregroundWaiters - 1).coerceAtLeast(0)
                    when {
                        entry.foregroundWaiters > 0 -> Unit
                        entry.prewarmInterests > 0 -> {
                            entry.priority = Priority.PREWARM
                            if (!environmentProvider.currentDecision().allowsPrewarm) {
                                cancelEntryLocked(
                                    entry,
                                    CancellationException("Promoted prewarm lost foreground ownership while suppressed.")
                                )
                            }
                        }
                        else -> cancelEntryLocked(
                            entry,
                            CancellationException("Article preparation has no remaining consumers.")
                        )
                    }
                    drainLocked()
                }
            }
        }
    }

    internal fun clearCache() = cache.clear()

    fun invalidate(request: ArticlePrewarmRequest) {
        cache.invalidate(request)
        synchronized(lock) {
            entries.values.toList().forEach { entry ->
                if (entry.request.key.matches(request.key)) {
                    cancelEntryLocked(
                        entry,
                        CancellationException("Prepared article identity was invalidated.")
                    )
                }
            }
            drainLocked()
        }
    }

    /** Stops speculative-only work promptly when lifecycle, network, power, or thermal gates change. */
    fun environmentChanged() {
        synchronized(lock) {
            val decision = environmentProvider.currentDecision()
            if (!decision.allowsPrewarm) {
                entries.values.toList().forEach { entry ->
                    if (entry.foregroundWaiters == 0 && entry.prewarmInterests > 0) {
                        cancelEntryLocked(
                            entry,
                            CancellationException("Prewarm suppressed: ${decision.suppression}")
                        )
                    }
                }
            } else {
                cancelExcessSpeculationLocked(decision.maxConcurrent.coerceIn(1, 2))
            }
            drainLocked()
        }
    }

    private fun findMatchingLocked(
        request: ArticlePrewarmRequest,
        forceNetwork: Boolean
    ): InFlight? = entries.values.firstOrNull { entry ->
        (entry.state == State.QUEUED || entry.state == State.RUNNING) &&
            entry.request.key.matches(request.key) &&
            (!forceNetwork || entry.forceNetwork)
    }

    private fun cancelPrewarmInterest(token: Long) {
        synchronized(lock) {
            val entry = entries[token] ?: return
            entry.prewarmInterests = (entry.prewarmInterests - 1).coerceAtLeast(0)
            if (entry.prewarmInterests > 0 || entry.foregroundWaiters > 0) {
                return
            }
            eventSink.record("canceled kind=prewarm key=${entry.request.key.logValue()} state=${entry.state}")
            cancelEntryLocked(
                entry,
                CancellationException("Visible row left during prewarm.")
            )
            drainLocked()
        }
    }

    private fun cancelEntryLocked(
        entry: InFlight,
        cause: CancellationException
    ) {
        when (entry.state) {
            State.QUEUED -> {
                queue.remove(entry)
                entries.remove(entry.token)
                entry.state = State.FINISHED
                entry.result.cancel(cause)
            }
            State.RUNNING -> {
                // Exclude canceled work from identity lookup immediately. Its active slot remains
                // occupied until invokeOnCompletion reaches finishEntry, while an immediate tap can
                // enqueue a fresh Deferred instead of joining an operation already being canceled.
                entries.remove(entry.token)
                entry.state = State.CANCELING
                // Any speculative cancellation can be temporarily non-cooperative, regardless of
                // whether it came from row detach, lifecycle/network suppression, downshift, or
                // foreground preemption. Track the physical lane by ownership, not by cause.
                entry.cancelingSpeculation =
                    entry.foregroundWaiters == 0 && entry.priority == Priority.PREWARM
                if (entry.cancelingSpeculation) {
                    cancelingSpeculationCount += 1
                }
                entry.result.cancel(cause)
                entry.job?.cancel(cause)
            }
            State.CANCELING -> Unit
            State.FINISHED -> Unit
        }
    }

    private fun cancelExcessSpeculationLocked(concurrencyLimit: Int) {
        val runningForeground = entries.values.count { entry ->
            entry.state == State.RUNNING && entry.foregroundWaiters > 0
        }
        val speculativeCapacity = (concurrencyLimit - runningForeground).coerceAtLeast(0)
        val runningSpeculation = entries.values
            .filter { entry ->
                entry.state == State.RUNNING &&
                    entry.foregroundWaiters == 0 &&
                    entry.prewarmInterests > 0 &&
                    entry.job?.isActive != false
            }
            .sortedWith(compareBy<InFlight> { it.startedAtMillis ?: Long.MAX_VALUE }.thenBy { it.token })
        runningSpeculation.drop(speculativeCapacity).forEach { entry ->
            eventSink.record("canceled kind=prewarm key=${entry.request.key.logValue()} reason=limit_downshift")
            cancelEntryLocked(entry, CancellationException("Prewarm concurrency limit decreased."))
        }
    }

    private fun drainLocked() {
        val decision = environmentProvider.currentDecision()
        val concurrencyLimit = decision.maxConcurrent.coerceIn(1, 2)
        val queuedForeground = queue.firstOrNull { it.priority == Priority.FOREGROUND }
        if (
            queuedForeground != null &&
            activeCount >= concurrencyLimit
        ) {
            val victim = entries.values
                .filter { entry ->
                    entry.state == State.RUNNING &&
                        entry.foregroundWaiters == 0 &&
                        entry.prewarmInterests > 0 &&
                        entry.job?.isActive != false
                }
                .maxWithOrNull(compareBy<InFlight> { it.startedAtMillis ?: Long.MIN_VALUE }.thenBy { it.token })
            if (victim != null) {
                eventSink.record(
                    "preempted key=${victim.request.key.logValue()} for=${queuedForeground.request.key.logValue()}"
                )
                cancelEntryLocked(
                    victim,
                    CancellationException("Foreground article preempted speculation.")
                )
            }
        }
        // Permit exactly one serialized foreground lane over cause-independent canceling
        // speculation. A 2 -> 1 downshift can temporarily leave two non-cooperative speculative
        // producers, so the hard peak is the normal unmetered limit (2) plus this single user lane.
        // No replacement speculation is admitted until both cancellation and overlap settle.
        while (true) {
            val next = queue.firstOrNull { it.priority == Priority.FOREGROUND }
                ?: queue.firstOrNull { it.priority == Priority.PREWARM && decision.allowsPrewarm }
                ?: break
            val hasNormalPhysicalCapacity = activeCount < concurrencyLimit
            val mayUseSingleForegroundOverlap =
                next.priority == Priority.FOREGROUND &&
                    !hasNormalPhysicalCapacity &&
                    cancelingSpeculationCount > 0 &&
                    !foregroundOverlapInUse
            val speculationBlockedByOverlap =
                next.priority == Priority.PREWARM &&
                    (cancelingSpeculationCount > 0 || foregroundOverlapInUse)
            if ((!hasNormalPhysicalCapacity && !mayUseSingleForegroundOverlap) || speculationBlockedByOverlap) {
                break
            }
            queue.remove(next)
            next.state = State.RUNNING
            next.startedAtMillis = clockMillis()
            next.usesForegroundOverlap = mayUseSingleForegroundOverlap
            if (mayUseSingleForegroundOverlap) foregroundOverlapInUse = true
            activeCount += 1
            val startedAsPrewarm = next.priority == Priority.PREWARM
            eventSink.record(
                "started kind=${if (startedAsPrewarm) "prewarm" else "foreground"} " +
                    "key=${next.request.key.logValue()} waitMs=${next.startedAtMillis!! - next.queuedAtMillis} " +
                    "active=$activeCount limit=$concurrencyLimit"
            )
            val job = processScope.launch(start = CoroutineStart.DEFAULT) {
                execute(next, startedAsPrewarm)
            }
            next.job = job
            job.invokeOnCompletion { cause -> finishEntry(next, cause) }
        }
    }

    private suspend fun execute(entry: InFlight, prewarmOnly: Boolean) {
        try {
            // The prepared payload is identical for prewarm and foreground. Optional image work
            // is deliberately outside this shared operation, so a running promotion loses nothing.
            val value = prepare(entry.request, entry.forceNetwork) { progress ->
                entry.htmlProgress.value = progress.coerceIn(0, 100)
            }
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (entry.state != State.RUNNING || entries[entry.token] !== entry) {
                    throw CancellationException("Superseded preparation cannot publish its result.")
                }
                val cached = cache.put(entry.request, value, entry.cacheGeneration)
                if (!cached) {
                    eventSink.record(
                        "cache_write_ignored key=${entry.request.key.logValue()} " +
                            "generation=${entry.cacheGeneration} reason=newer_success"
                    )
                }
                entry.result.complete(value)
            }
            val duration = clockMillis() - (entry.startedAtMillis ?: entry.queuedAtMillis)
            eventSink.record(
                "completed kind=${if (prewarmOnly) "prewarm" else "foreground"} " +
                    "key=${entry.request.key.logValue()} durationMs=$duration chars=${value.processedHtml.length}"
            )
        } catch (cancellation: CancellationException) {
            entry.result.cancel(cancellation)
            throw cancellation
        } catch (failure: Throwable) {
            entry.result.completeExceptionally(failure)
            val duration = clockMillis() - (entry.startedAtMillis ?: entry.queuedAtMillis)
            eventSink.record(
                "failed kind=${if (prewarmOnly) "prewarm" else "foreground"} " +
                    "key=${entry.request.key.logValue()} durationMs=$duration error=${failure::class.simpleName}"
            )
        }
    }

    private fun finishEntry(entry: InFlight, cause: Throwable?) {
        synchronized(lock) {
            if (cause is CancellationException && !entry.result.isCompleted) {
                entry.result.cancel(cause)
            }
            if (entry.state == State.RUNNING || entry.state == State.CANCELING) {
                activeCount = (activeCount - 1).coerceAtLeast(0)
            }
            if (entry.cancelingSpeculation) {
                cancelingSpeculationCount = (cancelingSpeculationCount - 1).coerceAtLeast(0)
                entry.cancelingSpeculation = false
            }
            if (entry.usesForegroundOverlap) {
                foregroundOverlapInUse = false
                entry.usesForegroundOverlap = false
            }
            entry.state = State.FINISHED
            entries.remove(entry.token)
            queue.remove(entry)
            drainLocked()
        }
    }
}
