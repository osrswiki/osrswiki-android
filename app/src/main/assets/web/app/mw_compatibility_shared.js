/**
 * Dynamic MediaWiki API Compatibility Layer
 * 
 * Provides MediaWiki API compatibility for extracted modules running in Android WebView.
 * Automatically reads deployment configuration to handle dynamic module loading.
 */

// Only create MW compatibility layer if it doesn't exist
if (typeof window.mw === 'undefined') {
    
    // Dynamic module registry - loaded from deployment config
    let MODULE_REGISTRY = {};
    
    // Load module registry from deployment report (synchronous to prevent race conditions)
    function loadModuleRegistry() {
        try {
            console.log('[MW-COMPAT] Loading module registry...');
            
            // Use synchronous XMLHttpRequest to ensure registry is ready before external modules load
            const xhr = new XMLHttpRequest();
            xhr.open('GET', 'https://appassets.androidplatform.net/assets/deployment_report.json', false); // false = synchronous
            xhr.send();
            
            if (xhr.status === 200) {
                const deploymentReport = JSON.parse(xhr.responseText);
                
                // Build module registry from deployed modules
                deploymentReport.deployed_modules.forEach(function(module) {
                    const extractedName = module.extracted_name.replace('.js', '').replace(/_/g, '.');
                    const deployedPath = 'web/external/' + module.deployed_name;
                    MODULE_REGISTRY[extractedName] = deployedPath;
                    console.log('[MW-COMPAT] Registered module:', extractedName, '->', deployedPath);
                });
                
                console.log('[MW-COMPAT] Module registry loaded with', Object.keys(MODULE_REGISTRY).length, 'modules');
            } else {
                throw new Error('Failed to load deployment report: HTTP ' + xhr.status);
            }
        } catch (error) {
            console.warn('[MW-COMPAT] Failed to load module registry:', error);
            // Fallback registry with known modules
            MODULE_REGISTRY = {
                'ext.gadget.GECharts-core': 'web/external/ge_charts_core.js',
                'ext.gadget.GECharts': 'web/external/ge_charts_loader.js',
                'ext.cite.ux-enhancements': 'web/external/citation_enhancements.js',
                'ext.Tabber': 'web/external/tabber.js'
            };
            console.log('[MW-COMPAT] Using fallback registry with', Object.keys(MODULE_REGISTRY).length, 'modules');
        }
    }
    
    // Load registry immediately and synchronously
    loadModuleRegistry();
    
    // Track loaded modules to prevent duplicate loading
    const loadedModules = new Set();
    
    window.mw = {
        config: {
            get: function(key, fallback) {
                const configs = {
                    'wgPageName': document.title || 'Unknown_Page',
                    'wgNamespaceNumber': 0,
                    'wgTitle': document.title || 'Unknown Page',
                    'wgUserGroups': ['*'],
                    'wgUserName': null,
                    'wgServer': 'https://oldschool.runescape.wiki',
                    'wgScriptPath': '/w',
                    'wgAction': 'view'
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
                // Handle dynamic module loading
                if (typeof modules === 'string') {
                    modules = [modules];
                }
                
                modules.forEach(function(moduleName) {
                    if (loadedModules.has(moduleName)) {
                        console.log('[MW-COMPAT] Module already loaded:', moduleName);
                        return;
                    }
                    
                    const assetPath = MODULE_REGISTRY[moduleName];
                    if (assetPath) {
                        console.log('[MW-COMPAT] Loading module:', moduleName, '->', assetPath);
                        
                        const script = document.createElement('script');
                        script.src = 'https://appassets.androidplatform.net/assets/' + assetPath;
                        script.onload = function() {
                            loadedModules.add(moduleName);
                            console.log('[MW-COMPAT] Module loaded successfully:', moduleName);
                        };
                        script.onerror = function() {
                            console.error('[MW-COMPAT] Failed to load module:', moduleName);
                        };
                        document.head.appendChild(script);
                    } else {
                        console.warn('[MW-COMPAT] Unknown module requested:', moduleName);
                    }
                });
            }
        },
        
        util: {
            getUrl: function(title, params) {
                // Basic URL construction for wiki links
                let url = 'https://oldschool.runescape.wiki/w/' + encodeURIComponent(title.replace(/ /g, '_'));
                if (params) {
                    const queryString = Object.keys(params)
                        .map(key => encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
                        .join('&');
                    url += '?' + queryString;
                }
                return url;
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
            
            set: function(name, value, options) {
                let cookie = encodeURIComponent(name) + '=' + encodeURIComponent(value);
                if (options && options.expires) {
                    cookie += '; expires=' + options.expires.toUTCString();
                }
                if (options && options.path) {
                    cookie += '; path=' + options.path;
                }
                document.cookie = cookie;
            }
        },
        
        user: {
            options: {
                get: function(key, fallback) { return fallback; }
            }
        }
    };
    
    console.log('[MW-COMPAT] MediaWiki compatibility layer initialized');
}

// Basic jQuery compatibility (if not already loaded)
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
        const elements = document.querySelectorAll(selector);
        return {
            length: elements.length,
            each: function(callback) {
                elements.forEach(callback);
                return this;
            },
            ready: function(fn) { $(fn); return this; }
        };
    };
    window.jQuery = window.$;
}