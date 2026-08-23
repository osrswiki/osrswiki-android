package com.omiyawaki.osrswiki.donate

import android.app.Activity
import android.content.Context

/**
 * FOSS distribution has no Play Billing on the classpath.
 * GitHub Sponsors is still pending (docs/public-landing/manifest.yaml);
 * tips CTA stays non-IAP copy only — never pretend IAP exists.
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
        // Sponsors pending: surface tips-coming-soon via setup-failed path (no products).
        callbackDispatcher.dispatch {
            if (!disconnectRequested) {
                listener.onBillingSetupFailed("tips_coming_soon")
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
