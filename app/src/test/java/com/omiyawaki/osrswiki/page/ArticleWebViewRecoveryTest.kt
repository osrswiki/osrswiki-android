package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.page.cache.AssetCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleWebViewRecoveryTest {

    @After
    fun tearDown() {
        AssetCache.resetLimitsForTests()
        AssetCache.clear()
    }

    @Test
    fun assetCacheEvictsLeastRecentlyUsedEntriesWhenByteLimitExceeded() {
        AssetCache.configureForTests(maxBytes = 10, maxEntries = 10)

        AssetCache.put("first", ByteArray(6) { 1 })
        assertEquals(1, AssetCache.stats().entryCount)
        assertEquals(6, AssetCache.stats().totalBytes)

        AssetCache.put("second", ByteArray(6) { 2 })

        assertNull(AssetCache.get("first"))
        assertEquals(6, AssetCache.stats().totalBytes)
        assertEquals(1, AssetCache.stats().entryCount)
        assertEquals(2, AssetCache.get("second")?.first()?.toInt())
    }

    @Test
    fun assetCacheSkipsSingleAssetsLargerThanByteLimit() {
        AssetCache.configureForTests(maxBytes = 10, maxEntries = 10)

        AssetCache.put("too-large", ByteArray(11))

        assertNull(AssetCache.get("too-large"))
        assertEquals(0, AssetCache.stats().entryCount)
        assertEquals(0, AssetCache.stats().totalBytes)
    }

    @Test
    fun appWebViewClientDoesNotBlockInterceptorOnNetworkFetches() {
        val source = sourceFile("AppWebViewClient.kt").readText()
        val interceptorBody = source.substringAfter("override fun shouldInterceptRequest")
            .substringBefore("override fun onPageStarted")

        assertFalse(interceptorBody.contains("runBlocking"))
        assertFalse(interceptorBody.contains("fetchAndCacheModuleResponse"))
        assertFalse(interceptorBody.contains("openConnection"))
    }

    @Test
    fun rendererGoneReturnsHandledAndNotifiesRecoveryCallback() {
        var recoveryRequested = false

        val handled = PageWebViewManager.handleRenderProcessGoneForRecovery(
            didCrash = true,
            onRecoveryRequested = { recoveryRequested = true }
        )

        assertTrue(handled)
        assertTrue(recoveryRequested)
    }

    @Test
    fun webViewManagerDisposesPendingRenderCallbacksBeforeFragmentBindingCanBeCleared() {
        val source = sourceFile("PageWebViewManager.kt").readText()

        assertTrue(source.contains("fun dispose()"))
        assertTrue(source.contains("mainHandler.removeCallbacksAndMessages(null)"))
        assertTrue(source.contains("private var isDisposed"))
    }

    @Test
    fun pageRestoresNavigationScrollAfterContentHeightCanHoldIt() {
        val fragmentSource = sourceFile("PageFragment.kt").readText()
        val managerSource = sourceFile("PageWebViewManager.kt").readText()
        val activitySource = sourceFile("PageActivity.kt").readText()

        assertTrue(fragmentSource.contains("lastObservedScrollY"))
        assertTrue(fragmentSource.contains("trackWebViewScrollPosition()"))
        assertTrue(activitySource.contains("commitPushedArticle"))
        assertTrue(managerSource.contains("contentCanHoldScroll"))
        assertTrue(managerSource.contains("waitUntilScrollable"))
        assertTrue(managerSource.contains("webView.alpha = 0f"))
    }

    @Test
    fun pageFragmentRevealCompletionUsesNullableBindingSnapshot() {
        val source = sourceFile("PageFragment.kt").readText()
        val readyCallbackBody = source.substringAfter("override fun onPageReadyForDisplay()")
            .substringBefore("fun showFindInPage()")

        assertTrue(readyCallbackBody.contains("val currentBinding = _binding ?: return@finalizeAndRevealPage"))
        assertFalse(readyCallbackBody.contains("binding.pageWebView.evaluateJavascript"))
    }

    @Test
    fun pageContentLoaderCanCancelActivePageWorkWhenFragmentStops() {
        val source = sourceFile("PageContentLoader.kt").readText()

        assertTrue(source.contains("private var pageLoadJob"))
        assertTrue(source.contains("private var backgroundAssetsJob"))
        assertTrue(source.contains("fun cancelActivePageWork()"))
    }

    @Test
    fun pageFragmentDoesNotAllocateReplacementWebViewWhileStopped() {
        val source = sourceFile("PageFragment.kt").readText()
        val releaseMethod = source.substringAfter("private fun releaseStoppedWebViewResources()")
            .substringBefore("private fun restoreStoppedWebViewResourcesIfNeeded()")
        val restoreMethod = source.substringAfter("private fun restoreStoppedWebViewResourcesIfNeeded()")
            .substringBefore("override fun onDestroyView()")

        assertTrue(source.contains("private var releasedWebViewIndex"))
        assertTrue(source.contains("private var releasedWebViewLayoutParams"))
        assertTrue(source.contains("private var releasedRootView"))
        assertFalse(releaseMethod.contains("ObservableWebView(requireContext())"))
        assertTrue(releaseMethod.contains("contentsHandler = null"))
        assertTrue(releaseMethod.contains("pageWebViewManager = null"))
        assertTrue(releaseMethod.contains("pageUiUpdater = null"))
        assertTrue(releaseMethod.contains("pageLoadCoordinator = null"))
        assertTrue(releaseMethod.contains("_binding = null"))
        assertTrue(restoreMethod.contains("ObservableWebView(requireContext())"))
        assertTrue(restoreMethod.contains("releasedRootView ?: _binding?.root ?: return"))
        assertTrue(restoreMethod.contains("contentsHandler = ContentsHandler(this)"))
        assertTrue(restoreMethod.contains("FragmentPageBinding.bind(root)"))
    }

    @Test
    fun pageFragmentReleasesArticleWebViewWhenCoveredByNextPage() {
        val source = sourceFile("PageFragment.kt").readText()
        val onPauseMethod = source.substringAfter("override fun onPause()")
            .substringBefore("override fun onResume()")
        val onResumeMethod = source.substringAfter("override fun onResume()")
            .substringBefore("override fun onStart()")
        val releaseMethod = source.substringAfter("private fun releaseStoppedWebViewResources()")
            .substringBefore("private fun restoreStoppedWebViewResourcesIfNeeded()")

        assertTrue(onPauseMethod.contains("if (!isHidden)"))
        assertTrue(onPauseMethod.contains("releaseStoppedWebViewResources()"))
        val onStopMethod = source.substringAfter("override fun onStop()")
            .substringBefore("override fun onPause()")
        assertTrue(onStopMethod.contains("releaseStoppedWebViewResources()"))
        assertTrue(onResumeMethod.contains("restoreStoppedWebViewResourcesIfNeeded()"))
        assertTrue(releaseMethod.contains("destroyReleasedWebView(oldWebView)"))
        assertTrue(source.contains("private fun requestWebViewHeapTrim()"))
        assertTrue(source.contains("System.runFinalization()"))
    }

    @Test
    fun pageFragmentPreservesRenderedArticleAndScrollWhenExternalHandoffStopsIt() {
        val source = sourceFile("PageFragment.kt").readText()
        val releaseMethod = source.substringAfter("private fun releaseStoppedWebViewResources()")
            .substringBefore("private fun restoreStoppedWebViewResourcesIfNeeded()")

        assertTrue(source.contains("private var releasedWebViewScrollY = 0"))
        assertTrue(releaseMethod.contains("releasedWebViewScrollY = oldWebView.scrollY"))
        assertFalse(releaseMethod.contains("htmlContent = null"))
        assertFalse(releaseMethod.contains("tableOfContentsSections = emptyList()"))
    }

    @Test
    fun pageFragmentRestoresExternalHandoffScrollAfterReplacementRenderIsReady() {
        val source = sourceFile("PageFragment.kt").readText()
        val readyCallbackBody = source.substringAfter("override fun onPageReadyForDisplay()")
            .substringBefore("fun showFindInPage()")
        val restoreMethod = source.substringAfter("private fun restoreReleasedWebViewScrollPositionIfNeeded")
            .substringBefore("override fun onDestroyView()")

        assertTrue(source.contains("private var shouldRestoreReleasedWebViewScroll = false"))
        assertTrue(readyCallbackBody.contains("restoreReleasedWebViewScrollPositionIfNeeded(currentBinding.pageWebView)"))
        assertTrue(restoreMethod.contains("webView.post"))
        assertTrue(restoreMethod.contains("webView.scrollTo(0, releasedWebViewScrollY)"))
        assertTrue(restoreMethod.contains("releasedWebViewScrollY = 0"))
        assertTrue(restoreMethod.contains("shouldRestoreReleasedWebViewScroll = false"))
    }

    @Test
    fun pageActivityKeepsDeepArticleNavigationInsideSingleActivity() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val pageActivityEntry = manifest.substringAfter("android:name=\".page.PageActivity\"")
            .substringBefore("/>")
        val source = sourceFile("PageActivity.kt").readText()

        assertTrue(pageActivityEntry.contains("android:launchMode=\"singleTop\""))
        assertTrue(source.contains("private val articleBackStack"))
        assertTrue(source.contains("PixelCopy.request"))
        assertTrue(source.contains("backPreviewStack"))
        assertTrue(source.contains("captureViewRestore"))
        assertTrue(source.contains("commitPushedArticle"))
        assertTrue(source.contains("override fun onNewIntent"))
        assertTrue(source.contains("showArticleFromIntent(intent, pushCurrent = true)"))
        assertTrue(source.contains("popArticleBackStack()"))
    }

    @Test
    fun pageActivityPersistsInternalArticleBackStackAcrossRecreation() {
        val source = sourceFile("PageActivity.kt").readText()

        assertTrue(source.contains("override fun onSaveInstanceState"))
        assertTrue(source.contains("STATE_ARTICLE_BACK_STACK"))
        assertTrue(source.contains("STATE_CURRENT_ARTICLE_ARGS"))
        assertTrue(source.contains("restoreArticleState(savedInstanceState)"))
        assertTrue(source.contains("outState.putParcelableArrayList"))
        assertTrue(source.contains("BundleCompat.getParcelableArrayList"))
        assertTrue(source.contains("Bundle::class.java"))
        assertTrue(source.contains("articleBackStack.addAll"))
        assertTrue(source.contains("ArticleArgs.fromBundle"))
        assertTrue(source.contains("fun toBundle(): Bundle"))
    }

    @Test
    fun pageActivityIgnoresStaleOfflineBannerResultForPreviousArticle() {
        val source = sourceFile("PageActivity.kt").readText()
        val bannerMethod = source.substringAfter("private fun checkAndShowOfflineBanner()")
            .substringBefore("private fun hasNetworkConnection()")

        assertTrue(bannerMethod.contains("val requestedArticleArgs = currentArticleArgs()"))
        assertTrue(bannerMethod.contains("if (currentArticleArgs() != requestedArticleArgs)"))
        assertTrue(bannerMethod.contains("return@launch"))
        assertTrue(bannerMethod.indexOf("if (currentArticleArgs() != requestedArticleArgs)") < bannerMethod.indexOf("binding.pageOfflineBanner.visibility"))
    }

    @Test
    fun pageActivityExposesDebugGatedDeterministicDeepNavigationFixtureProbe() {
        val source = sourceFile("PageActivity.kt").readText()

        assertTrue(source.contains("fun runDeepNavigationFixtureAuditForDebugTests("))
        assertTrue(source.contains("BuildConfig.DEBUG"))
        assertTrue(source.contains("EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS"))
        assertTrue(source.contains("pushArticleArgsForNativeStack"))
        assertTrue(source.contains("popArticleArgsFromNativeStack"))
        assertTrue(source.contains("replaceArticleFragmentIfFixtureProbeAllows"))
        assertTrue(source.contains("if (!isDeepNavigationFixtureProbeEnabledForDebugTests())"))
    }

    @Test
    fun androidDeepNavigationHarnessHasFullScaleDeterministicFixtureMode() {
        val instrumentationSource = File(
            "src/androidTest/java/com/omiyawaki/osrswiki/page/AndroidDeepNavigationFixtureAuditTest.kt"
        ).readText()
        val hostHarness = File("../../../scripts/audit/android_deep_navigation_stack_audit.py").readText()

        assertTrue(instrumentationSource.contains("fixtureStartCount"))
        assertTrue(instrumentationSource.contains("fixtureDepth"))
        assertTrue(instrumentationSource.contains("10_000"))
        assertTrue(instrumentationSource.contains("assertEquals(startCount * depth, result.forwardTransitions)"))
        assertTrue(instrumentationSource.contains("assertEquals(0, result.mismatchCount)"))
        assertTrue(instrumentationSource.contains("fixtureBatchSize"))
        assertTrue(instrumentationSource.contains("while (processedStarts < startCount)"))
        assertTrue(hostHarness.contains("--deterministic-fixture"))
        assertTrue(hostHarness.contains("ro.kernel.qemu"))
        assertTrue(hostHarness.contains("require_summary_passed"))
        assertTrue(hostHarness.contains("Android instrumentation failed"))
        assertTrue(hostHarness.contains("Android deep-navigation audit did not satisfy requested criteria"))
    }

    @Test
    fun pageAssetDownloaderHasNoEagerOptionalMediaPrefetchUnderDeepStacks() {
        val source = sourceFile("PageAssetDownloader.kt").readText()
        val preparedText = source.substringAfter("private suspend fun processPreparedText")
            .substringBefore("private fun logPreparationFailure")

        assertFalse(source.contains("downloadBackgroundAssets"))
        assertFalse(source.contains("downloadPostTextPriorityAssets"))
        assertTrue(preparedText.contains("backgroundUrls = emptyList()"))
    }

    @Test
    fun pageAssetDownloaderChecksCancellationBeforeHeavyHtmlSerialization() {
        val source = sourceFile("PageAssetDownloader.kt").readText()
        val processMethod = source.substringAfter("private suspend fun processPreparedText")
            .substringBefore("private fun logPreparationFailure")
        val preprocessMethod = source.substringAfter("private suspend fun preprocessHtml")
            .substringBefore("private fun normalizeRelativeUrls")

        assertTrue(processMethod.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(processMethod.indexOf("currentCoroutineContext().ensureActive()") < processMethod.indexOf("preprocessHtml(document)"))
        assertTrue(preprocessMethod.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(preprocessMethod.indexOf("currentCoroutineContext().ensureActive()") < preprocessMethod.indexOf("document.body()"))
    }

    @Test
    fun preparedArticlePathDoesNotPerformPriorityImageDiscoveryBeforeFirstPaint() {
        val source = sourceFile("PageAssetDownloader.kt").readText()
        val preparedText = source.substringAfter("private suspend fun processPreparedText")
            .substringBefore("private fun logPreparationFailure")

        assertFalse(source.contains("extractAssetUrls"))
        assertFalse(source.contains("isPriorityImageElement"))
        assertTrue(preparedText.contains("text-only"))
        assertTrue(preparedText.contains("currentCoroutineContext().ensureActive()"))
    }

    @Test
    fun pageHtmlBuilderAvoidsTrimIndentCopyForFullArticleDocument() {
        val source = sourceFile("PageHtmlBuilder.kt").readText()
        val buildMethod = source.substringAfter("fun buildFullHtmlDocument")
            .substringBefore("private fun removeDuplicatePageHeaders")

        assertTrue(buildMethod.contains("StringBuilder("))
        assertTrue(buildMethod.contains("append(articleBodyContent)"))
        assertFalse(buildMethod.contains(".trimIndent()"))
    }

    @Test
    fun pageContentLoaderChecksCancellationBeforeFinalHtmlBuild() {
        val source = sourceFile("PageContentLoader.kt").readText()
        val successBranch = source.substringAfter("is DownloadProgress.Success ->")
            .substringBefore("is DownloadProgress.Failure ->")

        assertTrue(successBranch.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(successBranch.indexOf("currentCoroutineContext().ensureActive()") < successBranch.indexOf("pageHtmlBuilder.buildFullHtmlDocument"))
    }

    @Test
    fun nativeMapCleanupRemovesChildFragmentsBeforeContainerViews() {
        val source = sourceFile("NativeMapHandler.kt").readText()
        val cleanupBody = source.substringAfter("fun cleanup()")
            .substringBeforeLast("}")

        assertTrue(source.contains("private var isCleanedUp"))
        assertTrue(cleanupBody.contains("childFragmentManager"))
        assertTrue(cleanupBody.contains("commitNowAllowingStateLoss()"))
        assertTrue(cleanupBody.indexOf("childFragmentManager") < cleanupBody.indexOf("removeView(container)"))
    }

    @Test
    fun visibleNativeMapPlaceholdersAttachImmediatelyWhileCollapsedMapsStayLazy() {
        val source = sourceFile("NativeMapHandler.kt").readText()
        val measuredBridgeBody = source.substringAfter("fun onMapPlaceholderMeasured")
            .substringBefore("@JavascriptInterface", missingDelimiterValue = source.substringAfter("fun onMapPlaceholderMeasured"))
        val toggleBridgeBody = source.substringAfter("fun onCollapsibleToggled")
            .substringBefore("@JavascriptInterface", missingDelimiterValue = source.substringAfter("fun onCollapsibleToggled"))

        assertTrue(source.contains("pendingMapPlaceholders"))
        assertTrue(measuredBridgeBody.contains("rememberMapPlaceholder"))
        assertFalse(measuredBridgeBody.contains("createMapContainer"))
        assertTrue(toggleBridgeBody.contains("ensureMapContainer"))
        assertTrue(source.contains("overlayState.recordDesiredVisibility(mapId, isOpening)"))
        assertTrue(source.contains("overlayState.recordMeasurement("))
        assertTrue(source.contains("mapContainers[id]?.let { applyMapContainerLayout(it, rect) }"))
        assertTrue(source.contains("val shouldBeVisible = record.desiredVisible == true"))
        assertTrue(source.contains("val initiallyVisible: Boolean = false"))
        assertTrue(source.contains("renderedMapIds"))
        assertTrue(source.contains("renderedMapIds += id"))
        assertTrue(source.contains("renderedMapIds.contains(mapId)"))
        assertTrue(source.contains("hideStaticPlaceholder(mapId)"))
        assertTrue(source.contains("ownerId != \"article-navigation\""))
        assertTrue(source.contains("onArticleHorizontalScrollNotOwned()"))
        assertTrue(source.contains("claimPointer = true"))
    }

    @Test
    fun articleNavigationWaitsForExplicitGenerationBoundDomOwnershipClassification() {
        val source = sourceFile("PageFragment.kt").readText()
        val setupBody = source.substringAfter("private fun setupGestureDetector()")
            .substringBefore("/** Called on the view thread")

        assertTrue(setupBody.contains("registerNavigationCandidate(generation)"))
        assertTrue(setupBody.contains("resolveArticleSwipeOwnership(generation, gravity)"))
        assertTrue(setupBody.contains("domSequenceFor(generation)"))
        assertTrue(setupBody.contains("articleHorizontalGestureSnapshotQuery(domSequence)"))
        assertTrue(setupBody.contains("recordFinalClassification(generation, snapshot)"))
        assertTrue(setupBody.contains("hasDomClassification()"))
        assertTrue(setupBody.contains("requestDisallowInterceptTouchEvent(true)"))
        assertFalse(setupBody.contains("latestTouchIsOwned"))
        assertFalse(setupBody.contains("postDelayed"))
        assertFalse(source.contains("LOCAL_SCROLL_CLAIM_GRACE_MS"))
    }

    @Test
    fun articleMapDiscoveryIncludesVisibleAndNonCollapsibleKartographerMaps() {
        val script = File("src/main/assets/web/collapsible_content.js").readText()
        val measure = script.substringAfter("function measureAndPreloadMaps()")
            .substringBefore("window.measureAndPreloadMaps")

        assertTrue(measure.contains("document.querySelectorAll('.mw-kartographer-map')"))
        assertTrue(measure.contains("container.classList.contains('collapsed')"))
        assertTrue(measure.contains("sendMapMeasurement(mapPlaceholder, index)"))
        assertTrue(script.contains("initiallyVisible: isNearViewport(rect)"))
        assertTrue(script.contains("IntersectionObserver"))
        assertTrue(script.contains("onMapViewportVisibilityChanged"))
        assertFalse(measure.contains("if (!container) return"))
    }

    @Test
    fun phrasingMediaWikiIconsRemainInlineWithoutChangingStandaloneFigures() {
        val css = File("src/main/assets/styles/fixes.css").readText()

        assertTrue(css.contains("span.mw-default-size[typeof^=\"mw:File\"] img.mw-file-element"))
        assertTrue(css.contains("display: inline-block !important"))
        assertTrue(css.contains("vertical-align: middle !important"))
        assertFalse(css.contains("\nimg.mw-file-element {\n"))
    }

    @Test
    fun pageLoadCoordinatorDedupeOnlyReusesErrorFreeContent() {
        val source = sourceFile("PageLoadCoordinator.kt").readText()

        assertTrue(source.contains("pageViewModel.uiState.error.isNullOrEmpty()"))
        assertFalse(source.contains("!pageViewModel.uiState.error.isNullOrEmpty()"))
    }

    @Test
    fun pageLoadCoordinatorChecksAlreadyRenderedContentBeforeResettingLoadingState() {
        val source = sourceFile("PageLoadCoordinator.kt").readText()

        assertTrue(
            source.indexOf("val contentAlreadyLoaded") < source.indexOf("PageUiState(isLoading = true")
        )
    }

    @Test
    fun pageActivityKeepsPreviousArticleFragmentAliveOnPushAndShowOnPop() {
        val source = sourceFile("PageActivity.kt").readText()
        val pushMethod = source.substringAfter("private fun pushCoveringArticleFragment()")
            .substringBefore("private fun revealPreviousArticleFragment()")
        val popMethod = source.substringAfter("private fun revealPreviousArticleFragment()")
            .substringBefore("private fun replaceArticleFragmentIfFixtureProbeAllows()")

        assertTrue(source.contains("hiddenArticleFragmentTags"))
        assertTrue(pushMethod.contains(".hide("))
        assertTrue(pushMethod.contains(".add(R.id.page_fragment_container"))
        assertTrue(popMethod.contains(".show("))
        assertTrue(popMethod.contains(".remove("))
        assertTrue(source.contains("osrsUnderlyingActivityPreview"))
        assertTrue(source.substringAfter("private fun popArticleBackStack()").contains("revealPreviousArticleFragment()"))
        assertTrue(source.substringAfter("private fun commitPushedArticle").contains("pushCoveringArticleFragment()"))
    }

    @Test
    fun pageActivityUsesCallerSnapshotWhenArticleBackPreviewStackIsEmpty() {
        val source = sourceFile("PageActivity.kt").readText()
        val progressMethod = source.substringAfter("private fun applyInteractiveBackProgress")
            .substringBefore("private fun applyInteractiveContentsProgress")

        assertTrue(progressMethod.contains("osrsUnderlyingActivityPreview"))
        assertTrue(progressMethod.contains("backPreviewStack.lastOrNull()"))
        assertTrue(source.contains("overridePendingTransition(0, 0)"))
        val baseActivity = File("src/main/java/com/omiyawaki/osrswiki/activity/BaseActivity.kt").readText()
        assertTrue(baseActivity.contains("osrsUnderlyingActivityPreview.captureFromCaller(this)"))
        assertFalse(
            progressMethod.contains("if (clamped > 0f && preview != null)") &&
                !progressMethod.contains("osrsUnderlyingActivityPreview")
        )
    }

    @Test
    fun pageUiDoesNotCoverArticlesWithTheOsrsLoadingBar() {
        val updater = sourceFile("PageUiUpdater.kt").readText()
        val html = sourceFile("PageHtmlBuilder.kt").readText()
        val css = File("src/main/assets/styles/base.css").readText()

        assertFalse(updater.contains("progressContainer.visibility = View.VISIBLE"))
        assertFalse(html.contains("style=\"visibility: hidden;\""))
        assertFalse(css.contains("visibility: hidden;"))
    }

    @Test
    fun openContentsRightSwipeIsNotBlockedByContentsProgressEarlyReturn() {
        val source = sourceFile("PageActivity.kt").readText()
        val progressMethod = source.substringAfter("private fun applyInteractiveContentsProgress")
            .substringBefore("private fun setContentsRevealProgress")

        assertFalse(progressMethod.contains("if (isContentsOpen && progress <= 0f)"))
        assertTrue(source.contains("contentsOpen ="))
    }

    private fun sourceFile(fileName: String): File {
        return File("src/main/java/com/omiyawaki/osrswiki/page/$fileName")
    }
}
