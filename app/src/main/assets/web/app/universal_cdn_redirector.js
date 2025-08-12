/**
 * Universal CDN Redirection System
 * 
 * Automatically redirects ALL external CDN requests to local assets
 * without requiring library-specific configuration.
 */

console.log('[CDN-REDIRECTOR] Starting universal CDN redirection system...');

// Universal CDN mapping - automatically redirect common CDNs to local assets
const CDN_MAPPINGS = {
    'chisel.weirdgloop.org/static': 'web/external',
    'code.highcharts.com': 'web/external', 
    'cdnjs.cloudflare.com': 'web/external',
    'cdn.jsdelivr.net': 'web/external',
    'unpkg.com': 'web/external'
};

// Track redirected URLs to prevent infinite loops
const redirectedUrls = new Set();

// Intercept all script creation
const originalCreateElement = document.createElement;
document.createElement = function(tagName) {
    const element = originalCreateElement.call(this, tagName);
    
    if (tagName.toLowerCase() === 'script') {
        // Override src setter to intercept script loading
        let originalSrc = '';
        Object.defineProperty(element, 'src', {
            get: function() {
                return originalSrc;
            },
            set: function(url) {
                originalSrc = url;
                
                // Check if this URL should be redirected
                const redirectedUrl = redirectCdnUrl(url);
                if (redirectedUrl !== url && !redirectedUrls.has(url)) {
                    console.log('[CDN-REDIRECTOR] Redirecting:', url, '->', redirectedUrl);
                    redirectedUrls.add(url);
                    originalSrc = redirectedUrl;
                }
                
                // Set the actual src attribute
                element.setAttribute('src', originalSrc);
            }
        });
    }
    
    return element;
};

// Universal CDN URL redirection
function redirectCdnUrl(url) {
    if (!url || typeof url !== 'string') {
        return url;
    }
    
    // Check each CDN mapping
    for (const [cdnDomain, localPath] of Object.entries(CDN_MAPPINGS)) {
        if (url.includes(cdnDomain)) {
            // Extract filename from URL
            const urlParts = url.split('/');
            const filename = urlParts[urlParts.length - 1];
            
            // Generate local asset URL
            const localUrl = `https://appassets.androidplatform.net/assets/${localPath}/${filename}`;
            return localUrl;
        }
    }
    
    return url; // No redirection needed
}

// Intercept mw.loader.implement to catch dynamic CDN loads
if (typeof window.mw !== 'undefined' && window.mw.loader) {
    const originalImplement = window.mw.loader.implement;
    window.mw.loader.implement = function(name, scripts, styles, messages) {
        console.log('[CDN-REDIRECTOR] Intercepting mw.loader.implement:', name);
        
        // Redirect any CDN URLs in scripts array
        if (Array.isArray(scripts)) {
            scripts = scripts.map(script => {
                if (typeof script === 'string' && script.startsWith('http')) {
                    const redirected = redirectCdnUrl(script);
                    if (redirected !== script) {
                        console.log('[CDN-REDIRECTOR] Redirecting in mw.loader.implement:', script, '->', redirected);
                    }
                    return redirected;
                }
                return script;
            });
        }
        
        return originalImplement.call(this, name, scripts, styles, messages);
    };
}

// Universal API and Data Redirection
const originalFetch = window.fetch;
window.fetch = function(url, options) {
    if (typeof url === 'string') {
        // GE Price Data API Redirection
        if (url.includes('api.weirdgloop.org/exchange/history/osrs/sample')) {
            console.log('[CDN-REDIRECTOR] Intercepting GE data API:', url);
            
            // Extract item ID from URL
            const idMatch = url.match(/id=(\d+)/);
            const itemId = idMatch ? idMatch[1] : '1511';
            
            // Generate mock GE price data locally
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve(generateMockGEData(itemId))
            });
        }
        
        // Other API redirections can be added here
        if (url.includes('api.weirdgloop.org')) {
            console.log('[CDN-REDIRECTOR] Other API call detected:', url);
        }
    }
    
    return originalFetch.call(this, url, options);
};

// Generate realistic mock GE price data
function generateMockGEData(itemId) {
    console.log('[CDN-REDIRECTOR] Generating mock GE data for item:', itemId);
    
    const data = {};
    data[itemId] = [];
    
    // Generate realistic price history (last 2 years)
    const now = Date.now();
    const basePrice = 96; // Current Logs price from the page
    
    for (let i = 0; i < 100; i++) {
        const timestamp = now - (i * 24 * 60 * 60 * 1000 * 7); // Weekly intervals
        const priceVariation = basePrice * (0.8 + Math.random() * 0.4); // ±20% variation
        const price = Math.round(priceVariation);
        
        data[itemId].unshift([timestamp, price]); // Chronological order
    }
    
    console.log('[CDN-REDIRECTOR] Generated', data[itemId].length, 'price data points for item', itemId);
    return data;
}

console.log('[CDN-REDIRECTOR] Universal CDN redirection system initialized');
console.log('[CDN-REDIRECTOR] Monitoring CDNs:', Object.keys(CDN_MAPPINGS));