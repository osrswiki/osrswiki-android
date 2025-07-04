package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Typeface
import android.util.SparseIntArray
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
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.view.PageScrollerView
import com.omiyawaki.osrswiki.views.ObservableWebView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ContentsHandler(
    private val activity: PageActivity,
    private val drawerLayout: DrawerLayout
) : ObservableWebView.OnScrollChangeListener,
    ObservableWebView.OnContentHeightChangedListener {

    private lateinit var webView: ObservableWebView
    private lateinit var scrollerView: PageScrollerView
    private lateinit var tocListView: ListView
    private lateinit var sidePanelContainer: View

    private val tocAdapter = TocAdapter(activity)
    private val sectionYOffsets = SparseIntArray()
    private var currentHighlightedPosition = 0
    private var isInitialized = false

    fun initViews() {
        sidePanelContainer = drawerLayout.findViewById(R.id.side_panel_container)
        tocListView = drawerLayout.findViewById(R.id.toc_list_view)
        scrollerView = drawerLayout.findViewById(R.id.page_scroller_view)

        val fragment = activity.supportFragmentManager.findFragmentByTag(PageActivity.FRAGMENT_TAG) as? PageFragment
        fragment?.view?.let { fragmentView ->
            webView = fragmentView.findViewById(R.id.page_web_view)
            webView.addOnScrollChangeListener(this)
            webView.addOnContentHeightChangedListener(this)

            scrollerView.callback = ScrollerCallback()

            tocListView.adapter = tocAdapter
            tocListView.divider = null
            tocListView.dividerHeight = 0
            tocListView.setOnItemClickListener { _, _, position, _ ->
                val section = tocAdapter.getItem(position) as Section
                scrollToSection(section)
                hide()
            }
            isInitialized = true
        } ?: L.e("Could not initialize ContentsHandler views because PageFragment was not found.")
    }

    fun setup(sections: List<Section>) {
        if (!isInitialized) return
        tocAdapter.setSections(sections)
        scrollerView.visibility = if (sections.size > 1) View.VISIBLE else View.GONE
        webView.postDelayed({ fetchSectionOffsets() }, 500)
    }

    fun show() {
        if (!isInitialized) return
        drawerLayout.openDrawer(sidePanelContainer)
    }

    fun hide() {
        if (!isInitialized) return
        drawerLayout.closeDrawer(sidePanelContainer)
    }

    fun isVisible(): Boolean {
        if (!isInitialized) return false
        return drawerLayout.isDrawerOpen(sidePanelContainer)
    }

    private fun scrollToSection(section: Section) {
        if (!isInitialized) return
        if (section.isLead) {
            webView.scrollTo(0, 0)
        } else {
            val script = "document.getElementById('${section.anchor}').scrollIntoView();"
            webView.evaluateJavascript(script, null)
        }
    }

    private fun fetchSectionOffsets() {
        if (!isInitialized) return
        val script = """
            (function() {
                var offsets = {};
                var headerSpans = document.querySelectorAll('.mw-headline');
                for (var i = 0; i < headerSpans.length; i++) {
                    var span = headerSpans[i];
                     if (span.id && (span.parentElement.tagName === 'H2' || span.parentElement.tagName === 'H3')) {
                         offsets[span.id] = span.parentElement.offsetTop;
                     }
                }
                return JSON.stringify(offsets);
            })();
        """
        webView.evaluateJavascript(script) { jsonString ->
            if (jsonString != null && jsonString != "null") {
                try {
                    val cleanedJson = jsonString.removeSurrounding("\"").replace("\\\"", "\"")
                    val json = Json.parseToJsonElement(cleanedJson).jsonObject
                    for (key in json.keys) {
                        val yOffset = json[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        sectionYOffsets.put(key.hashCode(), yOffset)
                    }
                } catch (e: Exception) {
                    L.e("Failed to parse section offsets JSON", e)
                }
            }
        }
    }

    private fun setScrollerPosition() {
        if (!isInitialized) return
        val contentHeight = webView.contentHeight * DimenUtil.densityScalar
        if (contentHeight == 0f) return

        val scrollProportion = webView.scrollY / contentHeight
        val availableHeight = webView.height - scrollerView.height
        val newTopMargin = (scrollProportion * availableHeight).toInt()

        scrollerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = newTopMargin.coerceIn(0, availableHeight)
        }
    }

    private fun updateTocHighlight(scrollY: Int) {
        if (!isInitialized) return
        val scrollWithOffset = scrollY + (webView.height / 4)
        var newHighlightedPosition = 0
        (0 until tocAdapter.count).forEach { i ->
            val section = tocAdapter.getItem(i) as Section
            if (section.isLead) return@forEach

            val sectionY = sectionYOffsets.get(section.anchor.hashCode(), 0) * DimenUtil.densityScalar
            if (scrollWithOffset >= sectionY) {
                newHighlightedPosition = i
            } else {
                return@forEach
            }
        }

        if (currentHighlightedPosition != newHighlightedPosition) {
            currentHighlightedPosition = newHighlightedPosition
            tocAdapter.setHighlightedItem(currentHighlightedPosition)
            tocListView.smoothScrollToPosition(currentHighlightedPosition)
        }
    }

    override fun onScrollChanged(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean) {
        if (!activity.isDestroyed && isInitialized) {
            setScrollerPosition()
            updateTocHighlight(scrollY)
        }
    }

    override fun onContentHeightChanged(contentHeight: Int) {
        if (!activity.isDestroyed && isInitialized) {
            fetchSectionOffsets()
        }
    }

    private inner class ScrollerCallback : PageScrollerView.Callback {
        override fun onVerticalScroll(dy: Float) {
            if (!isInitialized) return
            val contentHeight = webView.contentHeight * DimenUtil.densityScalar
            val scrollRange = contentHeight - webView.height
            if (scrollRange <= 0) return

            val scrollProportion = dy / webView.height
            val scrollBy = (scrollProportion * scrollRange).toInt()
            webView.scrollBy(0, scrollBy)
        }
        override fun onScrollStart() {}
        override fun onScrollStop() {}
    }

    private inner class TocAdapter(private val context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)
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
            val view = convertView ?: inflater.inflate(R.layout.toc_item, parent, false)
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
