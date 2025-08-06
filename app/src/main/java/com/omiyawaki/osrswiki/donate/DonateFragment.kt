package com.omiyawaki.osrswiki.donate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.google.android.material.chip.Chip
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentDonateBinding
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyRubikUILabel
import com.omiyawaki.osrswiki.util.applyRubikUIButton
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

class DonateFragment : Fragment() {

    private var _binding: FragmentDonateBinding? = null
    private val binding get() = _binding!!
    
    private var selectedAmount: BigDecimal? = null
    private var isCustomAmountSelected = false
    
    // Google Pay
    private lateinit var paymentsClient: PaymentsClient
    private var googlePayIsReady = false

    companion object {
        fun newInstance() = DonateFragment()
        const val TAG = "DonateFragment"
        
        // Google Pay constants
        private const val LOAD_PAYMENT_DATA_REQUEST_CODE = 991
        private const val GOOGLE_PAY_ENVIRONMENT = WalletConstants.ENVIRONMENT_TEST // Change to PRODUCTION for live
        
        // Wiki donation URL
        private const val WIKI_PATREON_URL = "https://www.patreon.com/runescapewiki"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("DonateFragment: onCreateView called.")
        _binding = FragmentDonateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("DonateFragment: onViewCreated called.")
        
        setupFonts()
        initializeGooglePay()
        setupAmountSelection()
        setupDonateButton()
        setupCustomAmountInput()
        setupWikiDonateButton()
    }
    
    private fun setupAmountSelection() {
        // Set up preset amount chips
        binding.chipAmount1.setOnClickListener { selectPresetAmount(BigDecimal("1.00")) }
        binding.chipAmount5.setOnClickListener { selectPresetAmount(BigDecimal("5.00")) }
        binding.chipAmount10.setOnClickListener { selectPresetAmount(BigDecimal("10.00")) }
        binding.chipAmount25.setOnClickListener { selectPresetAmount(BigDecimal("25.00")) }
        
        // Set up custom amount chip
        binding.chipAmountCustom.setOnClickListener { 
            selectCustomAmount()
        }
    }
    
    private fun selectPresetAmount(amount: BigDecimal) {
        L.d("DonateFragment: Preset amount selected: $amount")
        selectedAmount = amount
        isCustomAmountSelected = false
        
        // Hide custom amount input
        binding.customAmountLayout.visibility = View.GONE
        binding.customAmountInput.text?.clear()
        
        // Update button selection states
        updateButtonSelectionStates()
        updateDonateButtonState()
    }
    
    private fun selectCustomAmount() {
        L.d("DonateFragment: Custom amount selected")
        isCustomAmountSelected = true
        selectedAmount = null
        
        // Show custom amount input
        binding.customAmountLayout.visibility = View.VISIBLE
        binding.customAmountInput.requestFocus()
        
        // Update button selection states
        updateButtonSelectionStates()
        updateDonateButtonState()
    }
    
    private fun updateButtonSelectionStates() {
        // Clear all button selections first
        clearAllButtonSelections()
        
        // Set selected state based on current selection
        when {
            isCustomAmountSelected -> {
                binding.chipAmountCustom.isSelected = true
            }
            selectedAmount != null -> {
                when (selectedAmount) {
                    BigDecimal("1.00") -> binding.chipAmount1.isSelected = true
                    BigDecimal("5.00") -> binding.chipAmount5.isSelected = true
                    BigDecimal("10.00") -> binding.chipAmount10.isSelected = true
                    BigDecimal("25.00") -> binding.chipAmount25.isSelected = true
                }
            }
        }
    }
    
    private fun clearAllButtonSelections() {
        binding.chipAmount1.isSelected = false
        binding.chipAmount5.isSelected = false
        binding.chipAmount10.isSelected = false
        binding.chipAmount25.isSelected = false
        binding.chipAmountCustom.isSelected = false
    }
    
    private fun setupCustomAmountInput() {
        binding.customAmountInput.addTextChangedListener { text ->
            if (isCustomAmountSelected) {
                val amountStr = text?.toString()?.trim()
                selectedAmount = if (!amountStr.isNullOrEmpty()) {
                    try {
                        val amount = BigDecimal(amountStr)
                        if (amount > BigDecimal.ZERO) amount else null
                    } catch (e: NumberFormatException) {
                        null
                    }
                } else {
                    null
                }
                updateDonateButtonState()
            }
        }
    }
    
