/**
 * MediaWiki ResourceLoader Bridge
 * 
 * Ensures proper timing between MediaWiki ResourceLoader initialization
 * and external modules that depend on mw.loader.using().
 * 
 * This fixes the timing issue where GE charts try to use mw.loader.using()
 * before the ResourceLoader is fully initialized.
 */

console.log('[MW-ResourceLoader-Bridge] Starting MediaWiki ResourceLoader initialization bridge...');

// Queue for modules that need to wait for mw.loader to be ready
let moduleQueue = [];
let resourceLoaderReady = false;
let initCheckInterval;

/**
 * Check if MediaWiki ResourceLoader is fully initialized
 */
function isResourceLoaderReady() {
    return (
        typeof window.mw !== 'undefined' &&
        window.mw.loader &&
        typeof window.mw.loader.using === 'function' &&
        typeof window.mw.loader.implement === 'function' &&
        typeof window.mw.loader.load === 'function'
    );
}

/**
 * Process queued modules once ResourceLoader is ready
 */
function processQueuedModules() {
    console.log('[MW-ResourceLoader-Bridge] Processing', moduleQueue.length, 'queued operations');
    
    // Execute all queued operations
    while (moduleQueue.length > 0) {
        const operation = moduleQueue.shift();
        try {
            operation();
            console.log('[MW-ResourceLoader-Bridge] Executed queued operation successfully');
        } catch (error) {
            console.error('[MW-ResourceLoader-Bridge] Error executing queued operation:', error);
        }
    }
}

/**
 * Queue an operation to run when ResourceLoader is ready
 */
function queueOperation(operation) {
    if (resourceLoaderReady) {
        // ResourceLoader is ready, execute immediately
        operation();
    } else {
        // Queue for later execution
        moduleQueue.push(operation);
        console.log('[MW-ResourceLoader-Bridge] Queued operation (queue size:', moduleQueue.length + ')');
    }
}

/**
 * Enhanced mw.loader.using() wrapper that ensures proper timing
 */
function safeLoaderUsing(modules, ready, error) {
    queueOperation(() => {
        console.log('[MW-ResourceLoader-Bridge] Safely calling mw.loader.using for:', modules);
        window.mw.loader.using(modules, ready, error);
    });
}

/**
 * Enhanced mw.loader.load() wrapper that ensures proper timing
 */
function safeLoaderLoad(modules, type) {
    queueOperation(() => {
        console.log('[MW-ResourceLoader-Bridge] Safely calling mw.loader.load for:', modules);
        window.mw.loader.load(modules, type);
    });
}

/**
 * Check ResourceLoader readiness and set up the system
 */
function checkResourceLoaderReadiness() {
    if (isResourceLoaderReady()) {
        console.log('[MW-ResourceLoader-Bridge] ✅ MediaWiki ResourceLoader is ready!');
        resourceLoaderReady = true;
        
        // Clear the check interval
        if (initCheckInterval) {
            clearInterval(initCheckInterval);
            initCheckInterval = null;
        }
        
        // Process any queued modules
        if (moduleQueue.length > 0) {
            processQueuedModules();
        }
        
        // Set up global fallbacks for immediate access
        if (!window.mwLoaderBridge) {
            window.mwLoaderBridge = {
                using: safeLoaderUsing,
                load: safeLoaderLoad,
                isReady: () => resourceLoaderReady,
                queue: queueOperation
            };
        }
        
        // Dispatch ready event
        try {
            const readyEvent = new CustomEvent('mwResourceLoaderReady', {
                detail: { timestamp: new Date().toISOString() }
            });
            document.dispatchEvent(readyEvent);
            console.log('[MW-ResourceLoader-Bridge] Dispatched mwResourceLoaderReady event');
        } catch (e) {
            console.warn('[MW-ResourceLoader-Bridge] Could not dispatch ready event:', e);
        }
        
    } else {
        console.log('[MW-ResourceLoader-Bridge] ⏳ Waiting for MediaWiki ResourceLoader...');
        const status = {
            'window.mw': typeof window.mw,
            'mw.loader': window.mw && typeof window.mw.loader,
            'mw.loader.using': window.mw && window.mw.loader && typeof window.mw.loader.using,
            'mw.loader.implement': window.mw && window.mw.loader && typeof window.mw.loader.implement,
            'mw.loader.load': window.mw && window.mw.loader && typeof window.mw.loader.load
        };
        console.log('[MW-ResourceLoader-Bridge] Status details:', JSON.stringify(status));
        if (window.mw && window.mw.loader) {
            console.log('[MW-ResourceLoader-Bridge] Available loader methods:', Object.keys(window.mw.loader));
        }
    }
}

// Start checking for ResourceLoader readiness
console.log('[MW-ResourceLoader-Bridge] Starting ResourceLoader readiness checks...');
initCheckInterval = setInterval(checkResourceLoaderReadiness, 50); // Check every 50ms

// Also check immediately and on DOM ready
checkResourceLoaderReadiness();
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', checkResourceLoaderReadiness);
} else {
    // DOM is already ready
    setTimeout(checkResourceLoaderReadiness, 10);
}

// Set up global interface immediately (even before ResourceLoader is ready)
window.mwLoaderBridge = {
    using: safeLoaderUsing,
    load: safeLoaderLoad,
    isReady: () => resourceLoaderReady,
    queue: queueOperation
};

console.log('[MW-ResourceLoader-Bridge] Bridge interface available at window.mwLoaderBridge');