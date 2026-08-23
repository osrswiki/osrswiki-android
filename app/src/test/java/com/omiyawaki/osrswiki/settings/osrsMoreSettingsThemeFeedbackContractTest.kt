package com.omiyawaki.osrswiki.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsMoreSettingsThemeFeedbackContractTest {

    @Test
    fun choicePopupAlignsToTheTrailingEdgeOfTheRow() {
        val source = source("settings/osrsChoicePreference.kt")
        assertTrue(source.contains("Gravity.END"))
        assertTrue(source.contains("showAsDropDown(anchor, 0, (4 * density).toInt(), Gravity.END)"))
        assertFalse(source.contains("coerceAtMost(0)"))
    }

    @Test
    fun settingsHostInsetsEachRowInsteadOfOneWrappingCard() {
        val layout = resource("layout/activity_osrs_settings.xml")
        val fragment = source("settings/osrsSettingsPreferenceFragment.kt")
        val typography = resource("values/typography.xml")
        val strings = resource("values/strings.xml")

        assertFalse(layout.contains("osrs_settings_card"))
        assertTrue(layout.contains("@+id/osrs_settings_container"))
        assertTrue(fragment.contains("osrs_settings_row_inset"))
        assertTrue(fragment.contains("applyRowChrome"))
        assertTrue(typography.contains("<item name=\"android:textSize\">14sp</item>"))
        assertTrue(typography.substringAfter("PreferenceCategory").substringBefore("</style>").contains("600"))
        assertTrue(strings.contains("settings_navigation_gestures_section\">Navigation<"))
    }

    @Test
    fun overlayHostDetachesWhenEmptyAndBeforeThemeRecreate() {
        val presenter = source("page/osrsArticleOverlayPresenter.kt")
        val base = source("activity/BaseActivity.kt")
        val page = source("page/PageActivity.kt")
        val overlay = source("page/osrsArticleOverlayFragment.kt")

        assertTrue(presenter.contains("fun detachHost"))
        assertTrue(presenter.contains("visibility = View.GONE"))
        assertTrue(presenter.contains("elevation = 0f"))
        assertTrue(presenter.contains("Color.TRANSPARENT"))
        assertTrue(presenter.contains("translationX = 0f"))
        val applyTheme = base.substringAfter("fun applyThemeDynamically()").substringBefore("fun refreshThemeDependentElements")
        assertTrue(presenter.contains("fun snapshot("))
        assertTrue(presenter.contains("fun restore("))
        assertTrue(applyTheme.contains("osrsArticleOverlayPresenter.snapshot(this)"))
        assertTrue(applyTheme.contains("EXTRA_ARTICLE_OVERLAY_RESTORE"))
        assertTrue(applyTheme.contains("osrsArticleOverlayPresenter.popAll(this)"))
        assertTrue(applyTheme.contains("osrsArticleOverlayPresenter.detachHost(this)"))
        assertTrue(applyTheme.contains("recreate()"))
        assertTrue(base.contains("restoreArticleOverlaysAfterThemeRecreate"))
        assertTrue(base.contains("osrsArticleOverlayPresenter.restore(this"))
        val progress = page.substringAfter("private fun applyInteractiveBackProgress").substringBefore("private fun commitInteractiveBack")
        assertTrue(progress.contains("pageLiveUnderlay"))
        assertTrue(progress.contains("pageBackPreview.visibility = View.GONE"))
        assertTrue(progress.contains("revealLivePreviousActivity"))
        assertTrue(page.contains("setTranslucent(reveal)"))
        assertFalse(progress.contains("osrsUnderlyingActivityPreview"))
        assertTrue(overlay.contains("binding.navMenuTriggerLayout"))
        assertTrue(overlay.contains("fun slidingChrome(): View? = _binding?.navMenuTriggerLayout"))
        assertTrue(overlay.contains("applyOpaqueArticleChrome"))
        assertFalse(overlay.contains("val sliding = binding.root"))
    }

    @Test
    fun donateBindsStoreLocalizedPricesAndKeepsHonestCopy() {
        val gateway = source("donate/DonationBillingGateway.kt")
        val playGateway = File("src/play/java/com/omiyawaki/osrswiki/donate/DefaultDonationBillingGatewayFactory.kt").readText()
        val fossGateway = File("src/foss/java/com/omiyawaki/osrswiki/donate/DefaultDonationBillingGatewayFactory.kt").readText()
        val fragment = source("donate/DonateFragment.kt")
        val strings = resource("values/strings.xml")
        val layout = resource("layout/fragment_donate.xml")

        assertTrue(gateway.contains("fun onProductPrices"))
        assertFalse(gateway.contains("BillingClient"))
        assertTrue(playGateway.contains("osrsFormattedPrice()"))
        assertTrue(playGateway.contains("BillingClient"))
        assertFalse(fossGateway.contains("BillingClient"))
        assertFalse(fossGateway.contains("com.android.billingclient"))
        assertTrue(fragment.contains("setupFossNonIapUi"))
        assertTrue(fragment.contains("onProductPrices"))
        assertTrue(fragment.contains("localizedPrice"))
        assertFalse(fragment.contains("currency picker") || fragment.contains("Currency"))
        assertTrue(strings.contains("donate_app_blurb_free\">This app is free. Nothing is locked behind a donation."))
        assertTrue(strings.contains("Play Store and the time it takes to keep the app working."))
        assertTrue(strings.contains("donate_tips_coming_soon"))
        assertTrue(strings.contains("The Old School RuneScape Wiki is run by volunteers. Support them too if you can."))
        assertTrue(layout.contains("@string/donate_app_blurb_free"))
        assertTrue(layout.contains("@string/donate_app_blurb_help"))
        assertTrue(layout.contains("wiki_support_title"))
        assertTrue(layout.contains("android:visibility=\"gone\""))
        assertTrue(layout.contains("@style/Widget.OSRSWiki.OutboundLinkButton"))
        assertFalse(strings.contains("currency picker"))
    }

    @Test
    fun moreSubpagesDoNotRepeatTheNavTitleAndUseOutboundLinkRows() {
        val strings = resource("values/strings.xml")
        val feedback = resource("layout/fragment_feedback.xml")
        val fragment = source("feedback/FeedbackFragmentSecure.kt")
        val about = resource("layout/fragment_about.xml")
        val styles = resource("values/styles.xml")

        assertTrue(strings.contains("menu_title_feedback\">Feedback<"))
        assertTrue(strings.contains("feedback_nav_title\">Send Feedback<"))
        assertTrue(strings.contains("feedback_rate_app_title\">Rate This App<"))
        assertTrue(strings.contains("feedback_report_issue_title\">Report an Issue<"))
        assertTrue(strings.contains("feedback_request_feature_title\">Request a Feature<"))
        assertFalse(feedback.contains("@string/feedback_title"))
        assertFalse(feedback.contains("@string/menu_title_feedback"))
        assertTrue(feedback.contains("android:clickable=\"false\""))
        assertTrue(fragment.contains("rateAppButton.setOnClickListener"))
        assertTrue(fragment.contains("BuildConfig.FLAVOR == \"foss\""))
        assertTrue(fragment.contains("rateAppCard.visibility = View.GONE"))
        assertFalse(fragment.contains("rateAppCard.setOnClickListener"))
        assertTrue(styles.contains("Widget.OSRSWiki.OutboundLinkButton"))
        assertTrue(feedback.contains("@style/Widget.OSRSWiki.OutboundLinkButton"))
        assertTrue(about.contains("@style/Widget.OSRSWiki.OutboundLinkButton"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()

    private fun resource(relativePath: String): String =
        File("src/main/res/$relativePath").readText()
}
