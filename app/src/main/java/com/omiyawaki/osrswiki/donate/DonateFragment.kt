package com.omiyawaki.osrswiki.donate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentDonateBinding
import com.omiyawaki.osrswiki.util.log.L
import java.math.BigDecimal

class DonateFragment : Fragment() {

    private var _binding: FragmentDonateBinding? = null
    private val binding get() = _binding!!
    
    private var selectedAmount: BigDecimal? = null
    
    private lateinit var billingGateway: DonationBillingGateway
    private var isConnected = false
    private var selectedProductId: String? = null
    private var availableProductIds = emptySet<String>()
    private var acceptsBillingCallbacks = false

    companion object {
        fun newInstance() = DonateFragment()
        const val TAG = "DonateFragment"
        
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
        
        setupAmountSelection()
        setupDonateButton()
        setupWikiDonateButton()
        updateAmountButtonAvailability()
        updateDonateButtonState()
        acceptsBillingCallbacks = true
        initializeBilling()
    }
    
    private fun setupAmountSelection() {
        // Set up preset amount chips
        binding.chipAmount1.setOnClickListener { selectPresetAmount(BigDecimal("1.00"), DonationProductIds.DONATE_1) }
        binding.chipAmount5.setOnClickListener { selectPresetAmount(BigDecimal("5.00"), DonationProductIds.DONATE_5) }
        binding.chipAmount10.setOnClickListener { selectPresetAmount(BigDecimal("10.00"), DonationProductIds.DONATE_10) }
        binding.chipAmount25.setOnClickListener { selectPresetAmount(BigDecimal("25.00"), DonationProductIds.DONATE_25) }
        
    }
    
    private fun selectPresetAmount(amount: BigDecimal, productId: String) {
        if (!isDonationProductAvailable(productId)) {
            L.d("DonateFragment: Ignoring unavailable preset amount: $amount, productId: $productId")
            return
        }

        L.d("DonateFragment: Preset amount selected: $amount, productId: $productId")
        selectedAmount = amount
        selectedProductId = productId
        
        // Update button selection states
        updateButtonSelectionStates()
        updateDonateButtonState()
    }
    
    private fun updateButtonSelectionStates() {
        // Clear all button selections first
        clearAllButtonSelections()
        
        // Set selected state based on current selection
        selectedAmount?.let { amount ->
            when (amount) {
                BigDecimal("1.00") -> binding.chipAmount1.isSelected = true
                BigDecimal("5.00") -> binding.chipAmount5.isSelected = true
                BigDecimal("10.00") -> binding.chipAmount10.isSelected = true
                BigDecimal("25.00") -> binding.chipAmount25.isSelected = true
            }
        }
    }
    
    private fun clearAllButtonSelections() {
        binding.chipAmount1.isSelected = false
        binding.chipAmount5.isSelected = false
        binding.chipAmount10.isSelected = false
        binding.chipAmount25.isSelected = false
    }
    
    
    private fun setupDonateButton() {
        binding.donateButton.setOnClickListener {
            selectedAmount?.let { amount ->
                L.d("DonateFragment: Donate button clicked with amount: $amount")
                initiatePurchase(amount)
            }
        }
    }
    
    private fun updateDonateButtonState() {
        val hasValidAmount = selectedAmount != null && selectedAmount!! > BigDecimal.ZERO
        val productIsAvailable = selectedProductId?.let { it in availableProductIds } == true
        val isDonationEnabled = hasValidAmount && isConnected && productIsAvailable
        setControlActionable(binding.donateButton, isDonationEnabled)
        
        if (hasValidAmount) {
            binding.donateButton.text = getString(R.string.donate_button_text) + " ($$selectedAmount)"
        } else {
            binding.donateButton.text = getString(R.string.donate_button_text)
        }
    }

    private fun updateAmountButtonAvailability() {
        if (selectedProductId?.let { !isDonationProductAvailable(it) } == true) {
            selectedAmount = null
            selectedProductId = null
            clearAllButtonSelections()
        }

        setControlActionable(binding.chipAmount1, isDonationProductAvailable(DonationProductIds.DONATE_1))
        setControlActionable(binding.chipAmount5, isDonationProductAvailable(DonationProductIds.DONATE_5))
        setControlActionable(binding.chipAmount10, isDonationProductAvailable(DonationProductIds.DONATE_10))
        setControlActionable(binding.chipAmount25, isDonationProductAvailable(DonationProductIds.DONATE_25))
    }

    private fun isDonationProductAvailable(productId: String): Boolean {
        return isConnected && productId in availableProductIds
    }

    private fun setControlActionable(view: View, actionable: Boolean) {
        view.isEnabled = actionable
        view.isClickable = actionable
        view.isFocusable = actionable
    }
    
