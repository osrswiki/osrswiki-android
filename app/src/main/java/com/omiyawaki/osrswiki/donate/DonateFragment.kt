package com.omiyawaki.osrswiki.donate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentDonateBinding
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import kotlinx.coroutines.launch
import java.math.BigDecimal

class DonateFragment : Fragment(), PurchasesUpdatedListener, BillingClientStateListener {

    private var _binding: FragmentDonateBinding? = null
    private val binding get() = _binding!!
    
    private var selectedAmount: BigDecimal? = null
    
    // Google Play Billing
    private lateinit var billingClient: BillingClient
    private var isConnected = false
    private var availableProducts = mapOf<String, ProductDetails>()

    companion object {
        fun newInstance() = DonateFragment()
        const val TAG = "DonateFragment"
        
        // Product IDs for in-app purchases (these need to be configured in Google Play Console)
        private const val PRODUCT_DONATE_1 = "donate_1_usd"
        private const val PRODUCT_DONATE_5 = "donate_5_usd"
        private const val PRODUCT_DONATE_10 = "donate_10_usd" 
        private const val PRODUCT_DONATE_25 = "donate_25_usd"
        
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
        initializeBilling()
        setupAmountSelection()
        setupDonateButton()
        setupWikiDonateButton()
    }
    
    private fun setupAmountSelection() {
        // Set up preset amount chips
        binding.chipAmount1.setOnClickListener { selectPresetAmount(BigDecimal("1.00"), PRODUCT_DONATE_1) }
        binding.chipAmount5.setOnClickListener { selectPresetAmount(BigDecimal("5.00"), PRODUCT_DONATE_5) }
        binding.chipAmount10.setOnClickListener { selectPresetAmount(BigDecimal("10.00"), PRODUCT_DONATE_10) }
        binding.chipAmount25.setOnClickListener { selectPresetAmount(BigDecimal("25.00"), PRODUCT_DONATE_25) }
        
    }
    
    private fun selectPresetAmount(amount: BigDecimal, productId: String) {
        L.d("DonateFragment: Preset amount selected: $amount, productId: $productId")
        selectedAmount = amount
        
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
        binding.donateButton.isEnabled = hasValidAmount && isConnected
        
        if (hasValidAmount) {
            binding.donateButton.text = getString(R.string.donate_button_text) + " ($$selectedAmount)"
        } else {
            binding.donateButton.text = getString(R.string.donate_button_text)
        }
    }
    
    private fun initiatePurchase(amount: BigDecimal) {
        L.d("DonateFragment: Initiating purchase for amount: $amount")
        setStatusText(getString(R.string.donate_processing))
        
        val productId = when (amount) {
            BigDecimal("1.00") -> PRODUCT_DONATE_1
            BigDecimal("5.00") -> PRODUCT_DONATE_5
            BigDecimal("10.00") -> PRODUCT_DONATE_10
            BigDecimal("25.00") -> PRODUCT_DONATE_25
            else -> {
                L.e("DonateFragment: Unsupported amount: $amount")
                onPurchaseError("Unsupported donation amount")
                return
            }
        }
        
        val productDetails = availableProducts[productId]
        if (productDetails == null) {
            L.e("DonateFragment: Product details not found for: $productId")
            onPurchaseError("Product not available")
            return
        }
        
        val purchaseParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()
        
        val billingResult = billingClient.launchBillingFlow(requireActivity(), purchaseParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            L.e("DonateFragment: Failed to launch billing flow: ${billingResult.debugMessage}")
            onPurchaseError(billingResult.debugMessage)
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
        
        billingClient = BillingClient.newBuilder(requireContext())
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        billingClient.startConnection(this)
    }
    
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        L.d("DonateFragment: Billing setup finished with result: ${billingResult.responseCode}")
        
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            isConnected = true
            queryProducts()
            
            // Check for pending purchases
            queryPendingPurchases()
        } else {
            L.e("DonateFragment: Billing setup failed: ${billingResult.debugMessage}")
            setStatusText("Unable to connect to billing service")
            isConnected = false
        }
        
