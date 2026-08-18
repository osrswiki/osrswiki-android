package com.omiyawaki.osrswiki.ui.common

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omiyawaki.osrswiki.R

/**
 * One Material dialog constructor for every confirmation surface.
 *
 * Keep the builder on the activity theme (AppCompat). Dialog paper, title
 * size, and button contrast are applied after `show()` so we never wrap the
 * context in a ThemeOverlay that ThemeEnforcement rejects.
 */
object ThemedAlertDialogs {
    fun builder(context: Context): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(context)
    }

    fun show(builder: MaterialAlertDialogBuilder): AlertDialog {
        val dialog = builder.show()
        applyChrome(dialog)
        return dialog
    }

    fun applyChrome(dialog: AlertDialog) {
        val context = dialog.context
        val typed = context.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorOnSurface,
                R.attr.paper_color,
                com.google.android.material.R.attr.colorSurface
            )
        )
        val onSurface = typed.getColor(0, 0xFF111111.toInt())
        val paper = typed.getColor(1, typed.getColor(2, onSurface))
        typed.recycle()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(onSurface)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(onSurface)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(onSurface)
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            textSize = 18f
            setTextColor(onSurface)
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(paper))
    }
}
