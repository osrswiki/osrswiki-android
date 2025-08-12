/**
 * MediaWiki ResourceLoader Source Configuration
 * 
 * Extracted from live OSRS Wiki server.
 * Defines the sources (URLs) where modules can be loaded from.
 */

if (window.mw && window.mw.loader && window.mw.loader.addSource) {
    window.mw.loader.addSource({});
} else {
    console.warn('[MW-SOURCES] mw.loader.addSource not available, deferring source configuration');
    
    // Queue for when ResourceLoader is ready
    window.OSRSWIKI_DEFERRED_SOURCES = {};
}
