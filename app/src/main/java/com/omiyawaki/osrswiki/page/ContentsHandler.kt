package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Typeface
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private val fragment: PageFragment,
    private val drawerLayout: DrawerLayout
) : PageScrollerView.Callback,
    ObservableWebView.OnScrollChangeListener,
    ObservableWebView.OnContentHeightChangedListener {

    private val webView: ObservableWebView = fragment.requireView().findViewById(R.id.page_web_view)
    private val scrollerView: PageScrollerView = fragment.requireView().findViewById(R.id.page_scroller_view)
    private val tocListView: ListView = drawerLayout.findViewById(R.id.toc_list_view)

    private val tocAdapter = TocAdapter(fragment.requireContext())
    private val sectionYOffsets = SparseIntArray()

    init {
        scrollerView.callback = this
        scrollerView.visibility = View.GONE
        tocListView.adapter = tocAdapter
        tocListView.setOnItemClickListener { _, _, position, _ ->
            val section = tocAdapter.getItem(position) as Section
            scrollToSection(section)
            hide()
        }

        webView.addOnScrollChangeListener(this)
        webView.addOnContentHeightChangedListener(this)
    }

    fun setup(sections: List<Section>) {
        tocAdapter.setSections(sections)
        scrollerView.visibility = if (sections.size > 1) View.VISIBLE else View.GONE
        webView.postDelayed({ fetchSectionOffsets() }, 500)
    }

    fun show() {
        drawerLayout.openDrawer(tocListView)
    }

    fun hide() {
        drawerLayout.closeDrawer(tocListView)
    }

    fun isVisible(): Boolean {
        return drawerLayout.isDrawerOpen(tocListView)
    }

    private fun scrollToSection(section: Section) {
        if (section.isLead) {
            webView.scrollTo(0, 0)
        } else {
            val script = "document.getElementById('${section.anchor}').scrollIntoView();"
            webView.evaluateJavascript(script, null)
        }
    }

    private fun fetchSectionOffsets() {
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

    private fun setScrollerPosition(scrollY: Int) {
        val contentHeight = webView.contentHeight * DimenUtil.densityScalar
        if (contentHeight == 0f) return

        val scrollProportion = scrollY / contentHeight
        val availableHeight = webView.height - scrollerView.height
        val newTopMargin = (scrollProportion * availableHeight).toInt()

        val params = scrollerView.layoutParams as? ViewGroup.MarginLayoutParams
        params?.let {
            it.topMargin = newTopMargin
            scrollerView.layoutParams = it
        }
    }

    private fun updateTocHighlight(scrollY: Int) {
        val scrollWithOffset = scrollY + (webView.height / 4)
        var currentSectionIndex = 0
        (0 until tocAdapter.count).forEach { i ->
            val section = tocAdapter.getItem(i) as Section
            if (section.isLead) return@forEach

            val sectionY = sectionYOffsets.get(section.anchor.hashCode(), 0) * DimenUtil.densityScalar
            if (scrollWithOffset > sectionY) {
                currentSectionIndex = i
            } else {
                return@forEach
            }
        }
        tocAdapter.setHighlightedItem(currentSectionIndex)
    }

    override fun onScrollStart() {
        show()
    }

    override fun onScrollStop() {
        hide()
    }

    override fun onVerticalScroll(dy: Float) {
        val contentHeight = webView.contentHeight * DimenUtil.densityScalar
        val scrollRange = contentHeight - webView.height
        if (scrollRange <= 0) return

        val scrollProportion = dy / webView.height
        val scrollBy = (scrollProportion * scrollRange).toInt()
        webView.scrollBy(0, scrollBy)
    }

    override fun onScrollChanged(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean) {
        if (!fragment.isAdded) return
        setScrollerPosition(scrollY)
        updateTocHighlight(scrollY)
    }

    override fun onContentHeightChanged(contentHeight: Int) {
        if (!fragment.isAdded) return
        fetchSectionOffsets()
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
                tocListView.smoothScrollToPosition(position)
            }
        }

        override fun getCount(): Int = adapterSections.size
        override fun getItem(position: Int): Any = adapterSections[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.toc_item, parent, false)
            val section = getItem(position) as Section
            val textView = view.findViewById<TextView>(R.id.toc_item_text)

            val indentation = (section.level - 1) * 16
            textView.setPadding(DimenUtil.roundedDpToPx(indentation.toFloat()), textView.paddingTop, textView.paddingRight, textView.paddingBottom)
            textView.text = section.title

            val typedValue = TypedValue()
            if (position == highlightedPosition) {
                textView.typeface = Typeface.DEFAULT_BOLD
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
                textView.setTextColor(typedValue.data)
            } else {
                textView.typeface = Typeface.DEFAULT
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                textView.setTextColor(typedValue.data)
            }
            return view
        }
    }
}
