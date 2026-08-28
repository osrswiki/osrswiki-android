package com.omiyawaki.osrswiki.page

import android.content.Context
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Filter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.omiyawaki.osrswiki.R

class osrsNativeCalcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val fieldEditors = mutableMapOf<String, EditText>()
    private val errorBanner = TextView(context).apply {
        id = R.id.native_calc_error
        visibility = GONE
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(0, dp(8), 0, dp(8))
        minHeight = dp(24)
    }
    private val form = LinearLayout(context).apply {
        id = R.id.native_calc_form
        orientation = VERTICAL
    }
    // Frame, not an inner vertical scroller: the article owns vertical scroll.
    // Keep the overflow id so collapse still hides the chrome.
    private val overflow = FrameLayout(context).apply {
        id = R.id.native_calc_overflow
        isClickable = false
        isFocusable = false
        clipToPadding = true
        clipChildren = false
    }
    var collapsed: Boolean = false
        private set
    var onCollapsedChange: ((Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        isClickable = false
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)
        clipToPadding = true
        clipChildren = false
        form.isClickable = false
        overflow.addView(
            form,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        addView(overflow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        contentDescription = "calculator"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setCollapsed(value: Boolean) {
        val changed = collapsed != value
        collapsed = value
        overflow.visibility = if (value) GONE else VISIBLE
        if (changed) onCollapsedChange?.invoke(value)
    }

    fun bind(session: osrsNativeCalcSession) {
        val paper = themeColor(R.attr.paper_color, ContextCompat.getColor(context, R.color.osrs_parchment_light))
        val onPaper = themeColor(com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(context, R.color.osrs_text_dark))
        val secondary = ContextCompat.getColor(
            context,
            if (session.usesDarkTheme) R.color.osrs_text_secondary_dark else R.color.osrs_text_secondary_light
        )
        setBackgroundColor(paper)
        bindError(session)
        form.removeAllViews()
        form.addView(errorBanner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        fieldEditors.clear()
        session.visibleInputs().forEach { input ->
            form.addView(text(input.label, onPaper, 14f, true).apply {
                setPadding(0, dp(12), 0, dp(4))
                tag = "native-calc-label-${input.name}"
                contentDescription = input.label
                isClickable = false
            })
            form.addView(control(session, input, onPaper))
        }
        val submit = MaterialButton(context).apply {
            text = "Submit"
            setOnClickListener {
                commitFocusedFields(session)
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(windowToken, 0)
                clearFocus()
                session.submitNow()
            }
            contentDescription = "Submit calculator"
        }
        form.addView(submit, linear())
        if (session.statusMessage.isNotBlank()) {
            form.addView(text(session.statusMessage, secondary, 13f, false))
        }
        setCollapsed(collapsed)
    }

    private fun bindError(session: osrsNativeCalcSession) {
        val message = bannerText(session.hiscoresError, session.formError)
        if (message.isNullOrBlank()) {
            errorBanner.visibility = GONE
            errorBanner.text = ""
            errorBanner.contentDescription = null
        } else {
            errorBanner.setTextColor(ContextCompat.getColor(context, R.color.color_error))
            errorBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            errorBanner.text = message
            errorBanner.contentDescription = message
            errorBanner.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            errorBanner.visibility = VISIBLE
        }
    }

    private fun control(
        session: osrsNativeCalcSession,
        input: osrsNativeCalcDefinition.Input,
        onPaper: Int
    ): View {
        return when (input.type) {
            osrsNativeCalcDefinition.ParamType.HS,
            osrsNativeCalcDefinition.ParamType.RSN,
            osrsNativeCalcDefinition.ParamType.STRING -> {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                val field = edit(session, input, onPaper)
                row.addView(field, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                if (input.type == osrsNativeCalcDefinition.ParamType.HS) {
                    row.addView(MaterialButton(context).apply {
                        text = "Lookup"
                        contentDescription = "Lookup hiscores"
                        setOnClickListener {
                            session.setValue(input.name, field.text.toString(), submit = false)
                            field.clearFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                as? android.view.inputmethod.InputMethodManager
                            imm?.hideSoftInputFromWindow(windowToken, 0)
                            session.lookupHiscores()
                        }
                    }, linear())
                }
                row
            }
            osrsNativeCalcDefinition.ParamType.INT,
            osrsNativeCalcDefinition.ParamType.NUMBER -> stepper(session, input, onPaper)
            osrsNativeCalcDefinition.ParamType.SELECT -> picker(session, input, onPaper)
            osrsNativeCalcDefinition.ParamType.BUTTON_SELECT -> chips(session, input)
            osrsNativeCalcDefinition.ParamType.TOGGLE_SWITCH,
            osrsNativeCalcDefinition.ParamType.TOGGLE_BUTTON,
            osrsNativeCalcDefinition.ParamType.CHECK -> SwitchCompat(context).apply {
                isChecked = session.values[input.name].equals("true", true)
                setOnCheckedChangeListener { _, checked ->
                    session.setValue(input.name, if (checked) "true" else "false")
                }
            }
            else -> View(context)
        }
    }

    private fun stepper(
        session: osrsNativeCalcSession,
        input: osrsNativeCalcDefinition.Input,
        onPaper: Int
    ): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val minus = MaterialButton(context).apply {
            text = "−"
            setOnClickListener { session.step(input.name, -1) }
            contentDescription = "Decrease ${input.label}"
        }
        val field = edit(session, input, onPaper).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            if (Build.VERSION.SDK_INT >= 21) {
                showSoftInputOnFocus = true
            }
            gravity = android.view.Gravity.CENTER
        }
        val plus = MaterialButton(context).apply {
            text = "+"
            setOnClickListener { session.step(input.name, 1) }
            contentDescription = "Increase ${input.label}"
        }
        row.addView(minus, linear())
        row.addView(field, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(plus, linear())
        return row
    }

    private fun picker(
        session: osrsNativeCalcSession,
        input: osrsNativeCalcDefinition.Input,
        onPaper: Int
    ): View {
        val current = session.values[input.name] ?: input.defaultValue
        val adapter = UnfilteredArrayAdapter(context, input.options)
        val hidden = MaterialAutoCompleteTextView(context).apply {
            setAdapter(adapter)
            setText(current, false)
            threshold = Int.MAX_VALUE
            visibility = GONE
            contentDescription = "${input.label} adapter"
        }
        val optionsList = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            tag = "native-calc-options-${input.name}"
        }
        val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = current
            setTextColor(onPaper)
            contentDescription = "${input.label} menu"
            tag = input.options
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            isClickable = true
        }
        button.setOnClickListener {
            if (optionsList.visibility == VISIBLE) {
                optionsList.visibility = GONE
                onCollapsedChange?.invoke(collapsed)
                return@setOnClickListener
            }
            optionsList.removeAllViews()
            input.options.forEach { option ->
                optionsList.addView(
                    MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = option
                        setTextColor(onPaper)
                        contentDescription = option
                        tag = "native-calc-option-$option"
                        isFocusable = true
                        isClickable = true
                        setOnClickListener {
                            button.text = option
                            hidden.setText(option, false)
                            session.setValue(input.name, option)
                            optionsList.visibility = GONE
                            onCollapsedChange?.invoke(collapsed)
                        }
                    },
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                )
            }
            optionsList.visibility = VISIBLE
            onCollapsedChange?.invoke(collapsed)
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            addView(hidden, LayoutParams(0, 0))
            addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(optionsList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun chips(session: osrsNativeCalcSession, input: osrsNativeCalcDefinition.Input): View {
        val group = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val current = session.values[input.name] ?: input.defaultValue
        input.options.forEach { option ->
            val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = option
                isCheckable = true
                isChecked = option == current
                setOnClickListener { session.setValue(input.name, option) }
            }
            group.addView(button)
        }
        return group
    }

    private fun edit(
        session: osrsNativeCalcSession,
        input: osrsNativeCalcDefinition.Input,
        onPaper: Int
    ): EditText {
        return EditText(context).apply {
            setText(session.values[input.name] ?: input.defaultValue)
            setTextColor(onPaper)
            setHintTextColor(onPaper)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            if (Build.VERSION.SDK_INT >= 21) {
                showSoftInputOnFocus = true
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) session.setValue(input.name, text.toString(), submit = input.type != osrsNativeCalcDefinition.ParamType.HS && input.type != osrsNativeCalcDefinition.ParamType.RSN && input.type != osrsNativeCalcDefinition.ParamType.STRING)
            }
            fieldEditors[input.name] = this
        }
    }

    private fun commitFocusedFields(session: osrsNativeCalcSession) {
        fieldEditors.forEach { (name, editor) ->
            session.setValue(name, editor.text.toString(), submit = false)
        }
    }

    private fun bannerText(hiscores: String?, form: String?): String? {
        return hiscores?.takeIf { it.isNotBlank() } ?: form?.takeIf { it.isNotBlank() }
    }

    private fun text(value: String, color: Int, size: Float, bold: Boolean): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            isClickable = false
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val typed = TypedValue()
        return if (context.theme.resolveAttribute(attr, typed, true)) typed.data else fallback
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun linear() = LinearLayout.LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT
    ).apply { marginStart = dp(6) }

    private class UnfilteredArrayAdapter(
        context: Context,
        private val items: List<String>
    ) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): String = items[position]
        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = items
                    count = items.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }
        }
    }
}
