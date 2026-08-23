package com.omiyawaki.osrswiki.donate

import android.app.Activity
import android.content.Context

/**
 * FOSS distribution has no Play Billing on the classpath.
 * DonateFragment opens GitHub Sponsors in the browser for optional tips;
 * this gateway exists only to satisfy the play/foss factory split and must
 * never pretend IAP exists.
 */
object DefaultDonationBillingGatewayFactory : DonationBillingGatewayFactory {
    override fun create(
        context: Context,
        listener: DonationBillingListener
    ): DonationBillingGateway {
        return FossDonationBillingGateway(listener)
    }
}

private class FossDonationBillingGateway(
    private val listener: DonationBillingListener
) : DonationBillingGateway {

    private val callbackDispatcher = DonationBillingCallbackDispatcher()
    private var disconnectRequested = false

    override fun start() {
        if (disconnectRequested) return
        // FOSS UI does not use billing; report no products if ever started.
        callbackDispatcher.dispatch {
            if (!disconnectRequested) {
                listener.onBillingSetupFailed("foss_no_iap")
            }
        }
    }

    override fun launchPurchase(activity: Activity, productId: String): DonationBillingLaunchResult {
        return DonationBillingLaunchResult(
            isSuccess = false,
            message = "In-app tips are not available in the FOSS build"
        )
    }

    override fun disconnect() {
        if (disconnectRequested) return
        disconnectRequested = true
        callbackDispatcher.dispose()
    }
}
