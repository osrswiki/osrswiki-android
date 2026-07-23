package com.omiyawaki.osrswiki.donate

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

data class DonationBillingLaunchResult(
    val isSuccess: Boolean,
    val message: String? = null
)

interface DonationBillingListener {
    fun onBillingReady(productIds: Set<String>)
    fun onBillingSetupFailed(message: String)
    fun onBillingDisconnected()
    fun onPurchaseSuccess(productId: String?)
    fun onPurchasePending()
    fun onPurchaseCancelled()
    fun onPurchaseError(message: String)
}

interface DonationBillingGateway {
    fun start()
    fun launchPurchase(activity: Activity, productId: String): DonationBillingLaunchResult
    fun disconnect()
}

interface DonationBillingGatewayFactory {
    fun create(
        context: Context,
        listener: DonationBillingListener
    ): DonationBillingGateway
}

internal class DonationBillingCallbackDispatcher(
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    @Volatile
    private var disposed = false

    fun dispatch(action: () -> Unit) {
        if (disposed) return

        if (Looper.myLooper() == mainHandler.looper) {
            if (!disposed) action()
        } else {
            mainHandler.post {
                if (!disposed) action()
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        mainHandler.removeCallbacksAndMessages(null)
    }
}

object DonationBillingGatewayRegistry {
    @Volatile
    var factory: DonationBillingGatewayFactory = PlayDonationBillingGatewayFactory

    fun reset() {
        factory = PlayDonationBillingGatewayFactory
    }
}

object DonationProductIds {
    const val DONATE_1 = "donate_1_usd"
    const val DONATE_5 = "donate_5_usd"
    const val DONATE_10 = "donate_10_usd"
    const val DONATE_25 = "donate_25_usd"

    val all = listOf(DONATE_1, DONATE_5, DONATE_10, DONATE_25)
}

private object PlayDonationBillingGatewayFactory : DonationBillingGatewayFactory {
    override fun create(
        context: Context,
        listener: DonationBillingListener
    ): DonationBillingGateway {
        return PlayDonationBillingGateway(context, listener)
    }
}

private class PlayDonationBillingGateway(
    context: Context,
    private val listener: DonationBillingListener
) : DonationBillingGateway, PurchasesUpdatedListener, BillingClientStateListener {

    private val callbackDispatcher = DonationBillingCallbackDispatcher()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var availableProducts = mapOf<String, ProductDetails>()
    private var disconnectRequested = false

    override fun start() {
        if (disconnectRequested) return
        billingClient.startConnection(this)
    }

    override fun launchPurchase(activity: Activity, productId: String): DonationBillingLaunchResult {
        val productDetails = availableProducts[productId]
            ?: return DonationBillingLaunchResult(false, "Product not available")

        val purchaseParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, purchaseParams)
        return DonationBillingLaunchResult(
            isSuccess = billingResult.responseCode == BillingClient.BillingResponseCode.OK,
            message = billingResult.debugMessage
        )
    }

    override fun disconnect() {
        if (disconnectRequested) return
        disconnectRequested = true
        callbackDispatcher.dispose()

        if (billingClient.isReady) {
            try {
                billingClient.endConnection()
            } catch (exception: IllegalArgumentException) {
                Log.d("DonationBillingGateway", "Ignoring BillingClient disconnect after unavailable service: ${exception.message}")
            } catch (exception: IllegalStateException) {
                Log.d("DonationBillingGateway", "Ignoring BillingClient disconnect after state change: ${exception.message}")
            }
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (disconnectRequested) return
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProducts()
            queryPendingPurchases()
        } else {
            notifyListener { onBillingSetupFailed(billingResult.debugMessage) }
        }
    }

    override fun onBillingServiceDisconnected() {
        if (disconnectRequested) return
        notifyListener { onBillingDisconnected() }
    }

    private fun queryProducts() {
        val productList = DonationProductIds.all.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (disconnectRequested) return@queryProductDetailsAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                availableProducts = productDetailsList.associateBy { it.productId }
                notifyListener { onBillingReady(availableProducts.keys) }
            } else {
                notifyListener { onBillingSetupFailed(billingResult.debugMessage) }
            }
        }
    }

    private fun queryPendingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (disconnectRequested) return@queryPurchasesAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (disconnectRequested) return
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> notifyListener { onPurchaseCancelled() }
            else -> notifyListener { onPurchaseError(billingResult.debugMessage ?: "Purchase failed") }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (disconnectRequested) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> acknowledgeOrReportSuccess(purchase)
            Purchase.PurchaseState.PENDING -> notifyListener { onPurchasePending() }
        }
    }

    private fun acknowledgeOrReportSuccess(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { ackResult ->
                if (disconnectRequested) return@acknowledgePurchase
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    notifyListener { onPurchaseSuccess(purchase.products.firstOrNull()) }
                } else {
                    notifyListener { onPurchaseError("Failed to complete purchase") }
                }
            }
        } else {
            notifyListener { onPurchaseSuccess(purchase.products.firstOrNull()) }
        }
    }

    private fun notifyListener(action: DonationBillingListener.() -> Unit) {
        callbackDispatcher.dispatch {
            listener.action()
        }
    }
}
