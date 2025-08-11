
// MediaWiki API Compatibility Layer
if (typeof window.mw === 'undefined') {
    window.mw = {
        config: {
            get: function(key, fallback) {
                const configs = {
                    'wgPageName': document.title || 'Unknown_Page',
                    'wgNamespaceNumber': 0,
                    'wgTitle': document.title || 'Unknown Page',
                    'wgUserGroups': ['*'],
                    'wgUserName': null
                };
                return configs[key] !== undefined ? configs[key] : fallback;
            }
        },
        loader: {
            using: function(modules, callback) {
                // Simple implementation - assume modules are already loaded
                if (typeof callback === 'function') {
                    setTimeout(callback, 0);
                }
                return Promise.resolve();
            },
            load: function(modules) {
                console.log('[MW-COMPAT] Module load requested:', modules);
            }
        },
        util: {
            getUrl: function(title, params) {
                // Basic URL construction for wiki links
                return '#' + encodeURIComponent(title.replace(/ /g, '_'));
            },
            addCSS: function(css) {
                const style = document.createElement('style');
                style.textContent = css;
                document.head.appendChild(style);
            }
        },
        message: function(key) {
            // Basic message implementation - return the key
            return {
                text: function() { return key; },
                parse: function() { return key; }
            };
        },
        cookie: {
            get: function(name, defaultValue) {
                const value = document.cookie.match('(^|;)\\s*' + name + '\\s*=\\s*([^;]+)');
                return value ? decodeURIComponent(value[2]) : defaultValue;
            },
            set: function(name, value, expires) {
                document.cookie = name + '=' + encodeURIComponent(value) + 
                    (expires ? '; expires=' + expires : '') + '; path=/';
            }
        },
        user: {
            getName: function() { return null; },
            isAnon: function() { return true; },
            options: {
                get: function(key, fallback) { return fallback; }
            }
        }
    };
}

// jQuery compatibility (if not already loaded)
if (typeof window.$ === 'undefined' && typeof window.jQuery !== 'undefined') {
    window.$ = window.jQuery;
}


// Basic jQuery-like functionality for simple cases
if (typeof window.$ === 'undefined') {
    window.$ = function(selector) {
        if (typeof selector === 'function') {
            // Document ready
            if (document.readyState === 'complete' || document.readyState === 'interactive') {
                setTimeout(selector, 0);
            } else {
                document.addEventListener('DOMContentLoaded', selector);
            }
            return;
        }
        // Basic element selection
        return {
            ready: function(fn) { $(fn); },
            length: 0,
            each: function() { return this; }
        };
    };
    window.jQuery = window.$;
}


// Adapted module: ext.gadget.GECharts
(function() {
'use strict';

$(function(){if($('.GEdatachart').length){mw.loader.load('ext.gadget.GECharts-core');}})


})();