        updateDonateButtonState()
    }
    
    override fun onBillingServiceDisconnected() {
        L.d("DonateFragment: Billing service disconnected")
        isConnected = false
        updateDonateButtonState()
    }
    
    private fun queryProducts() {
        L.d("DonateFragment: Querying available products")
        
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_DONATE_1)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_DONATE_5)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_DONATE_10)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_DONATE_25)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        lifecycleScope.launch {
            val productDetailsResult = billingClient.queryProductDetails(params)
            
            if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productDetailsList = productDetailsResult.productDetailsList ?: emptyList()
                availableProducts = productDetailsList.associateBy { it.productId }
                L.d("DonateFragment: Found ${availableProducts.size} available products")
                
                if (availableProducts.isEmpty()) {
                    setStatusText("No donation options available")
                } else {
                    hideStatusText()
                }
            } else {
                L.e("DonateFragment: Failed to query products: ${productDetailsResult.billingResult.debugMessage}")
                setStatusText("Unable to load donation options")
            }
        }
    }
    
    private fun queryPendingPurchases() {
        L.d("DonateFragment: Checking for pending purchases")
        
        lifecycleScope.launch {
            val purchasesResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            
            if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchases = purchasesResult.purchasesList
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        handlePurchase(purchase)
                    }
                }
            }
        }
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        L.d("DonateFragment: Purchases updated with result: ${billingResult.responseCode}")
        
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                L.d("DonateFragment: Purchase cancelled by user")
                onPurchaseCancelled()
            }
            else -> {
                L.e("DonateFragment: Purchase error: ${billingResult.debugMessage}")
                onPurchaseError(billingResult.debugMessage ?: "Purchase failed")
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        L.d("DonateFragment: Handling purchase: ${purchase.products}")
        
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge the purchase
            lifecycleScope.launch {
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    
                    val ackResult = billingClient.acknowledgePurchase(acknowledgePurchaseParams)
                    
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        L.d("DonateFragment: Purchase acknowledged successfully")
                        onPurchaseSuccess(purchase)
                    } else {
                        L.e("DonateFragment: Failed to acknowledge purchase: ${ackResult.debugMessage}")
                        onPurchaseError("Failed to complete purchase")
                    }
                } else {
                    onPurchaseSuccess(purchase)
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            L.d("DonateFragment: Purchase is pending")
            setStatusText("Purchase is pending...")
        }
    }
    
    private fun onPurchaseSuccess(purchase: Purchase) {
        L.d("DonateFragment: Purchase successful")
        hideStatusText()
        
        // Get the donation amount from the product ID
        val productId = purchase.products.firstOrNull()
        val amount = when (productId) {
            PRODUCT_DONATE_1 -> BigDecimal("1.00")
            PRODUCT_DONATE_5 -> BigDecimal("5.00")
            PRODUCT_DONATE_10 -> BigDecimal("10.00")
            PRODUCT_DONATE_25 -> BigDecimal("25.00")
            else -> selectedAmount ?: BigDecimal.ZERO
        }
        
        // Show success dialog
        val successDialog = DonationSuccessDialogFragment.newInstance(amount)
        successDialog.show(childFragmentManager, DonationSuccessDialogFragment.TAG)
    }
    
    private fun onPurchaseError(message: String) {
        hideStatusText()
        setStatusText("${getString(R.string.donate_error_message)}: $message")
        binding.donateButton.isEnabled = selectedAmount != null && isConnected
    }
    
    private fun onPurchaseCancelled() {
        hideStatusText()
        binding.donateButton.isEnabled = selectedAmount != null && isConnected
    }
    
    private fun resetForm() {
        selectedAmount = null
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
    
    private fun setupFonts() {
        L.d("DonateFragment: Setting up fonts...")
        
        // Apply fonts to all TextViews
        binding.donateTitle.applyAlegreyaHeadline()
        binding.wikiSupportTitle.applyAlegreyaHeadline()
        
        L.d("DonateFragment: Fonts applied to all TextViews, chips, and buttons")
    }

    override fun onDestroyView() {
        L.d("DonateFragment: onDestroyView called.")
        
        // Clean up billing client
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
        
        _binding = null
        super.onDestroyView()
    }
}