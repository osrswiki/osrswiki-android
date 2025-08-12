package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.StringUtil
import kotlin.system.measureTimeMillis

class PageHtmlBuilder(private val context: Context) {

    private val logTag = "PageLoadTrace"

    private val styleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/fonts.css",
        "styles/layout.css",
        "styles/components.css",
        "styles/wiki-integration.css",
        "styles/navbox_styles.css",
        "web/app/collapsible_tables.css",
        "web/app/collapsible_sections.css",
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(),
        "styles/fixes.css"
    )

    // ResourceLoader startup module - must load directly as it contains the ResourceLoader system itself
    private val startupModule = "web/core/startup.js"
    
    // Individual core modules loaded through ResourceLoader (proper server pattern)
    private val coreResourceLoaderModules = listOf(
        "web/core/mediawiki_base.js",     // Contains mw.loader.using and core MediaWiki functionality
        "web/core/mediawiki_util.js",     // MediaWiki utility functions  
        "web/core/oojs.js"                // OOjs library for OOUI support
    )
    
    // Expert recommendation: Remove complex coordination infrastructure
    // The browser's sequential script execution provides all the coordination we need
    
    // Essential app-specific functionality (simplified)
    private val appSpecificModules = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js",
        "web/app/collapsible_content.js",
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
        "web/app/horizontal_scroll_interceptor.js",
        "web/app/responsive_videos.js",
        "web/app/clipboard_bridge.js"
    )
    
    // Expert recommendation: Remove complex asset categorization
    // Use simple sequential loading instead of deferred/direct/bridge patterns

    
    private val timelineLoggerScript = """
        <script>
            document.addEventListener('DOMContentLoaded', function() {
                if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
                    window.RenderTimeline.log('Event: DOMContentLoaded');
                }
            });
            window.addEventListener('load', function() {
                if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
                    window.RenderTimeline.log('Event: window.load');
                }
                // Make page visible once fully loaded to prevent FOUC
                document.body.style.visibility = 'visible';
            });
        </script>
    """.trimIndent()
    
    private val themeUtilityScript = """
        <script>
            // Theme switching utility for instant theme changes
            window.OSRSWikiTheme = {
                switchTheme: function(isDark) {
                    var body = document.body;
                    if (!body) return;
                    
                    // Remove existing theme classes
                    body.classList.remove('theme-osrs-dark');
                    
                    // Add dark theme class if needed
                    if (isDark) {
                        body.classList.add('theme-osrs-dark');
                    }
                    
                    // Force immediate style recalculation
                    body.offsetHeight;
                    
                    // Ensure page remains visible after theme change
                    if (body.style.visibility !== 'visible') {
                        body.style.visibility = 'visible';
                    }
                }
            };
        </script>
    """.trimIndent()

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        var finalHtml: String
        val time = measureTimeMillis {
            val cleanedTitle = StringUtil.extractMainTitle(title)
            val documentTitle = if (cleanedTitle.isBlank()) "OSRS Wiki" else cleanedTitle
            val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
            
            // Clean any existing page-header titles from bodyContent to prevent duplication
            val cleanedBodyContent = removeDuplicatePageHeaders(bodyContent)
            val finalBodyContent = titleHeaderHtml + cleanedBodyContent
            val themeClass = when (theme) {
                Theme.OSRS_DARK -> "theme-osrs-dark"
                else -> "" // OSRS Light is the default theme in CSS, no class needed.
            }

            val cssLinks = styleSheetAssets.joinToString("\n") { assetPath ->
                // The URL must match the path handled by WebViewAssetLoader.
                "<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/$assetPath\">"
            }

            // Phase 1 Simplification: Remove two-phase loading system per expert recommendations
            // Expert consensus: Pre-scanning creates race conditions between gadget execution and ResourceLoader initialization
            Log.d(logTag, "Using simplified single-phase loading (expert-recommended architecture)")

            // Expert-recommended single-phase loading: Use browser's sequential script execution
            // Order: jQuery → dependency_normalizer → startup.js → bind_normalizer → package_executor → mediawiki.base → external deps → gadgets
            
            // 1. Foundation: jQuery and dependency resolution
            val foundationAssets = listOf(
                "web/external/jquery.js",                    // Must be first - foundation for everything
                "web/app/dependency_normalizer.js"           // Must be BEFORE startup.js to intercept register calls
            )
            
            // 2. ResourceLoader startup (will call our normalizer)
            val startupAssets = listOf(
                startupModule                               // Creates mw.loader and calls register with numeric deps
            )
            
            // 3. Package module executor (will run after dependencies are normalized)
            // Note: dependency_normalizer.js auto-binds when startup.js loads
            
            // 4. Package module executor (Expert 2's minimal approach + Expert 1's dependency fix)
            val packageExecutor = listOf(
                "web/app/package_module_executor.js"          // Enhances mw.loader.impl for package modules
            )
            
            // 5. Core MediaWiki modules (contains mw.loader.using)
            val coreAssets = coreResourceLoaderModules
            
            // 6. External dependencies (Highcharts, etc.) - bundled as assets
            val externalDependencies = listOf(
                "web/external/highcharts-stock.js", // Bundle Highcharts instead of URL loading
                "web/external/chart.js"              // Additional chart dependencies
            )
            
            // 7. Gadget modules (will be registered but not executed immediately)
            val gadgetAssets = listOf(
                "web/external/ge_charts_core.js",   // GE Charts core functionality
                "web/external/ge_charts_loader.js", // GE Charts main gadget
                "web/external/citation_enhancements.js" // Citation improvements
            )
            
            // Expert recommendation: Use ordered script tags for deterministic, synchronous loading
            // Browser guarantees sequential execution without async/defer attributes
            // Order: jQuery → dependency_normalizer → startup → package_executor → core → external deps → gadgets → app-specific
            val allScriptsInOrder = foundationAssets + startupAssets + packageExecutor + coreAssets + externalDependencies + gadgetAssets + appSpecificModules
            
            val sequentialScripts = allScriptsInOrder.joinToString("\n") { assetPath ->
                if (assetPath.startsWith("inline:")) {
                    // Handle inline JavaScript code
                    val jsCode = assetPath.substring(7) // Remove "inline:" prefix
                    "<script>$jsCode</script>"
                } else {
                    // Handle external script files
                    "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
                }
            }
            
            // Debug trigger: Check MediaWiki's actual dependency resolution
            val executionTrigger = """
                <script>
                    // Debug MediaWiki dependency resolution
                    function debugMediaWikiDependencies() {
                        console.log('[DEBUG] Checking MediaWiki dependency resolution...');
                        
                        if (window.mw && window.mw.loader && typeof window.mw.loader.using === 'function') {
                            console.log('[DEBUG] mw.loader.using is available!');
                            
                            // Check jQuery module in registry
                            const jqueryMod = window.mw.loader.moduleRegistry['jquery'];
                            console.log('[DEBUG] jQuery module in registry:', jqueryMod);
                            
                            // Check mediawiki.base dependencies
                            const mediawikiBase = window.mw.loader.moduleRegistry['mediawiki.base'];
                            console.log('[DEBUG] mediawiki.base dependencies:', mediawikiBase ? mediawikiBase.deps : 'not found');
                            
                            // Check ext.gadget.GECharts dependencies  
                            const geCharts = window.mw.loader.moduleRegistry['ext.gadget.GECharts'];
                            console.log('[DEBUG] ext.gadget.GECharts dependencies:', geCharts ? geCharts.deps : 'not found');
                            
                            // Test jQuery dependency resolution directly
                            console.log('[DEBUG] Testing mw.loader.using([\"jquery\"])...');
                            window.mw.loader.using(['jquery'], function() {
                                console.log('[DEBUG] SUCCESS: jQuery loaded via mw.loader.using');
                                
                                // Now test GE Charts after jQuery works
                                if (document.querySelector('.GEChartBox, .GEdatachart, .GEdataprices')) {
                                    console.log('[DEBUG] GE chart elements found, testing GE charts...');
                                    window.mw.loader.using(['ext.gadget.GECharts'], function() {
                                        console.log('[DEBUG] SUCCESS: GE Charts loaded!');
                                    }, function(error) {
                                        console.error('[DEBUG] ERROR: GE Charts failed:', error);
                                    });
                                }
                            }, function(error) {
                                console.error('[DEBUG] ERROR: jQuery failed to load:', error);
                            });
                        } else {
                            console.log('[DEBUG] mw.loader.using not ready, retrying in 50ms...');
                            setTimeout(debugMediaWikiDependencies, 50);
                        }
                    }
                    
                    // Start debugging after DOM is ready
                    document.addEventListener('DOMContentLoaded', function() {
                        console.log('[DEBUG] DOM ready, starting MediaWiki dependency debug...');
                        debugMediaWikiDependencies();
                    });
                </script>
            """
            
            // Expert-recommended approach: Simple concatenation of sequential scripts + execution trigger
            // No complex coordination, timing delays, or polling - just ordered execution
            val allScripts = sequentialScripts + "\n" + executionTrigger

            // Experiment: Preload the main web font to improve rendering performance.
            val fontPreloadLink = "<link rel=\"preload\" href=\"https://appassets.androidplatform.net/res/font/runescape_plain.ttf\" as=\"font\" type=\"font/ttf\" crossorigin=\"anonymous\">"

            finalHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${documentTitle}</title>
                    ${fontPreloadLink}
                    ${cssLinks}
                    ${themeUtilityScript}
                </head>
                <body class="$themeClass" style="visibility: hidden;">
                    ${finalBodyContent}
                    ${allScripts}
                    ${timelineLoggerScript}
                </body>
                </html>
            """.trimIndent()
        }
        Log.d(logTag, "buildFullHtmlDocument() took ${time}ms")
        return finalHtml
    }
    
    /**
     * Removes any existing page-header titles from HTML content to prevent duplication.
     * This is useful for cleaning content that may have been processed multiple times.
     */
    private fun removeDuplicatePageHeaders(htmlContent: String): String {
        return try {
            // Use a regex to remove h1 elements with class="page-header"
            // This is more efficient than parsing the entire HTML with Jsoup for this simple operation
            val pageHeaderRegex = Regex("<h1\\s+class=\"page-header\"[^>]*>.*?</h1>", RegexOption.DOT_MATCHES_ALL)
            htmlContent.replace(pageHeaderRegex, "")
        } catch (e: Exception) {
            Log.e(logTag, "Error removing duplicate page headers", e)
            htmlContent // Return original content if cleaning fails
        }
    }
}
