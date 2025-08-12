/**
 * jQuery Debug Script
 * Check jQuery availability for Highcharts plugin
 */

console.log('[JQUERY-DEBUG] === jQuery Availability Debug ===');
console.log('[JQUERY-DEBUG] typeof window.jQuery:', typeof window.jQuery);
console.log('[JQUERY-DEBUG] typeof window.$:', typeof window.$);
console.log('[JQUERY-DEBUG] jQuery is:', window.jQuery);
console.log('[JQUERY-DEBUG] $ is:', window.$);

if (window.jQuery) {
    console.log('[JQUERY-DEBUG] jQuery.fn exists:', typeof window.jQuery.fn);
    console.log('[JQUERY-DEBUG] jQuery.fn.jquery (version):', window.jQuery.fn.jquery);
} else {
    console.log('[JQUERY-DEBUG] jQuery not available!');
}

// Check if we can safely assign to jQuery.fn.highcharts
try {
    if (window.jQuery && window.jQuery.fn) {
        console.log('[JQUERY-DEBUG] Can assign to jQuery.fn.highcharts - test assignment...');
        window.jQuery.fn.testProperty = 'test';
        console.log('[JQUERY-DEBUG] Test assignment successful, value:', window.jQuery.fn.testProperty);
        delete window.jQuery.fn.testProperty;
    } else {
        console.log('[JQUERY-DEBUG] Cannot assign to jQuery.fn - jQuery or jQuery.fn is undefined');
    }
} catch (error) {
    console.log('[JQUERY-DEBUG] Error testing jQuery.fn assignment:', error.message);
}

console.log('[JQUERY-DEBUG] === End jQuery Availability Debug ===');