    private fun setupDonateButton() {
        binding.donateButton.setOnClickListener {
            selectedAmount?.let { amount ->
                L.d("DonateFragment: Donate button clicked with amount: $amount")
                initiatePayment(amount)
            }
        }
    }
    
    private fun updateDonateButtonState() {
        val hasValidAmount = selectedAmount != null && selectedAmount!! > BigDecimal.ZERO
        binding.donateButton.isEnabled = hasValidAmount && googlePayIsReady
        
        if (hasValidAmount) {
            binding.donateButton.text = getString(R.string.donate_button_text) + " ($$selectedAmount)"
        } else {
            binding.donateButton.text = getString(R.string.donate_button_text)
        }
    }
    
    private fun initiatePayment(amount: BigDecimal) {
        L.d("DonateFragment: Initiating payment for amount: $amount")
        setStatusText(getString(R.string.donate_processing))
        
        val paymentDataRequestJson = createPaymentDataRequest(amount)
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())
        
        if (request != null) {
            AutoResolveHelper.resolveTask(
                paymentsClient.loadPaymentData(request),
                requireActivity(),
                LOAD_PAYMENT_DATA_REQUEST_CODE
            )
        } else {
            L.e("DonateFragment: Error creating payment data request")
            onPaymentError("Failed to create payment request")
        }
    }
    
    private fun setStatusText(text: String, isVisible: Boolean = true) {
        binding.statusText.text = text
        binding.statusText.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
    
    private fun hideStatusText() {
        binding.statusText.visibility = View.GONE
    }
    
    // Google Pay Methods
    private fun initializeGooglePay() {
        L.d("DonateFragment: Initializing Google Pay")
        
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(GOOGLE_PAY_ENVIRONMENT)
            .build()
        
        paymentsClient = Wallet.getPaymentsClient(requireActivity(), walletOptions)
        
        checkGooglePayAvailability()
    }
    
    private fun checkGooglePayAvailability() {
        val isReadyToPayJson = createIsReadyToPayRequestJson()
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString())
        
        paymentsClient.isReadyToPay(request)
            .addOnCompleteListener { task ->
                try {
                    val result = task.getResult(ApiException::class.java)
                    googlePayIsReady = result ?: false
                    L.d("DonateFragment: Google Pay availability: $googlePayIsReady")
                    
                    if (!googlePayIsReady) {
                        setStatusText(getString(R.string.donate_google_pay_unavailable))
                        binding.donateButton.isEnabled = false
                    } else {
                        hideStatusText()
                    }
                } catch (exception: ApiException) {
                    L.e("DonateFragment: Error checking Google Pay availability", exception)
                    googlePayIsReady = false
                    setStatusText(getString(R.string.donate_google_pay_unavailable))
                    binding.donateButton.isEnabled = false
                }
            }
    }
    
    private fun createIsReadyToPayRequestJson(): JSONObject {
        return try {
            JSONObject().apply {
                put("apiVersion", 2)
                put("apiVersionMinor", 0)
                put("allowedPaymentMethods", JSONArray().put(createCardPaymentMethod()))
            }
        } catch (e: JSONException) {
            JSONObject()
        }
    }
    
    private fun createCardPaymentMethod(): JSONObject {
        return JSONObject().apply {
            put("type", "CARD")
            
            val parameters = JSONObject().apply {
                put("allowedAuthMethods", JSONArray().apply {
                    put("PAN_ONLY")
                    put("CRYPTOGRAM_3DS")
                })
                put("allowedCardNetworks", JSONArray().apply {
                    put("AMEX")
                    put("DISCOVER")
                    put("MASTERCARD")
                    put("VISA")
                })
            }
            put("parameters", parameters)
        }
    }
    
    private fun createPaymentDataRequest(amount: BigDecimal): JSONObject {
        return JSONObject().apply {
            put("apiVersion", 2)
            put("apiVersionMinor", 0)
            
            // Allowed payment methods
            put("allowedPaymentMethods", JSONArray().put(createCardPaymentMethodForPayment()))
            
            // Transaction info
            val transactionInfo = JSONObject().apply {
                put("totalPrice", amount.toString())
                put("totalPriceStatus", "FINAL")
                put("currencyCode", "USD")
            }
            put("transactionInfo", transactionInfo)
            
            // Merchant info
            val merchantInfo = JSONObject().apply {
                put("merchantName", "OSRS Wiki")
                put("merchantId", "BCR2DN4T2TUUAOLG") // Test merchant ID - replace with real one
            }
            put("merchantInfo", merchantInfo)
        }
    }
    
    private fun createCardPaymentMethodForPayment(): JSONObject {
        return JSONObject().apply {
            put("type", "CARD")
            
            val parameters = JSONObject().apply {
                put("allowedAuthMethods", JSONArray().apply {
                    put("PAN_ONLY")
                    put("CRYPTOGRAM_3DS")
                })
                put("allowedCardNetworks", JSONArray().apply {
                    put("AMEX")
                    put("DISCOVER")
                    put("MASTERCARD")
                    put("VISA")
                })
            }
            put("parameters", parameters)
            
            // Tokenization specification
            val tokenizationSpec = JSONObject().apply {
                put("type", "PAYMENT_GATEWAY")
                val parameters = JSONObject().apply {
                    put("gateway", "example") // Replace with actual payment processor
                    put("gatewayMerchantId", "exampleGatewayMerchantId") // Replace with actual merchant ID
                }
                put("parameters", parameters)
            }
            put("tokenizationSpecification", tokenizationSpec)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        data?.let { intent ->
                            PaymentData.getFromIntent(intent)?.let { paymentData ->
                                handlePaymentSuccess(paymentData)
                            }
                        }
                    }
                    Activity.RESULT_CANCELED -> {
                        L.d("DonateFragment: Payment cancelled by user")
                        onPaymentCancelled()
                    }
                    AutoResolveHelper.RESULT_ERROR -> {
                        AutoResolveHelper.getStatusFromIntent(data)?.let { status ->
                            L.e("DonateFragment: Payment error: ${status.statusMessage}")
                            onPaymentError(status.statusMessage ?: "Payment failed")
                        }
                    }
                }
            }
        }
    }
    
    private fun handlePaymentSuccess(paymentData: PaymentData) {
        L.d("DonateFragment: Payment successful")
        
        // In a real app, you would send the payment token to your server for processing
        val paymentToken = paymentData.toJson()
        L.d("DonateFragment: Payment token received: $paymentToken")
        
        // For now, just show success message
        onPaymentSuccess()
    }
    
    private fun onPaymentSuccess() {
        hideStatusText()
        setStatusText(getString(R.string.donate_success_message))
        binding.donateButton.text = getString(R.string.donate_success_title)
        binding.donateButton.isEnabled = false
        
        // Reset form after delay
        binding.root.postDelayed({
            resetForm()
        }, 3000)
    }
    
    private fun onPaymentError(message: String) {
        hideStatusText()
        setStatusText("${getString(R.string.donate_error_message)}: $message")
        binding.donateButton.isEnabled = selectedAmount != null && googlePayIsReady
    }
    
    private fun onPaymentCancelled() {
        hideStatusText()
        binding.donateButton.isEnabled = selectedAmount != null && googlePayIsReady
    }
    
    private fun resetForm() {
        selectedAmount = null
        isCustomAmountSelected = false
        clearAllButtonSelections()
        binding.customAmountLayout.visibility = View.GONE
        binding.customAmountInput.text?.clear()
        hideStatusText()
        updateDonateButtonState()
    }
    
    private fun setupWikiDonateButton() {
        binding.wikiDonateButton.setOnClickListener {
            L.d("DonateFragment: Wiki donate button clicked")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WIKI_PATREON_URL))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                L.e("DonateFragment: Error opening Patreon URL", e)
                setStatusText("Unable to open browser")
            }
        }
    }
    
    private fun setupFonts() {
        L.d("DonateFragment: Setting up fonts...")
        
        // Apply fonts to all TextViews
        binding.donateTitle.applyAlegreyaHeadline()
        binding.donateDescription.applyRubikUILabel()
        binding.statusText.applyRubikUILabel()
        binding.wikiSupportTitle.applyAlegreyaHeadline()
        binding.wikiSupportDescription.applyRubikUILabel()
        
        // Apply fonts to chips (amount selection)
        binding.chipAmount1.applyRubikUIButton()
        binding.chipAmount5.applyRubikUIButton()
        binding.chipAmount10.applyRubikUIButton()
        binding.chipAmount25.applyRubikUIButton()
        binding.chipAmountCustom.applyRubikUIButton()
        
        // Apply fonts to buttons
        binding.donateButton.applyRubikUIButton()
        binding.wikiDonateButton.applyRubikUIButton()
        
        L.d("DonateFragment: Fonts applied to all TextViews, chips, and buttons")
    }

    override fun onDestroyView() {
        L.d("DonateFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}