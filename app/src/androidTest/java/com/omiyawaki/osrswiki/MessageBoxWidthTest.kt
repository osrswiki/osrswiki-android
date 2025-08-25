package com.omiyawaki.osrswiki

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.omiyawaki.osrswiki.page.PageActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MessageBoxWidthTest {
    
    @get:Rule
    val activityRule = ActivityTestRule(PageActivity::class.java, true, false)
    
    @Test
    fun testMessageBoxWidth_CurrentStateShouldBeNarrow() {
        // This test documents the current broken state
        // Message boxes should currently be narrow (~65% width)
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, PageActivity::class.java).apply {
            putExtra(PageActivity.EXTRA_PAGE_TITLE, "Woodcutting")
            putExtra(PageActivity.EXTRA_PAGE_ID, 0) // Will trigger title-based loading
        }
        
        val activity = activityRule.launchActivity(intent)
        
        // Wait for page to load
        Thread.sleep(5000)
        
        val messageBoxWidths = measureMessageBoxWidths(activity)
        
        // Current broken state: message boxes should be narrow
        // We expect them to be around 65% of parent width or less
        assertFalse("Message boxes should currently be narrow (broken state)", 
            messageBoxWidths.isEmpty())
        
        for (widthData in messageBoxWidths) {
            val widthPercentage = (widthData.boxWidth.toDouble() / widthData.parentWidth.toDouble()) * 100
            
            // Document current broken state - boxes should be narrow
            assertTrue(
                "Message box should be narrow (current broken state). " +
                "Box width: ${widthData.boxWidth}, Parent width: ${widthData.parentWidth}, " +
                "Percentage: $widthPercentage%",
                widthPercentage < 80 // Less than 80% indicates narrow/broken state
            )
            
            println("📏 Message box width: ${widthData.boxWidth}px / ${widthData.parentWidth}px = $widthPercentage%")
        }
    }
    
    @Test 
    fun testMessageBoxWidth_FixedStateShouldBeWide() {
        // This test defines the target fixed state
        // After fix, message boxes should use nearly full width
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, PageActivity::class.java).apply {
            putExtra(PageActivity.EXTRA_PAGE_TITLE, "Woodcutting")
            putExtra(PageActivity.EXTRA_PAGE_ID, 0)
        }
        
        val activity = activityRule.launchActivity(intent)
        Thread.sleep(5000)
        
        val messageBoxWidths = measureMessageBoxWidths(activity)
        
        // Fixed state: message boxes should be wide (close to 100% width)
        assertFalse("Should find message boxes to measure", messageBoxWidths.isEmpty())
        
        for (widthData in messageBoxWidths) {
            val widthPercentage = (widthData.boxWidth.toDouble() / widthData.parentWidth.toDouble()) * 100
            
            // After fix, boxes should be wide (>90% of available width)
            assertTrue(
                "Message box should be wide (fixed state). " +
                "Box width: ${widthData.boxWidth}, Parent width: ${widthData.parentWidth}, " +
                "Percentage: $widthPercentage%",
                widthPercentage > 90 // Greater than 90% indicates wide/fixed state
            )
            
            println("📏 Fixed message box width: ${widthData.boxWidth}px / ${widthData.parentWidth}px = $widthPercentage%")
        }
    }
    
    private fun measureMessageBoxWidths(activity: PageActivity): List<MessageBoxWidthData> {
        val results = mutableListOf<MessageBoxWidthData>()
        val latch = CountDownLatch(1)
        
        activity.runOnUiThread {
            // Find WebView
            val webView = findWebView(activity)
            if (webView != null) {
                // JavaScript to measure message box widths
                val script = """
                    (function() {
                        var messageBoxes = document.querySelectorAll('.messagebox');
                        var results = [];
                        for (var i = 0; i < messageBoxes.length; i++) {
                            var box = messageBoxes[i];
                            var parent = box.parentElement;
                            var boxWidth = box.getBoundingClientRect().width;
                            var parentWidth = parent.getBoundingClientRect().width;
                            results.push({
                                boxWidth: boxWidth,
                                parentWidth: parentWidth,
                                cssWidth: getComputedStyle(box).width
                            });
                        }
                        return JSON.stringify(results);
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(script) { result ->
                    try {
                        if (result != "null") {
                            // Parse JSON result
                            val jsonResult = result.removeSurrounding("\"").replace("\\", "")
                            // Simple JSON parsing for test
                            if (jsonResult.contains("boxWidth")) {
                                // Extract width data (simplified parsing)
                                val matches = Regex(""""boxWidth":([0-9.]+).*?"parentWidth":([0-9.]+)""")
                                    .findAll(jsonResult)
                                
                                for (match in matches) {
                                    val boxWidth = match.groupValues[1].toFloatOrNull() ?: 0f
                                    val parentWidth = match.groupValues[2].toFloatOrNull() ?: 1f
                                    results.add(MessageBoxWidthData(boxWidth, parentWidth))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Error parsing width data: ${e.message}")
                    }
                    latch.countDown()
                }
            } else {
                latch.countDown()
            }
        }
        
        // Wait for JavaScript execution
        latch.await(10, TimeUnit.SECONDS)
        return results
    }
    
    private fun findWebView(activity: PageActivity): WebView? {
        var webView: WebView? = null
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)
        
        fun searchForWebView(view: android.view.View) {
            if (view is WebView) {
                webView = view
                return
            }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    searchForWebView(view.getChildAt(i))
                    if (webView != null) return
                }
            }
        }
        
        searchForWebView(rootView)
        return webView
    }
}

data class MessageBoxWidthData(
    val boxWidth: Float,
    val parentWidth: Float
)