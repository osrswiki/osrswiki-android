package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.util.DimenUtil
import com.omiyawaki.osrswiki.view.PageScrollerView
import com.omiyawaki.osrswiki.views.ObservableWebView

class ContentsHandler(private val fragment: PageFragment) :
    ObservableWebView.OnScrollChangeListener {

    // Get references to views from the fragment's hosting activity and the fragment itself.
    private val activity = fragment.requireActivity() as PageActivity
    private val binding = activity.binding
    private val webView = fragment.binding.pageWebView
    private val scrollerView = binding.pageScrollerView
    private val tocListView = binding.tocListView
    private val drawerLayout = binding.pageDrawerLayout

    private val tocAdapter = TocAdapter(activity)
    private var isInitialized = false

    // This block runs when the class is instantiated.
    init {
        tocListView.adapter = tocAdapter
        tocListView.setOnItemClickListener { _, _, position, _ ->
            val section = tocAdapter.getItem(position) as Section
            scrollToSection(section)
            hide()
        }

        // Wire up the listeners for bi-directional communication.
        webView.addOnScrollChangeListener(this)
        scrollerView.callback = ScrollerCallback()

        isInitialized = true
    }

    fun setup(sections: List<Section>) {
        if (!isInitialized) return
        tocAdapter.setSections(sections)
        scrollerView.visibility = if (sections.size > 1) View.VISIBLE else View.GONE
    }

    fun show() {
        if (!isInitialized) return
        drawerLayout.openDrawer(binding.sidePanelContainer)
    }

    fun hide() {
        if (!isInitialized) return
        drawerLayout.closeDrawer(binding.sidePanelContainer)
    }

    fun isVisible(): Boolean {
        if (!isInitialized) return false
        return drawerLayout.isDrawerOpen(binding.sidePanelContainer)
    }

    private fun scrollToSection(section: Section) {
        if (!isInitialized) return
        if (section.isLead) {
            webView.scrollTo(0, 0)
        } else {
            // The Wikipedia app uses a more complex JS bridge here.
            // Our existing JS call is simpler and should still work.
            val script = "document.getElementById('${section.anchor}').scrollIntoView();"
            webView.evaluateJavascript(script, null)
        }
    }

    // Called when the WebView is scrolled MANUALLY.
    override fun onScrollChanged(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean) {
        if (isInitialized) {
            setScrollerPosition()
        }
    }

    // This function moves the scroller handle based on the WebView's scroll position.
    private fun setScrollerPosition() {
        val webViewContentHeight = webView.contentHeight * DimenUtil.densityScalar
        if (webViewContentHeight == 0f) return

        val availableScrollHeight = (webViewContentHeight - webView.height).coerceAtLeast(0f)
        val scrollProportion = webView.scrollY / availableScrollHeight

        val availableScrollerHeight = (drawerLayout.height - scrollerView.height).coerceAtLeast(0)
        val newTopMargin = (scrollProportion * availableScrollerHeight).toInt()

        scrollerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = newTopMargin.coerceIn(0, availableScrollerHeight)
        }
    }

    // This function scrolls the WebView based on the scroller handle's drag events.
    private fun onScrollerMoved(dy: Float) {
        val webViewContentHeight = webView.contentHeight * DimenUtil.densityScalar
        if (webViewContentHeight == 0f) return

        val availableScrollHeight = (webViewContentHeight - webView.height)
        val availableScrollerHeight = (drawerLayout.height - scrollerView.height).toFloat()

        if (availableScrollerHeight <= 0f) return

        val scrollBy = dy * (availableScrollHeight / availableScrollerHeight)
        webView.scrollBy(0, scrollBy.toInt())
    }

    private inner class ScrollerCallback : PageScrollerView.Callback {
        override fun onScrollStart() {
            // Not needed for our implementation yet.
        }
        override fun onScrollStop() {
            // Could be used to hide the drawer after scrolling, for example.
        }
        override fun onVerticalScroll(dy: Float) {
            onScrollerMoved(dy)
        }
    }

    private inner class TocAdapter(private val context: Context) : BaseAdapter() {
        private var adapterSections = emptyList<Section>()
        private var highlightedPosition = 0

        fun setSections(sections: List<Section>) {
            adapterSections = sections
            notifyDataSetChanged()
        }

        fun setHighlightedItem(position: Int) {
            if (highlightedPosition != position) {
                highlightedPosition = position
                notifyDataSetChanged()
            }
        }

        override fun getCount(): Int = adapterSections.size
        override fun getItem(position: Int): Any = adapterSections[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.toc_item, parent, false)
            val section = getItem(position) as Section
            val textView = view.findViewById<TextView>(R.id.toc_item_text)
            val bullet = view.findViewById<ImageView>(R.id.toc_item_bullet)
            textView.text = section.title
            val bodyTypeface = Typeface.SERIF
            val textSize: Float
            when {
                section.isLead -> {
                    textSize = 24f
                    textView.typeface = bodyTypeface
                }
                section.level == 2 -> {
                    textSize = 18f
                    textView.typeface = bodyTypeface
                }
                else -> {
                    textSize = 14f
                    textView.typeface = bodyTypeface
                }
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            val typedValue = TypedValue()
            if (position == highlightedPosition) {
                textView.typeface = Typeface.create(textView.typeface, Typeface.BOLD_ITALIC)
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
                textView.setTextColor(typedValue.data)
                bullet.setColorFilter(typedValue.data)
            } else {
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                textView.setTextColor(typedValue.data)
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                bullet.setColorFilter(typedValue.data)
            }
            return view
        }
    }
}
