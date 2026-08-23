package com.omiyawaki.osrswiki.donate

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper

data class DonationBillingLaunchResult(
    val isSuccess: Boolean,
    val message: String? = null
)

interface DonationBillingListener {
    fun onBillingReady(productIds: Set<String>)
    fun onProductPrices(prices: Map<String, String>) {}
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
    var factory: DonationBillingGatewayFactory = DefaultDonationBillingGatewayFactory

    fun reset() {
        factory = DefaultDonationBillingGatewayFactory
    }
}

object DonationProductIds {
    const val DONATE_1 = "donate_1_usd"
    const val DONATE_5 = "donate_5_usd"
    const val DONATE_10 = "donate_10_usd"
    const val DONATE_25 = "donate_25_usd"

    val all = listOf(DONATE_1, DONATE_5, DONATE_10, DONATE_25)
}
