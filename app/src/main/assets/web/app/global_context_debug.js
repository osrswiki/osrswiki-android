/**
 * Global Context Diagnostic Script
 * Debug WebView global context to understand UMD failure
 */

console.log('[GLOBAL-DEBUG] === Global Context Diagnostic ===');
console.log('[GLOBAL-DEBUG] typeof window:', typeof window);
console.log('[GLOBAL-DEBUG] window === undefined:', window === undefined);
console.log('[GLOBAL-DEBUG] window === null:', window === null);
console.log('[GLOBAL-DEBUG] typeof this:', typeof this);
console.log('[GLOBAL-DEBUG] this === undefined:', this === undefined);
console.log('[GLOBAL-DEBUG] this === null:', this === null);
console.log('[GLOBAL-DEBUG] this === window:', this === window);
console.log('[GLOBAL-DEBUG] typeof globalThis:', typeof globalThis);
console.log('[GLOBAL-DEBUG] globalThis === window:', typeof globalThis !== 'undefined' && globalThis === window);

// Test the UMD condition
var umdContext = ("undefined" != typeof window ? window : this);
console.log('[GLOBAL-DEBUG] UMD detected context:', typeof umdContext);
console.log('[GLOBAL-DEBUG] UMD context === undefined:', umdContext === undefined);
console.log('[GLOBAL-DEBUG] UMD context === null:', umdContext === null);

// Test property setting
console.log('[GLOBAL-DEBUG] Testing property assignment...');
try {
    umdContext.testProperty = 'test-value';
    console.log('[GLOBAL-DEBUG] Property assignment successful, value:', umdContext.testProperty);
    delete umdContext.testProperty;
} catch (error) {
    console.log('[GLOBAL-DEBUG] Property assignment failed:', error.message);
}

console.log('[GLOBAL-DEBUG] === End Global Context Diagnostic ===');