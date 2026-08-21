package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.EdgeToEdgeInsetCoordinator
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.views.ObservableWebView

class osrsArticleOverlayFragment : Fragment(), PageFragment.Callback, osrsArticleChromeHost {
    private var _binding: ActivityPageBinding? = null
    private val binding get() = _binding!!
    override val articleChromeBinding: ActivityPageBinding
        get() = binding
    private lateinit var pageActionBarManager: PageActionBarManager
    private var isContentsOpen = false
    private var contentsDismissSwipe: osrsArticleInteractiveSwipe? = null
    private var contentsDismissDownRawX = Float.NaN
    private var contentsDismissDownRawY = Float.NaN
    private var contentsDismissLastDx = 0f
    private var contentsDismissLastVx = 0f
    private var contentsDismissLastX = 0f
    private var contentsDismissLastTime = 0L
    private var contentsDismissTracking = false
    private var backCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pageBackPreview.visibility = View.GONE
        binding.pageDrawerLayout.clipChildren = false
        binding.pageDrawerLayout.clipToPadding = false
        binding.pageContentHost.clipChildren = false
        binding.pageContentHost.clipToPadding = false
        applyWindowInsets()
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, newArticleFragment(), FRAGMENT_TAG)
                .commitNowAllowingStateLoss()
        }
        setupToolbar()
        setupInteractiveSwipeChrome()
        setupBackNavigation()
    }

    override fun onDestroyView() {
        backCallback?.remove()
        backCallback = null
        _binding = null
        super.onDestroyView()
    }

    fun slidingChrome(): View? = _binding?.root

    private fun newArticleFragment(): PageFragment {
        return PageFragment.newInstance(
            pageId = requireArguments().getString(PageActivity.EXTRA_PAGE_ID),
            pageTitle = requireArguments().getString(PageActivity.EXTRA_PAGE_TITLE),
            source = requireArguments().getInt(
                PageActivity.EXTRA_PAGE_SOURCE,
                HistoryEntry.SOURCE_INTERNAL_LINK
            ),
            snippet = requireArguments().getString(PageActivity.EXTRA_PAGE_SNIPPET),
            thumbnailUrl = requireArguments().getString(PageActivity.EXTRA_PAGE_THUMBNAIL)
        )
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.pageFragmentContainer) { target, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val systemBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                cutout
            )
            val navigationBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()),
                cutout
            )
            val navBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bar_height)
            target.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                navBarHeight + navigationBars.bottom
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root.findViewById(R.id.page_action_bar)) { target, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navigationBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()),
                cutout
            )
            target.translationY = -navigationBars.bottom.toFloat()
            insets
        }
    }

    private fun setupToolbar() {
        binding.pageToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val searchContainer = binding.pageToolbar.findViewById<TextView>(R.id.toolbar_search_container)
        if (searchContainer.hint.isNullOrBlank()) {
            searchContainer.setHint(R.string.page_toolbar_search_hint)
        }
        searchContainer.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.pageToolbar.findViewById<ImageView>(R.id.toolbar_voice_search_button)?.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.pageToolbar.findViewById<View>(R.id.toolbar_overflow_menu_button).setOnClickListener { anchor ->
            currentPageFragment()?.showPageOverflowMenu(anchor) ?: Toast.makeText(
                requireContext(),
                "Error: Could not show menu.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isContentsDrawerOpen()) {
                    closeContents(animate = true)
                    return
                }
                isEnabled = false
                osrsArticleOverlayPresenter.pop(requireActivity())
            }
        }
        backCallback = callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupInteractiveSwipeChrome() {
        val slop = ViewConfiguration.get(requireContext()).scaledPagingTouchSlop
        contentsDismissSwipe = osrsArticleInteractiveSwipe(touchSlop = slop)
        binding.pageContentsScrim.setOnTouchListener { _, event ->
            handleContentsDismissTouch(event, consumeUntracked = true, dismissOnTap = true)
        }
        binding.sidePanelContainer.setOnTouchListener { _, event ->
            handleContentsDismissTouch(event, consumeUntracked = false, dismissOnTap = false)
        }
        binding.tocListView.setOnTouchListener { _, event ->
            handleContentsDismissTouch(event, consumeUntracked = false, dismissOnTap = false)
        }
        binding.pageContentHost.doOnLayout {
            if (!isContentsOpen) {
                applyInteractiveContentsProgress(0f)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleContentsDismissTouch(
        event: MotionEvent,
        consumeUntracked: Boolean,
        dismissOnTap: Boolean
    ): Boolean {
        if (!isContentsOpen && event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        val tracker = contentsDismissSwipe ?: return false
        val drawerWidth = binding.sidePanelContainer.width.toFloat().coerceAtLeast(
            osrsArticleInteractiveSwipe.CONTENTS_DRAWER_WIDTH_DP * resources.displayMetrics.density
        )
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isContentsOpen) return false
                contentsDismissDownRawX = event.rawX
                contentsDismissDownRawY = event.rawY
                contentsDismissLastDx = 0f
                contentsDismissLastVx = 0f
                contentsDismissLastX = event.rawX
                contentsDismissLastTime = event.eventTime
                contentsDismissTracking = false
                tracker.reset()
                return consumeUntracked
            }
            MotionEvent.ACTION_MOVE -> {
                if (contentsDismissDownRawX.isNaN()) return false
                val dx = event.rawX - contentsDismissDownRawX
                val dy = event.rawY - contentsDismissDownRawY
                val dt = (event.eventTime - contentsDismissLastTime).coerceAtLeast(1L)
                contentsDismissLastVx = (event.rawX - contentsDismissLastX) * 1000f / dt
                contentsDismissLastX = event.rawX
                contentsDismissLastTime = event.eventTime
                contentsDismissLastDx = dx
                val axis = tracker.onMove(dx, dy, contentsOpen = true)
                if (tracker.isTracking && axis == osrsArticleInteractiveSwipe.Axis.CONTENTS) {
                    contentsDismissTracking = true
                    binding.sidePanelContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    applyInteractiveContentsProgress(tracker.progress(dx, drawerWidth))
                    return true
                }
                return consumeUntracked
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = if (contentsDismissDownRawX.isNaN()) 0f else event.rawX - contentsDismissDownRawX
                val dy = if (contentsDismissDownRawY.isNaN()) 0f else event.rawY - contentsDismissDownRawY
                val wasTracking = contentsDismissTracking
                val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
                val isTap = !wasTracking &&
                    kotlin.math.abs(dx) <= slop &&
                    kotlin.math.abs(dy) <= slop
                if (wasTracking) {
                    tracker.onMove(dx, dy, contentsOpen = true)
                    val commit = tracker.shouldCommit(dx, contentsDismissLastVx, drawerWidth)
                    tracker.reset()
                    contentsDismissTracking = false
                    contentsDismissDownRawX = Float.NaN
                    if (commit) {
                        settleContentsClosed(animate = true, velocityX = contentsDismissLastVx)
                    } else {
                        cancelInteractiveSwipeChrome()
                    }
                    return true
                }
                tracker.reset()
                contentsDismissTracking = false
                contentsDismissDownRawX = Float.NaN
                if (event.actionMasked == MotionEvent.ACTION_UP && isTap && isContentsOpen && dismissOnTap) {
                    closeContents(animate = true)
                    return true
                }
                return consumeUntracked
            }
        }
        return false
    }

    override fun onPageSwipe(gravity: Int, velocityX: Float) {
        val action = when (gravity) {
            Gravity.START -> ReaderSwipeAction.BACK
            Gravity.END -> ReaderSwipeAction.CONTENTS
            else -> return
        }
        if (gravity == Gravity.END && isContentsOpen) {
            settleContentsClosed(animate = true, velocityX = velocityX)
            return
        }
        if (!ReaderGesturePolicy.isEnabled(action, Prefs.readerPreferences)) {
            cancelInteractiveSwipeChrome()
            return
        }
        if (gravity == Gravity.END) {
            if (isContentsOpen) {
                settleContentsClosed(animate = true, velocityX = velocityX)
            } else {
                setContentsRevealProgress(1f, animate = true, velocityX = velocityX)
                isContentsOpen = true
            }
        } else {
            commitInteractiveBack(velocityX)
        }
    }

    override fun onPageSwipeProgress(gravity: Int, progress: Float) {
        if (gravity == Gravity.END) {
            applyInteractiveContentsProgress(progress)
        } else if (gravity == Gravity.START) {
            applyInteractiveBackProgress(progress)
        }
    }

    override fun onPageSwipeCancelled() {
        cancelInteractiveSwipeChrome()
    }

    override fun isContentsDrawerOpen(): Boolean = isContentsOpen

    override fun openContents() {
        setContentsRevealProgress(1f, animate = true)
        isContentsOpen = true
    }

    override fun closeContents(animate: Boolean) {
        settleContentsClosed(animate = animate)
    }

    override fun onWebViewReady(webView: ObservableWebView) = Unit

    override fun onPageStartActionMode(callback: android.view.ActionMode.Callback) {
        requireActivity().startActionMode(callback)
    }

    override fun onPageStopActionMode() = Unit

    override fun onPageFinishActionMode() = Unit

    override fun getPageToolbarContainer(): View = binding.pageAppbarLayout

    override fun getPageActionBarManager(): PageActionBarManager {
        if (!::pageActionBarManager.isInitialized) {
            pageActionBarManager = PageActionBarManager(binding)
        }
        return pageActionBarManager
    }

    private fun currentPageFragment(): PageFragment? {
        return childFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
    }

    private fun applyInteractiveBackProgress(progress: Float) {
        val sliding = binding.root
        val width = sliding.width.toFloat().coerceAtLeast(1f)
        sliding.animate().cancel()
        sliding.translationX = progress.coerceIn(0f, 1f) * width
    }

    private fun commitInteractiveBack(velocityX: Float) {
        val sliding = binding.root
        val width = sliding.width.toFloat().coerceAtLeast(1f)
        val progress = (sliding.translationX / width).coerceIn(0f, 1f)
        val remaining = osrsArticleInteractiveSwipe.remainingPx(progress, width)
        val duration = osrsArticleInteractiveSwipe.remainingCommitDurationMs(
            progress,
            velocityX,
            width,
            resources.displayMetrics.density
        )
        val interpolator = osrsArticleInteractiveSwipe.settleInterpolator(
            velocityX,
            remaining,
            duration
        )
        sliding.animate().cancel()
        sliding.animate()
            .translationX(width)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .withEndAction {
                osrsArticleOverlayPresenter.pop(requireActivity())
            }
            .start()
    }

    private fun applyInteractiveContentsProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val drawer = binding.sidePanelContainer
        drawer.animate().cancel()
        val width = if (drawer.width > 0) {
            drawer.width.toFloat()
        } else {
            osrsArticleInteractiveSwipe.CONTENTS_DRAWER_WIDTH_DP * resources.displayMetrics.density
        }
        drawer.translationX = osrsArticleInteractiveSwipe.contentsPeekTranslationX(width, clamped)
        drawer.isClickable = clamped >= 0.98f
        val scrim = binding.pageContentsScrim
        if (isContentsOpen || clamped > 0.02f) {
            scrim.visibility = View.VISIBLE
            scrim.alpha = clamped.coerceAtLeast(if (isContentsOpen) 0.08f else 0f)
            scrim.isClickable = true
        } else {
            hideContentsScrim()
        }
    }

    private fun setContentsRevealProgress(progress: Float, animate: Boolean, velocityX: Float = 0f) {
        val drawer = binding.sidePanelContainer
        val width = if (drawer.width > 0) {
            drawer.width.toFloat()
        } else {
            osrsArticleInteractiveSwipe.CONTENTS_DRAWER_WIDTH_DP * resources.displayMetrics.density
        }
        val target = osrsArticleInteractiveSwipe.contentsPeekTranslationX(width, progress)
        val scrim = binding.pageContentsScrim
        drawer.animate().cancel()
        scrim.animate().cancel()
        if (animate) {
            val remaining = kotlin.math.abs(drawer.translationX - target)
            val traveled = 1f - (remaining / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val duration = osrsArticleInteractiveSwipe.remainingCommitDurationMs(
                traveled,
                velocityX,
                width,
                resources.displayMetrics.density
            )
            val interpolator = osrsArticleInteractiveSwipe.settleInterpolator(
                velocityX,
                remaining,
                duration
            )
            drawer.animate()
                .translationX(target)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
            if (progress <= 0f) {
                scrim.visibility = View.VISIBLE
                scrim.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .withEndAction { hideContentsScrim() }
                    .start()
            } else {
                scrim.visibility = View.VISIBLE
                scrim.isClickable = true
                scrim.animate()
                    .alpha(progress.coerceAtLeast(0.08f))
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .start()
            }
        } else {
            drawer.translationX = target
            if (progress <= 0f) {
                hideContentsScrim()
            } else {
                scrim.visibility = View.VISIBLE
                scrim.alpha = progress.coerceAtLeast(0.08f)
                scrim.isClickable = true
            }
        }
        drawer.isClickable = progress >= 0.98f
    }

    private fun settleContentsClosed(animate: Boolean = true, velocityX: Float = 0f) {
        setContentsRevealProgress(0f, animate = animate, velocityX = velocityX)
        isContentsOpen = false
    }

    private fun cancelInteractiveSwipeChrome() {
        val sliding = binding.root
        val width = sliding.width.toFloat().coerceAtLeast(1f)
        val progress = (sliding.translationX / width).coerceIn(0f, 1f)
        val remaining = progress * width
        val duration = osrsArticleInteractiveSwipe.remainingCommitDurationMs(
            1f - progress,
            0f,
            width,
            resources.displayMetrics.density
        )
        val interpolator = osrsArticleInteractiveSwipe.settleInterpolator(0f, remaining, duration)
        sliding.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
        if (isContentsOpen) {
            setContentsRevealProgress(1f, animate = true)
        } else {
            setContentsRevealProgress(0f, animate = true)
            hideContentsScrim()
        }
    }

    private fun hideContentsScrim() {
        binding.pageContentsScrim.visibility = View.GONE
        binding.pageContentsScrim.alpha = 0f
        binding.pageContentsScrim.isClickable = false
    }

    companion object {
        private const val FRAGMENT_TAG = "PageFragmentTag"

        fun newInstance(intent: Intent): osrsArticleOverlayFragment {
            return osrsArticleOverlayFragment().apply {
                arguments = intent.extras ?: Bundle()
            }
        }
    }
}
