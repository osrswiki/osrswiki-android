/**
 * MediaWiki API Compatibility Layer for OSRS Wiki Android App
 * 
 * Provides essential MediaWiki JavaScript APIs for standalone use in the Android app.
 * This allows wiki gadgets and extensions to work without the full MediaWiki environment.
 * 
 * Usage:
 *   Include this file before loading any adapted MediaWiki modules.
 *   The compatibility layer will create window.mw and window.$ objects as needed.
 * 
 * Features:
 *   - Core MediaWiki APIs (mw.config, mw.loader, mw.util, etc.)
 *   - Basic jQuery compatibility 
 *   - App-specific bridge integration
 *   - Fallback implementations for missing functionality
 */

(function() {
    'use strict';
    
    const LOG_TAG = 'MW-Compat';
    
    function log(level, msg, obj) {
        try {
            const text = `[${LOG_TAG}] ${msg}`;
            if (level === 'e') console.error(text, obj || '');
            else if (level === 'w') console.warn(text, obj || '');
            else console.log(text, obj || '');
        } catch (e) {}
    }
    
    // Skip initialization if already loaded
    if (window.mwCompatibilityLoaded) {
        log('i', 'MediaWiki compatibility layer already loaded');
        return;
    }
    
    // =================
    // MediaWiki Object
    // =================
    
    if (typeof window.mw === 'undefined') {
        window.mw = {};
    }
    
    // Configuration API
    window.mw.config = window.mw.config || {
        values: {
            // Basic wiki configuration
            'wgSiteName': 'Old School RuneScape Wiki',
            'wgScriptPath': '/w',
            'wgArticlePath': '/w/$1',
            'wgPageName': (document.title || 'Main_Page').replace(/ /g, '_'),
            'wgTitle': document.title || 'Main Page',
            'wgNamespaceNumber': 0,
            'wgAction': 'view',
            'wgUserGroups': ['*'],
            'wgUserName': null,
            'wgUserId': 0,
            'wgUserLanguage': 'en',
            'wgContentLanguage': 'en',
            'skin': 'vector',
            'stylepath': '/w/skins',
            'wgVersion': '1.35.0',
            'wgEnableAPI': true,
            'wgServer': 'https://oldschool.runescape.wiki',
            'wgServerName': 'oldschool.runescape.wiki',
            'wgDBname': 'osrswiki',
            // App-specific config
            'osmw-app-mode': true,
            'osmw-mobile': true
        },
        
        get: function(key, fallback) {
            const value = this.values[key];
            return value !== undefined ? value : fallback;
        },
        
        set: function(key, value) {
            this.values[key] = value;
        },
        
        exists: function(key) {
            return key in this.values;
        }
    };
    
    // Loader API (for module dependencies)
    window.mw.loader = window.mw.loader || {
        moduleRegistry: {},
        
        using: function(modules, callback) {
            log('i', 'Module load requested: ' + (Array.isArray(modules) ? modules.join(', ') : modules));
            
            // Simple implementation - assume modules are loaded or not critical
            if (typeof callback === 'function') {
                // Execute callback asynchronously to match MediaWiki behavior
                setTimeout(callback, 0);
            }
            
            // Return a promise for modern usage
            return new Promise(function(resolve) {
                setTimeout(resolve, 0);
            });
        },
        
        load: function(modules) {
            log('i', 'Module pre-load requested: ' + (Array.isArray(modules) ? modules.join(', ') : modules));
        },
        
        getState: function(module) {
            // Assume all modules are ready for simplicity
            return 'ready';
        },
        
        implement: function(module, version, dependencies, group, source) {
            // Basic module implementation tracking
            this.moduleRegistry[module] = {
                version: version,
                dependencies: dependencies,
                group: group,
                state: 'ready'
            };
            
            // Execute the source if it's a function
            if (typeof source === 'function') {
                try {
                    source();
                } catch (e) {
                    log('e', 'Error executing module ' + module, e);
                }
            }
        }
    };
    
    // Utility functions
    window.mw.util = window.mw.util || {
        getUrl: function(title, params) {
            // Basic URL construction for wiki links
            if (!title) return '#';
            
            let url = '/w/' + encodeURIComponent(title.replace(/ /g, '_'));
            
            if (params && typeof params === 'object') {
                const queryParams = [];
                for (const key in params) {
                    if (params.hasOwnProperty(key)) {
                        queryParams.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]));
                    }
                }
                if (queryParams.length > 0) {
                    url += '?' + queryParams.join('&');
                }
            }
            
            return url;
        },
        
        addCSS: function(css) {
            if (!css) return;
            
            const style = document.createElement('style');
            style.type = 'text/css';
            style.textContent = css;
            document.head.appendChild(style);
            
            log('i', 'Added CSS styles (' + css.length + ' chars)');
            return style;
        },
        
        getParamValue: function(param, url) {
            url = url || window.location.href;
            const regex = new RegExp('[?&]' + param + '=([^&#]*)', 'i');
            const results = regex.exec(url);
            return results ? decodeURIComponent(results[1]) : null;
        },
        
        wikiScript: function(script) {
            script = script || 'index';
            return window.mw.config.get('wgScriptPath') + '/' + script + '.php';
        },
        
        // DOM ready functionality
        $content: null // Will be set when jQuery is available
    };
    
    // Message/internationalization API  
    window.mw.message = function(key) {
        // Basic message implementation
        const messages = {
            // Common messages that might be used by gadgets
            'show': 'Show',
            'hide': 'Hide',
            'expand': 'Expand',
            'collapse': 'Collapse',
            'loading': 'Loading...',
            'error': 'Error',
            'ok': 'OK',
            'cancel': 'Cancel',
            'yes': 'Yes',
            'no': 'No'
        };
        
        const messageText = messages[key] || key;
        
        return {
            text: function() { 
                return messageText; 
            },
            parse: function() { 
                return messageText; 
            },
            plain: function() { 
                return messageText; 
            },
            exists: function() {
                return key in messages;
            }
        };
    };
    
    // Cookie utilities
    window.mw.cookie = window.mw.cookie || {
        get: function(name, prefix, defaultValue) {
            const fullName = (prefix || 'mw') + name;
            const value = document.cookie.match('(^|;)\\\\s*' + fullName + '\\\\s*=\\\\s*([^;]+)');
            return value ? decodeURIComponent(value[2]) : defaultValue;
        },
        
        set: function(name, value, expires, options) {
            const fullName = (options && options.prefix || 'mw') + name;
            let cookie = fullName + '=' + encodeURIComponent(value);
            
            if (expires) {
                const date = new Date();
                date.setTime(date.getTime() + expires * 1000);
                cookie += '; expires=' + date.toUTCString();
            }
            
            cookie += '; path=/';
            if (options && options.domain) {
                cookie += '; domain=' + options.domain;
            }
            
            document.cookie = cookie;
        }
    };
    
    // User information API
    window.mw.user = window.mw.user || {
        getName: function() { 
            return window.mw.config.get('wgUserName'); 
        },
        
        getId: function() { 
            return window.mw.config.get('wgUserId', 0); 
        },
        
        isAnon: function() { 
            return !this.getName(); 
        },
        
        getGroups: function() {
            return window.mw.config.get('wgUserGroups', ['*']);
        },
        
        options: {
            get: function(key, fallback) {
                // Basic user preferences - could be extended with app-specific storage
                const defaults = {
                    'language': 'en',
                    'variant': 'en',
                    'skin': 'vector'
                };
                return defaults[key] !== undefined ? defaults[key] : fallback;
            }
        }
    };
    
    // API interface (basic implementation)
    window.mw.Api = function(options) {
        options = options || {};
        
        return {
            get: function(params) {
                log('i', 'API GET request', params);
                // Return a promise that resolves with basic structure
                return Promise.resolve({
                    query: {},
                    warnings: []
                });
            },
            
            post: function(params) {
                log('i', 'API POST request', params);
                return Promise.resolve({
                    success: true
                });
            }
        };
    };
    
    // =================
    // jQuery Compatibility
    // =================
    
    // Basic jQuery-like functionality for simple cases
    function createBasicJQuery() {
        const $ = function(selector) {
            // Handle different selector types
            if (typeof selector === 'function') {
                // Document ready
                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    setTimeout(selector, 0);
                } else {
                    document.addEventListener('DOMContentLoaded', selector);
                }
                return $;
            }
            
            if (typeof selector === 'string') {
                // Element selection
                const elements = document.querySelectorAll(selector);
                const jqObject = {
                    length: elements.length,
                    elements: elements,
                    
                    // Basic methods
                    each: function(callback) {
                        Array.prototype.forEach.call(elements, callback);
                        return this;
                    },
                    
                    on: function(event, handler) {
                        this.each(function(element) {
                            element.addEventListener(event, handler);
                        });
                        return this;
                    },
                    
                    off: function(event, handler) {
                        this.each(function(element) {
                            element.removeEventListener(event, handler);
                        });
                        return this;
                    },
                    
                    find: function(childSelector) {
                        const found = [];
                        this.each(function(element) {
                            const children = element.querySelectorAll(childSelector);
                            Array.prototype.push.apply(found, children);
                        });
                        return $(found);
                    },
                    
                    addClass: function(className) {
                        this.each(function(element) {
                            element.classList.add(className);
                        });
                        return this;
                    },
                    
                    removeClass: function(className) {
                        this.each(function(element) {
                            element.classList.remove(className);
                        });
                        return this;
                    },
                    
                    hasClass: function(className) {
                        return elements.length > 0 && elements[0].classList.contains(className);
                    },
                    
                    text: function(value) {
                        if (value !== undefined) {
                            this.each(function(element) {
                                element.textContent = value;
                            });
                            return this;
                        } else {
                            return elements.length > 0 ? elements[0].textContent : '';
                        }
                    },
                    
                    html: function(value) {
                        if (value !== undefined) {
                            this.each(function(element) {
                                element.innerHTML = value;
                            });
                            return this;
                        } else {
                            return elements.length > 0 ? elements[0].innerHTML : '';
                        }
                    },
                    
                    hide: function() {
                        this.each(function(element) {
                            element.style.display = 'none';
                        });
                        return this;
                    },
                    
                    show: function() {
                        this.each(function(element) {
                            element.style.display = '';
                        });
                        return this;
                    },
                    
                    toggle: function() {
                        this.each(function(element) {
                            if (element.style.display === 'none') {
                                element.style.display = '';
                            } else {
                                element.style.display = 'none';
                            }
                        });
                        return this;
                    }
                };
                
                return jqObject;
            }
            
            // Handle element objects
            if (selector && (selector.nodeType || selector === window || selector === document)) {
                return $([selector]);
            }
            
            // Handle arrays
            if (Array.isArray(selector)) {
                return {
                    length: selector.length,
                    elements: selector,
                    each: function(callback) {
                        Array.prototype.forEach.call(selector, callback);
                        return this;
                    }
                };
            }
            
            // Fallback
            return {
                length: 0,
                elements: [],
                each: function() { return this; }
            };
        };
        
        // Add static methods
        $.extend = function(target) {
            Array.prototype.slice.call(arguments, 1).forEach(function(source) {
                if (source) {
                    for (const key in source) {
                        if (source.hasOwnProperty(key)) {
                            target[key] = source[key];
                        }
                    }
                }
            });
            return target;
        };
        
        $.ready = function(callback) {
            $(callback);
        };
        
        return $;
    }
    
    // Set up jQuery compatibility
    if (typeof window.$ === 'undefined') {
        if (typeof window.jQuery !== 'undefined') {
            window.$ = window.jQuery;
            log('i', 'Using existing jQuery as $');
        } else {
            window.$ = createBasicJQuery();
            window.jQuery = window.$;
            log('i', 'Created basic jQuery compatibility layer');
        }
    }
    
    // Set mw.util.$content when DOM is ready
    $(function() {
        if (window.mw && window.mw.util) {
            window.mw.util.$content = $('#mw-content-text, .mw-parser-output, body').first();
        }
    });
    
    // =================
    // App-specific Integration  
    // =================
    
    // Hook into existing app bridge if available
    if (window.OsrsWikiBridge) {
        // Extend mw.util with app-specific functionality
        window.mw.util.appBridge = window.OsrsWikiBridge;
        
        // Add app-specific config values
        window.mw.config.set('osmw-bridge-available', true);
        
        log('i', 'Integrated with OSRS Wiki Bridge');
    }
    
    // Expose utility for gadgets to check app mode
    window.mw.isAppMode = function() {
        return window.mw.config.get('osmw-app-mode', false);
    };
    
    // =================
    // Initialization Complete
    // =================
    
    window.mwCompatibilityLoaded = true;
    log('i', 'MediaWiki compatibility layer initialized');
    
    // Fire a ready event for gadgets that might be waiting
    $(function() {
        $(document).trigger('mwCompatibilityReady');
    });
    
})();