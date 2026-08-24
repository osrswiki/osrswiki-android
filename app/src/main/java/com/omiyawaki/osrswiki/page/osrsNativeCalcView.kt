package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
) : ScrollView(context, attrs) {
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(16)
        setPadding(pad, pad, pad, dp(96))
    }
    private var resultWeb: WebView? = null
    private val fieldEditors = mutableMapOf<String, EditText>()

    init {
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        isFillViewport = true
        contentDescription = "Native calculator"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun bind(session: osrsNativeCalcSession) {
        contentDescription = session.chromeTitle
        val paper = themeColor(R.attr.paper_color, ContextCompat.getColor(context, R.color.osrs_parchment_light))
        val onPaper = themeColor(com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(context, R.color.osrs_text_dark))
        val secondary = ContextCompat.getColor(
            context,
            if (session.usesDarkTheme) R.color.osrs_text_secondary_dark else R.color.osrs_text_secondary_light
        )
        setBackgroundColor(paper)
        column.setBackgroundColor(paper)
        column.removeAllViews()
        fieldEditors.clear()

        column.addView(text(session.chromeTitle, onPaper, 20f, true).apply {
            id = View.generateViewId()
            setTextAppearance(R.style.AppTextAppearance_TitleBold)
            setTextColor(onPaper)
            contentDescription = session.chromeTitle
        })
        bannerText(session.hiscoresError, session.formError)?.let { message ->
            column.addView(text(message, ContextCompat.getColor(context, R.color.color_error), 14f, false).apply {
                setPadding(0, dp(12), 0, dp(8))
                contentDescription = message
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            })
        }
        if (session.introCopy.isNotBlank()) {
            column.addView(text(session.introCopy, secondary, 15f, false).apply {
                setTextAppearance(R.style.AppTextAppearance_Body)
                setTextColor(secondary)
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        session.visibleInputs().forEach { input ->
            column.addView(text(input.label, onPaper, 14f, true).apply {
                setPadding(0, dp(12), 0, dp(4))
            })
            column.addView(control(session, input, onPaper))
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
        column.addView(submit, linear())
        if (session.statusMessage.isNotBlank()) {
            column.addView(text(session.statusMessage, secondary, 13f, false))
        }
        if (session.resultHtml.isNotBlank()) {
            val web = resultWeb ?: WebView(context).apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "Calculator results"
            }
            resultWeb = web
            if (web.parent != null) {
                (web.parent as? android.view.ViewGroup)?.removeView(web)
            }
            web.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(420))
            column.addView(web)
            web.loadDataWithBaseURL(
                osrsWikiWebViewUrl.WIKI_ORIGIN + "/",
                session.resultDocument,
                "text/html",
                "utf-8",
                null
            )
        }
        if (bannerText(session.hiscoresError, session.formError) != null) {
            post { scrollTo(0, 0) }
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
        val layout = TextInputLayout(context)
        val current = session.values[input.name] ?: input.defaultValue
        val dropdown = MaterialAutoCompleteTextView(context).apply {
            setAdapter(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    input.options
                )
            )
            setText(current, false)
            setTextColor(onPaper)
            inputType = InputType.TYPE_NULL
            keyListener = null
            isFocusable = false
            isCursorVisible = false
            threshold = 1
            contentDescription = "${input.label} menu"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                val selected = input.options.getOrNull(position) ?: return@setOnItemClickListener
                session.setValue(input.name, selected)
            }
        }
        layout.addView(
            dropdown,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        layout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        layout.contentDescription = "${input.label} menu"
        layout.setEndIconOnClickListener { dropdown.showDropDown() }
        return layout
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
}