    private fun initiatePurchase(amount: BigDecimal) {
        L.d("DonateFragment: Initiating purchase for amount: $amount")
        setStatusText(getString(R.string.donate_processing))
        
        val productId = when (amount) {
            BigDecimal("1.00") -> DonationProductIds.DONATE_1
            BigDecimal("5.00") -> DonationProductIds.DONATE_5
            BigDecimal("10.00") -> DonationProductIds.DONATE_10
            BigDecimal("25.00") -> DonationProductIds.DONATE_25
            else -> {
                L.e("DonateFragment: Unsupported amount: $amount")
                onPurchaseError("Unsupported donation amount")
                return
            }
        }
        
        if (productId !in availableProductIds) {
            L.e("DonateFragment: Product details not found for: $productId")
            onPurchaseError("Product not available")
            return
        }
        
        val launchResult = billingGateway.launchPurchase(requireActivity(), productId)
        if (!launchResult.isSuccess) {
            L.e("DonateFragment: Failed to launch billing flow: ${launchResult.message}")
            onPurchaseError(launchResult.message ?: "Purchase failed")
        }
    }
    
    private fun setStatusText(text: String, isVisible: Boolean = true) {
        binding.statusText.text = text
        binding.statusText.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
    
    private fun hideStatusText() {
        binding.statusText.visibility = View.GONE
    }
    
    // Google Play Billing Methods
    private fun initializeBilling() {
        L.d("DonateFragment: Initializing Google Play Billing")
        
        billingGateway = DonationBillingGatewayRegistry.factory.create(
            requireContext(),
            object : DonationBillingListener {
                override fun onBillingReady(productIds: Set<String>) {
                    withActiveDonationView {
                        L.d("DonateFragment: Found ${productIds.size} available products")
                        isConnected = true
                        availableProductIds = productIds
                        if (availableProductIds.isEmpty()) {
                            setStatusText("No donation options available")
                        } else {
                            hideStatusText()
                        }
                        updateAmountButtonAvailability()
                        updateDonateButtonState()
                    }
                }

                override fun onBillingSetupFailed(message: String) {
                    withActiveDonationView {
                        L.d("DonateFragment: Billing setup failed: $message")
                        isConnected = false
                        availableProductIds = emptySet()
                        setStatusText("Unable to connect to billing service")
                        updateAmountButtonAvailability()
                        updateDonateButtonState()
                    }
                }

                override fun onBillingDisconnected() {
                    withActiveDonationView {
                        L.d("DonateFragment: Billing service disconnected")
                        isConnected = false
                        availableProductIds = emptySet()
                        updateAmountButtonAvailability()
                        updateDonateButtonState()
                    }
                }

                override fun onPurchaseSuccess(productId: String?) {
                    withActiveDonationView {
                        handlePurchaseSuccess(productId)
                    }
                }

                override fun onPurchasePending() {
                    withActiveDonationView {
                        L.d("DonateFragment: Purchase is pending")
                        setStatusText("Purchase is pending...")
                    }
                }

                override fun onPurchaseCancelled() {
                    withActiveDonationView {
                        L.d("DonateFragment: Purchase cancelled by user")
                        this@DonateFragment.onPurchaseCancelled()
                    }
                }

                override fun onPurchaseError(message: String) {
                    withActiveDonationView {
                        L.d("DonateFragment: Purchase error: $message")
                        this@DonateFragment.onPurchaseError(message)
                    }
                }
            }
        )
        billingGateway.start()
    }

    private fun withActiveDonationView(action: () -> Unit) {
        if (!acceptsBillingCallbacks || _binding == null) {
            L.d("DonateFragment: Ignoring billing callback after view teardown")
            return
        }

        if (viewLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            L.d("DonateFragment: Ignoring billing callback for inactive view lifecycle")
            return
        }

        action()
    }

    private fun handlePurchaseSuccess(productId: String?) {
        L.d("DonateFragment: Purchase successful")
        if (!isAdded || childFragmentManager.isStateSaved) {
            L.d("DonateFragment: Skipping donation success dialog after saved state")
            return
        }

        hideStatusText()

        val amount = when (productId) {
            DonationProductIds.DONATE_1 -> BigDecimal("1.00")
            DonationProductIds.DONATE_5 -> BigDecimal("5.00")
            DonationProductIds.DONATE_10 -> BigDecimal("10.00")
            DonationProductIds.DONATE_25 -> BigDecimal("25.00")
            else -> selectedAmount ?: BigDecimal.ZERO
        }

        val successDialog = DonationSuccessDialogFragment.newInstance(amount)
        successDialog.show(childFragmentManager, DonationSuccessDialogFragment.TAG)
    }
    
    private fun onPurchaseError(message: String) {
        L.e("DonateFragment: Purchase failed: $message")
        hideStatusText()
        setStatusText(getString(R.string.donate_error_message))
        updateDonateButtonState()
    }
    
    private fun onPurchaseCancelled() {
        hideStatusText()
        updateDonateButtonState()
    }
    
    private fun resetForm() {
        selectedAmount = null
        selectedProductId = null
        clearAllButtonSelections()
        hideStatusText()
        updateDonateButtonState()
    }
    
    fun onSuccessDialogDismissed() {
        L.d("DonateFragment: Success dialog dismissed, resetting form")
        resetForm()
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
    
    override fun onDestroyView() {
        L.d("DonateFragment: onDestroyView called.")
        acceptsBillingCallbacks = false
        
        if (::billingGateway.isInitialized) {
            billingGateway.disconnect()
        }
        
        _binding = null
        super.onDestroyView()
    }
}
