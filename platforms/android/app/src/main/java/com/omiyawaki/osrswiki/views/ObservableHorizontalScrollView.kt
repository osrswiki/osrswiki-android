package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class ObservableHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var onReadyListener: (() -> Unit)? = null

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // The first time layout occurs, the view is ready to be scrolled.
        onReadyListener?.invoke()
        onReadyListener = null
    }
}
