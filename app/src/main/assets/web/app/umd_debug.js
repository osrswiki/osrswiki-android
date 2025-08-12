/**
 * UMD Debug Script
 * Intercept and debug the exact UMD pattern execution
 */

console.log('[UMD-DEBUG] === UMD Interception Debug ===');

// Store original Highcharts error function if it exists
var originalError = null;
if (window.Highcharts && typeof window.Highcharts.error === 'function') {
    originalError = window.Highcharts.error;
    console.log('[UMD-DEBUG] Found existing Highcharts.error function');
}

// Monkey patch the global assignment to see what's happening
var originalWindowHighcharts = window.Highcharts;
console.log('[UMD-DEBUG] Original window.Highcharts:', typeof originalWindowHighcharts);

// Override the window object's Highcharts setter to debug the assignment
Object.defineProperty(window, 'Highcharts', {
    get: function() {
        return this._highcharts;
    },
    set: function(value) {
        console.log('[UMD-DEBUG] Attempting to set window.Highcharts to:', typeof value);
        console.log('[UMD-DEBUG] Current context (this):', typeof this);
        console.log('[UMD-DEBUG] Context is window:', this === window);
        console.log('[UMD-DEBUG] Context is undefined:', this === undefined);
        console.log('[UMD-DEBUG] Context is null:', this === null);
        
        try {
            this._highcharts = value;
            console.log('[UMD-DEBUG] Successfully set window.Highcharts');
        } catch (error) {
            console.log('[UMD-DEBUG] Failed to set window.Highcharts:', error.message);
            throw error;
        }
    },
    enumerable: true,
    configurable: true
});

// Restore the original value if it existed
if (originalWindowHighcharts) {
    window.Highcharts = originalWindowHighcharts;
    console.log('[UMD-DEBUG] Restored original Highcharts');
}

console.log('[UMD-DEBUG] === UMD interception ready ===');