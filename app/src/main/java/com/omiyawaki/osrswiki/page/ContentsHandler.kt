package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.google.android.material.color.MaterialColors
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.util.DimenUtil
import com.omiyawaki.osrswiki.util.FontUtil
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.view.PageScrollerView
import com.omiyawaki.osrswiki.views.ObservableWebView

class ContentsHandler(private val fragment: PageFragment) :
    ObservableWebView.OnScrollChangeListener {

    private val activity = fragment.requireActivity() as PageActivity
    private val binding = activity.binding
    private val webView = fragment.binding.pageWebView
    private val scrollerView = binding.pageScrollerView
    private val tocListView = binding.tocListView
    private val drawerHost = binding.pageDrawerLayout
    private val tocAdapter = TocAdapter(activity)
    private var isInitialized = false

    init {
        tocListView.adapter = tocAdapter
        tocListView.setOnItemClickListener { _, _, position, _ ->
            val section = tocAdapter.getItem(position) as Section
            L.d("ContentsHandler: selected ${section.anchor} '${section.title}'")
            scrollToSection(section)
            hide()
        }
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
        activity.openContents()
    }

    fun hide() {
        if (!isInitialized) return
        activity.closeContents(animate = true)
    }

    fun isVisible(): Boolean {
        if (!isInitialized) return false
        return activity.isContentsDrawerOpen()
    }

    private fun scrollToSection(section: Section) {
        if (!isInitialized) return
        if (section.isLead) {
            webView.scrollTo(0, 0)
        } else {
            val script = osrsArticleSectionScroll.javaScript(section.anchor)
            webView.evaluateJavascript(script) { raw ->
                val viewportCss = raw?.trim()?.trim('"')?.toFloatOrNull()
                if (viewportCss == null || viewportCss.isNaN()) {
                    L.d("ContentsHandler: no heading offset for ${section.anchor} raw=$raw")
                    return@evaluateJavascript
                }
                val scale = webView.scale.takeIf { it > 0f } ?: DimenUtil.densityScalar
                val y = (webView.scrollY + viewportCss * scale).toInt().coerceAtLeast(0)
                L.d(
                    "ContentsHandler: ${section.anchor} raw=$raw css=$viewportCss " +
                        "scrollY=${webView.scrollY} scale=$scale -> $y"
                )
                webView.post { webView.scrollTo(0, y) }
            }
        }
    }

    override fun onScrollChanged(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean) {
        if (isInitialized) {
            setScrollerPosition()
        }
    }

    private fun setScrollerPosition() {
        val webViewContentHeight = webView.contentHeight * DimenUtil.densityScalar
        if (webViewContentHeight == 0f) return
        val availableScrollHeight = (webViewContentHeight - webView.height).coerceAtLeast(0f)
        val scrollProportion = webView.scrollY / availableScrollHeight
        val availableScrollerHeight = (drawerHost.height - scrollerView.height).coerceAtLeast(0)
        val newTopMargin = (scrollProportion * availableScrollerHeight).toInt()
        scrollerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = newTopMargin.coerceIn(0, availableScrollerHeight)
        }
    }

    private fun onScrollerMoved(dy: Float) {
        val webViewContentHeight = webView.contentHeight * DimenUtil.densityScalar
        if (webViewContentHeight == 0f) return
        val availableScrollHeight = (webViewContentHeight - webView.height)
        val availableScrollerHeight = (drawerHost.height - scrollerView.height).toFloat()
        if (availableScrollerHeight <= 0f) return
        val scrollBy = dy * (availableScrollHeight / availableScrollerHeight)
        webView.scrollBy(0, scrollBy.toInt())
    }

    private inner class ScrollerCallback : PageScrollerView.Callback {
        override fun onScrollStart() {}
        override fun onScrollStop() {}
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

            // Apply consistent font instead of manual typeface creation
            when {
                section.isLead -> {
                    FontUtil.applyAlegreyaDisplay(textView)
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                }
                section.level == 2 -> {
                    FontUtil.applyAlegreyaTitle(textView)
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                }
                else -> {
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                }
            }

            val isHighlighted = position == highlightedPosition
            val colorAttr = if (isHighlighted) {
                com.google.android.material.R.attr.colorOnSurface
            } else {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            val resolvedColor = MaterialColors.getColor(context, colorAttr, Color.BLACK)
            textView.setTextColor(resolvedColor)
            bullet.imageTintList = ColorStateList.valueOf(resolvedColor)

            return view
        }
    }
}
