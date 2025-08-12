/**
 * MediaWiki Gadget Loader
 * 
 * Registers and loads MediaWiki gadgets with their dependencies
 */

(function() {
    'use strict';
    
    console.log('[GADGET-LOADER] Initializing gadget loader');
    
    // rsw-util is already registered in startup module, so we don't register it again
    if (window.mw && window.mw.loader) {
        console.log('[GADGET-LOADER] rsw-util will be handled by ResourceLoader');
        
        // Register GECharts gadget
        mw.loader.implement('ext.gadget.GECharts', function() {
            // Load GECharts.js
            console.log('[GADGET-LOADER] Loading GECharts gadget');
            var script = document.createElement('script');
            script.src = 'https://appassets.androidplatform.net/assets/web/gadgets/GECharts.js';
            document.head.appendChild(script);
        }, {}, {});
        
        // Register GECharts-core gadget with dependencies
        mw.loader.implement('ext.gadget.GECharts-core', function() {
            console.log('[GADGET-LOADER] Loading GECharts-core gadget');
            
            // First ensure dependencies are loaded
            mw.loader.using(['ext.gadget.rsw-util'], function() {
                // Load GECharts-core.js
                var script = document.createElement('script');
                script.src = 'https://appassets.androidplatform.net/assets/web/gadgets/GECharts-core.js';
                script.onload = function() {
                    console.log('[GADGET-LOADER] GECharts-core loaded');
                };
                script.onerror = function() {
                    console.error('[GADGET-LOADER] Failed to load GECharts-core');
                };
                document.head.appendChild(script);
            });
        }, {}, {});
        
        // Register Charts gadget
        mw.loader.implement('ext.gadget.Charts', function() {
            console.log('[GADGET-LOADER] Loading Charts gadget');
            var script = document.createElement('script');
            script.src = 'https://appassets.androidplatform.net/assets/web/gadgets/Charts.js';
            document.head.appendChild(script);
        }, {}, {});
        
        // Register Charts-core gadget
        mw.loader.implement('ext.gadget.Charts-core', function() {
            console.log('[GADGET-LOADER] Loading Charts-core gadget');
            
            // First ensure dependencies are loaded
            mw.loader.using(['ext.gadget.rsw-util'], function() {
                var script = document.createElement('script');
                script.src = 'https://appassets.androidplatform.net/assets/web/gadgets/Charts-core.js';
                script.onload = function() {
                    console.log('[GADGET-LOADER] Charts-core loaded');
                };
                document.head.appendChild(script);
            });
        }, {}, {});
        
        console.log('[GADGET-LOADER] Gadgets registered');
        
        // Auto-load GECharts if needed (mimics the gadget definition)
        $(function() {
            if ($('.GEdatachart').length || $('.GEChartBox').length || $('.GEdataprices').length) {
                console.log('[GADGET-LOADER] GE chart elements found, loading GECharts');
                mw.loader.load('ext.gadget.GECharts');
            }
            
            if ($('.chart').length) {
                console.log('[GADGET-LOADER] Chart elements found, loading Charts');
                mw.loader.load('ext.gadget.Charts');
            }
        });
        
    } else {
        console.warn('[GADGET-LOADER] mw.loader not available');
    }
})();