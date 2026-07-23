package com.omiyawaki.osrswiki.donate

import android.app.Dialog
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.log.L
import java.math.BigDecimal

class DonationSuccessDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_DONATION_AMOUNT = "donation_amount"
        const val TAG = "DonationSuccessDialogFragment"

        fun newInstance(donationAmount: BigDecimal): DonationSuccessDialogFragment {
            val fragment = DonationSuccessDialogFragment()
            val args = Bundle()
            args.putString(ARG_DONATION_AMOUNT, donationAmount.toString())
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        L.d("DonationSuccessDialogFragment: Creating success dialog")
        
        val donationAmountStr = arguments?.getString(ARG_DONATION_AMOUNT) ?: "0.00"
        val donationAmount = try {
            BigDecimal(donationAmountStr)
        } catch (e: NumberFormatException) {
            BigDecimal.ZERO
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_donation_success, null)
        
        // Set up views
        val successIcon = dialogView.findViewById<ImageView>(R.id.success_icon)
        val successTitle = dialogView.findViewById<TextView>(R.id.success_title)
        val donationAmountView = dialogView.findViewById<TextView>(R.id.donation_amount)
        val successMessage = dialogView.findViewById<TextView>(R.id.success_message)

        // Configure the views
        donationAmountView.text = getString(R.string.donate_success_amount_format, "$$donationAmount")
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                L.d("DonationSuccessDialogFragment: Dialog dismissed")
                dismiss()
            }
            .setCancelable(true)
            .create()

        return dialog
    }

    override fun onStart() {
        super.onStart()
        L.d("DonationSuccessDialogFragment: Dialog started")
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        L.d("DonationSuccessDialogFragment: Dialog dismissed")
        
        // Notify parent fragment that dialog was dismissed
        (parentFragment as? DonateFragment)?.onSuccessDialogDismissed()
    }
}