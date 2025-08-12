/**
 * jQuery Status Debug Script
 * Comprehensive jQuery initialization debugging
 */

console.log('[JQUERY-STATUS] === jQuery Status Debug ===');

// Check if jQuery is starting to load
console.log('[JQUERY-STATUS] Document readyState:', document.readyState);

// Override error handler temporarily to catch jQuery errors
var originalOnError = window.onerror;
window.onerror = function(message, source, lineno, colno, error) {
    if (source && source.includes('jquery')) {
        console.log('[JQUERY-STATUS] jQuery ERROR:', message, 'at', source + ':' + lineno);
    }
    if (originalOnError) {
        return originalOnError.apply(this, arguments);
    }
};

// Monitor jQuery constructor getting set
var jquerySetCount = 0;
Object.defineProperty(window, 'jQuery', {
    get: function() {
        return this._jQuery;
    },
    set: function(value) {
        jquerySetCount++;
        console.log('[JQUERY-STATUS] jQuery set attempt #' + jquerySetCount + ', typeof:', typeof value);
        
        if (value && typeof value === 'function') {
            console.log('[JQUERY-STATUS] jQuery function set, checking prototype...');
            setTimeout(function() {
                console.log('[JQUERY-STATUS] jQuery.fn after set:', typeof window.jQuery.fn);
                console.log('[JQUERY-STATUS] jQuery.prototype after set:', typeof window.jQuery.prototype);
                if (window.jQuery.fn) {
                    console.log('[JQUERY-STATUS] jQuery.fn.jquery:', window.jQuery.fn.jquery);
                } else if (window.jQuery.prototype) {
                    console.log('[JQUERY-STATUS] jQuery.prototype.jquery:', window.jQuery.prototype.jquery);
                }
            }, 0);
        }
        
        this._jQuery = value;
    },
    enumerable: true,
    configurable: true
});

console.log('[JQUERY-STATUS] === jQuery monitoring setup complete ===');