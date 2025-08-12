/**
 * MediaWiki Infrastructure Coordination Script
 * 
 * Loads all MediaWiki infrastructure components in the correct order
 * and handles deferred initialization for components that couldn't load immediately.
 */

(function() {
    'use strict';
    
    var LOG_PREFIX = '[MW-COORDINATION]';
    
    function log() {
        try { console.log.apply(console, [LOG_PREFIX].concat([].slice.call(arguments))); } catch (_) {}
    }
    
    // Provide UMD environment early for core modules bundle
    if (typeof window.module === 'undefined') {
        log('Providing global UMD environment for core modules bundle');
        window.module = { exports: {} };
    }
    
    function executeDeferred() {
        // Execute deferred package modules FIRST (mediawiki.base, mediawiki.util)
        if (window.OSRSWIKI_DEFERRED_PACKAGES && window.mw && window.mw.loader && typeof window.mw.loader.impl === 'function') {
            log('Executing ' + window.OSRSWIKI_DEFERRED_PACKAGES.length + ' deferred package modules');
            for (var i = 0; i < window.OSRSWIKI_DEFERRED_PACKAGES.length; i++) {
                var pkg = window.OSRSWIKI_DEFERRED_PACKAGES[i];
                log('Executing deferred package: ' + pkg.name);
                try {
                    pkg.execute();
                } catch (e) {
                    log('Error executing package ' + pkg.name + ': ' + e.message);
                }
            }
            delete window.OSRSWIKI_DEFERRED_PACKAGES;
        }
        
        // Execute deferred module registry
        if (window.OSRSWIKI_DEFERRED_REGISTRY && window.mw && window.mw.loader && window.mw.loader.register) {
            log('Executing deferred module registry');
            window.mw.loader.register(window.OSRSWIKI_DEFERRED_REGISTRY);
            delete window.OSRSWIKI_DEFERRED_REGISTRY;
        }
        
        // Execute deferred source configuration
        if (window.OSRSWIKI_DEFERRED_SOURCES && window.mw && window.mw.loader && window.mw.loader.addSource) {
            log('Executing deferred source configuration');
            window.mw.loader.addSource(window.OSRSWIKI_DEFERRED_SOURCES);
            delete window.OSRSWIKI_DEFERRED_SOURCES;
        }
        
        // Execute deferred module registry
        if (window.OSRSWIKI_DEFERRED_REGISTRY && typeof window.OSRSWIKI_DEFERRED_REGISTRY === 'function') {
            log('Executing deferred module registry function');
            window.OSRSWIKI_DEFERRED_REGISTRY();
            delete window.OSRSWIKI_DEFERRED_REGISTRY;
        }
        
        // Execute deferred source configuration
        if (window.OSRSWIKI_DEFERRED_SOURCES && typeof window.OSRSWIKI_DEFERRED_SOURCES === 'function') {
            log('Executing deferred source configuration function');
            window.OSRSWIKI_DEFERRED_SOURCES();
            delete window.OSRSWIKI_DEFERRED_SOURCES;
        }
        
        // Execute deferred additional initialization
        if (window.OSRSWIKI_DEFERRED_INIT && typeof window.OSRSWIKI_DEFERRED_INIT === 'function') {
            log('Executing deferred additional initialization');
            window.OSRSWIKI_DEFERRED_INIT();
            delete window.OSRSWIKI_DEFERRED_INIT;
        }
        
        // Provide UMD environment for core modules bundle (needed for oojs and other UMD modules)
        if (typeof window.module === 'undefined') {
            log('Providing UMD environment for core modules');
            window.module = { exports: {} };
        }
        
        // Verify that the complete ResourceLoader system is available
        if (window.mw && window.mw.loader) {
            var requiredMethods = ['using', 'load', 'implement', 'register', 'addSource', 'enqueue'];
            var missingMethods = [];
            
            for (var i = 0; i < requiredMethods.length; i++) {
                if (typeof window.mw.loader[requiredMethods[i]] !== 'function') {
                    missingMethods.push(requiredMethods[i]);
                }
            }
            
            if (missingMethods.length === 0) {
                log('✅ Complete ResourceLoader system verified - all methods available');
            } else {
                log('⚠️  Incomplete ResourceLoader system - missing methods:', missingMethods.join(', '));
            }
        }
    }
    
    // Try to execute deferred items immediately
    executeDeferred();
    
    // Set up interval to check for MediaWiki readiness
    var checkInterval = setInterval(function() {
        if (window.mw && window.mw.loader) {
            executeDeferred();
            clearInterval(checkInterval);
            log('MediaWiki infrastructure coordination complete');
        }
    }, 100);
    
    // Clear interval after 10 seconds to avoid infinite checking
    setTimeout(function() {
        clearInterval(checkInterval);
    }, 10000);
})();
