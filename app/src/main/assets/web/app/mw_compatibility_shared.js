/**
 * Enhanced MediaWiki API Compatibility Layer
 * 
 * Provides MediaWiki API compatibility for extracted modules running in Android WebView.
 * Automatically reads deployment configuration to handle dynamic module loading.
 * Includes special handling for external library dependencies like Highcharts.
 * 
 * IMPORTANT: This must load before any external modules to prevent race conditions.
 */

console.log('[MW-COMPAT] Starting MediaWiki compatibility layer initialization...');

// Prevent double-initialization with stronger checks
if (typeof window.mw === 'undefined') {
    
    // Provide minimal CommonJS compatibility for OOjs module
    if (typeof window.module === 'undefined') {
        window.module = { exports: {} };
    }
    
    // Mark that we're initializing to prevent other compatibility layers from interfering
    window.__mw_compat_initializing = true;
    
    // Dynamic module registry - loaded from deployment config
    let MODULE_REGISTRY = {};
    
    // Special module handlers for external libraries (using correct filenames)
    const SPECIAL_MODULES = {
        // Note: rs.highcharts is handled dynamically by mw.loader.implement() 
        'oojs': {
            script: 'web/external/oojs.js',
            provides: 'OO'
        },
        'oojs-ui-core': {
            script: 'web/external/oojs_ui_core.js',
            provides: 'OO.ui'
        }
    };
    
    // Load module registry from deployment report with robust error handling
    function loadModuleRegistry() {
        try {
            console.log('[MW-COMPAT] Loading module registry...');
            
            // Use synchronous XMLHttpRequest to ensure registry is ready before external modules load
            const xhr = new XMLHttpRequest();
            xhr.timeout = 5000; // 5 second timeout
            xhr.open('GET', 'https://appassets.androidplatform.net/assets/deployment_report.json', false); // false = synchronous
            
            // Set up error handlers
            xhr.onerror = function() {
                throw new Error('Network error loading deployment report');
            };
            xhr.ontimeout = function() {
                throw new Error('Timeout loading deployment report');
            };
            
            xhr.send();
            
            if (xhr.status === 200 && xhr.responseText) {
                try {
                    const deploymentReport = JSON.parse(xhr.responseText);
                    
                    if (deploymentReport.deployed_modules && Array.isArray(deploymentReport.deployed_modules)) {
                        // Build module registry from deployed modules
                        deploymentReport.deployed_modules.forEach(function(module) {
                            if (module.extracted_name && module.deployed_name) {
                                const extractedName = module.extracted_name.replace('.js', '').replace(/_/g, '.');
                                const deployedPath = 'web/external/' + module.deployed_name;
                                MODULE_REGISTRY[extractedName] = deployedPath;
                                console.log('[MW-COMPAT] Registered module:', extractedName, '->', deployedPath);
                            }
                        });
                        
                        console.log('[MW-COMPAT] Module registry loaded with', Object.keys(MODULE_REGISTRY).length, 'modules');
                    } else {
                        throw new Error('Invalid deployment report format - missing deployed_modules array');
                    }
                } catch (parseError) {
                    throw new Error('Failed to parse deployment report JSON: ' + parseError.message);
                }
            } else {
                throw new Error('Failed to load deployment report: HTTP ' + xhr.status + (xhr.statusText ? ' ' + xhr.statusText : ''));
            }
        } catch (error) {
            console.warn('[MW-COMPAT] Failed to load module registry:', error.message);
            // Comprehensive fallback registry with all known modules
            MODULE_REGISTRY = {
                'ext.gadget.GECharts-core': 'web/external/ge_charts_core.js',
                'ext.gadget.GECharts': 'web/external/ge_charts_loader.js',
                'ext.cite.ux-enhancements': 'web/external/citation_enhancements.js',
                'ext.Tabber': 'web/external/tabber.js'
            };
            console.log('[MW-COMPAT] Using fallback registry with', Object.keys(MODULE_REGISTRY).length, 'modules');
            console.log('[MW-COMPAT] Available modules:', Object.keys(MODULE_REGISTRY));
        }
    }
    
    // Load registry immediately and synchronously
    loadModuleRegistry();
    
    // Track loaded modules to prevent duplicate loading
    const loadedModules = new Set();
    
    // Track readiness state to handle race conditions
    let isFullyInitialized = false;
    const pendingModuleRequests = [];
    
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
                    'wgAction': 'view',
                    'wgSiteName': 'Old School RuneScape Wiki'
                };
                
                // Handle array of keys (MediaWiki supports this)
                if (Array.isArray(key)) {
                    const result = {};
                    key.forEach(function(k) {
                        result[k] = configs[k] !== undefined ? configs[k] : fallback;
                    });
                    return result;
                }
                
                return configs[key] !== undefined ? configs[key] : fallback;
            }
        },
        
        loader: {
            using: function(modules, callback) {
                // Handle module loading
                if (typeof modules === 'string') {
                    modules = [modules];
                }
                
                // Load all required modules
                const loadPromises = modules.map(function(moduleName) {
                    return new Promise(function(resolve) {
                        if (loadedModules.has(moduleName)) {
                            resolve();
                            return;
                        }
                        
                        // Check for special module handlers
                        if (SPECIAL_MODULES[moduleName]) {
                            const special = SPECIAL_MODULES[moduleName];
                            
                            // For rs.highcharts, wait for Highcharts to become available
                            if (moduleName === 'rs.highcharts') {
                                console.log('[MW-COMPAT] (using) Waiting for Highcharts to become available...');
                                
                                function waitForHighcharts() {
                                    if (typeof window.Highcharts !== 'undefined') {
                                        console.log('[MW-COMPAT] (using) Highcharts now available!');
                                        loadedModules.add(moduleName);
                                        resolve();
                                    } else {
                                        // Check every 50ms until Highcharts is available
                                        setTimeout(waitForHighcharts, 50);
                                    }
                                }
                                waitForHighcharts();
                                return;
                            }
                            
                            const script = document.createElement('script');
                            script.src = 'https://appassets.androidplatform.net/assets/' + special.script;
                            script.onload = function() {
                                loadedModules.add(moduleName);
                                console.log('[MW-COMPAT] Special module loaded:', moduleName);
                                resolve();
                            };
                            script.onerror = function() {
                                console.error('[MW-COMPAT] Failed to load special module:', moduleName);
                                resolve(); // Don't block other modules
                            };
                            document.head.appendChild(script);
                        } else {
                            // Assume module is already loaded
                            loadedModules.add(moduleName);
                            resolve();
                        }
                    });
                });
                
                // Execute callback when all modules are loaded
                Promise.all(loadPromises).then(function() {
                    if (typeof callback === 'function') {
                        // Create require function that can return loaded modules
                        function requireFunction(moduleName) {
                            // Special handling for rs.highcharts
                            if (moduleName === 'rs.highcharts') {
                                return window.Highcharts;
                            }
                            // For other modules, return a basic stub or undefined
                            console.log('[MW-COMPAT] Module required:', moduleName);
                            return undefined;
                        }
                        callback(requireFunction);
                    }
                });
                
                return Promise.all(loadPromises);
            },
            
            load: function(modules) {
                // Handle dynamic module loading with race condition protection
                if (typeof modules === 'string') {
                    modules = [modules];
                }
                
                // If not fully initialized, buffer the request
                if (!isFullyInitialized) {
                    console.log('[MW-COMPAT] System not ready, buffering module request:', modules);
                    pendingModuleRequests.push(modules);
                    return;
                }
                
                modules.forEach(function(moduleName) {
                    if (loadedModules.has(moduleName)) {
                        console.log('[MW-COMPAT] Module already loaded:', moduleName);
                        return;
                    }
                    
                    // Check for special module handlers first
                    if (SPECIAL_MODULES[moduleName]) {
                        const special = SPECIAL_MODULES[moduleName];
                        
                        // For rs.highcharts, check if Highcharts is already loaded by pre-scan
                        if (moduleName === 'rs.highcharts') {
                            console.log('[MW-COMPAT] Checking for existing Highcharts... window.Highcharts =', typeof window.Highcharts);
                            
                            // Check if Highcharts object exists OR if Highcharts script is already in DOM
                            const highchartsExists = typeof window.Highcharts !== 'undefined';
                            const highchartsScript = document.querySelector('script[src*="highcharts-stock.js"]');
                            
                            if (highchartsExists || highchartsScript) {
                                if (highchartsExists) {
                                    console.log('[MW-COMPAT] Highcharts already available from pre-scan, skipping rs.highcharts');
                                } else {
                                    console.log('[MW-COMPAT] Highcharts script already in DOM from pre-scan, skipping rs.highcharts');
                                }
                                loadedModules.add(moduleName);
                                return;
                            }
                        }
                        
                        
                        const script = document.createElement('script');
                        script.src = 'https://appassets.androidplatform.net/assets/' + special.script;
                        script.onload = function() {
                            loadedModules.add(moduleName);
                            console.log('[MW-COMPAT] Special module loaded:', moduleName);
                        };
                        script.onerror = function() {
                            console.error('[MW-COMPAT] Failed to load special module:', moduleName);
                        };
                        document.head.appendChild(script);
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
            },
            
            implement: function(name, scripts, styles, messages) {
                // Handle mw.loader.implement calls for dynamic registration
                console.log('[MW-COMPAT] Module implement called:', name);
                
                // For rs.highcharts, allow the GE charts module to dynamically implement it
                if (name === 'rs.highcharts') {
                    console.log('[MW-COMPAT] Intercepting dynamic rs.highcharts implementation');
                    
                    // Instead of loading from CDN, use our local Highcharts asset
                    const localHighchartsUrl = 'https://appassets.androidplatform.net/assets/web/external/highcharts-stock.js';
                    console.log('[MW-COMPAT] Redirecting Highcharts to local asset:', localHighchartsUrl);
                    
                    const script = document.createElement('script');
                    script.src = localHighchartsUrl;
                    script.onload = function() {
                        loadedModules.add(name);
                        console.log('[MW-COMPAT] rs.highcharts module dynamically implemented using local asset');
                        // Set module state to ready
                        window.mw.loader.state({[name]: 'ready'});
                    };
                    script.onerror = function() {
                        console.error('[MW-COMPAT] Failed to load rs.highcharts from local asset:', localHighchartsUrl);
                    };
                    document.head.appendChild(script);
                    return;
                }
                
                // For other modules, try to load scripts if provided
                if (Array.isArray(scripts)) {
                    scripts.forEach(function(scriptUrl) {
                        if (typeof scriptUrl === 'string' && scriptUrl.startsWith('http')) {
                            console.log('[MW-COMPAT] Loading external script:', scriptUrl);
                            const script = document.createElement('script');
                            script.src = scriptUrl;
                            script.onload = function() {
                                loadedModules.add(name);
                                window.mw.loader.state({[name]: 'ready'});
                            };
                            document.head.appendChild(script);
                        }
                    });
                }
            },
            
            getState: function(moduleName) {
                // Return module state for dependency checking
                if (loadedModules.has(moduleName)) {
                    return 'ready';
                }
                if (SPECIAL_MODULES[moduleName] || MODULE_REGISTRY[moduleName]) {
                    return 'registered';
                }
                return null;
            },
            
            state: function(moduleStates) {
                // Handle mw.loader.state() calls from modules
                if (typeof moduleStates === 'object') {
                    Object.keys(moduleStates).forEach(function(moduleName) {
                        const state = moduleStates[moduleName];
                        if (state === 'ready') {
                            loadedModules.add(moduleName);
                            console.log('[MW-COMPAT] Module state set to ready:', moduleName);
                        }
                    });
                } else if (typeof moduleStates === 'string') {
                    // Single module state query
                    return this.getState(moduleStates);
                }
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
        },
        
        // Additional methods needed by OOUI
        language: {
            getFallbackLanguageChain: function() {
                return ['en']; // Default to English
            }
        },
        
        msg: function(key) {
            // Simple message function - just return the key
            return key;
        },
        
        track: function(topic, data) {
            // Analytics tracking - just log for debugging
            console.log('[MW-COMPAT] Track:', topic, data);
        },
        
        log: {
            warn: function(message) {
                console.warn('[MW-COMPAT]', message);
            }
        }
    };
    
    // Process any buffered module requests that came in during initialization
    function processPendingRequests() {
        if (pendingModuleRequests.length > 0) {
            console.log('[MW-COMPAT] Processing', pendingModuleRequests.length, 'buffered module requests');
            const requests = [...pendingModuleRequests];  // Copy the array
            pendingModuleRequests.length = 0;  // Clear the buffer
            
            requests.forEach(function(modules) {
                window.mw.loader.load(modules);  // Recursive call, but now isFullyInitialized = true
            });
        }
    }
    
    // Mark system as fully initialized and process any pending requests
    isFullyInitialized = true;
    processPendingRequests();
    
    // Clear initialization flag
    delete window.__mw_compat_initializing;
    
    console.log('[MW-COMPAT] Enhanced MediaWiki compatibility layer fully initialized');
    console.log('[MW-COMPAT] Available modules:', Object.keys(MODULE_REGISTRY));
    console.log('[MW-COMPAT] Special modules:', Object.keys(SPECIAL_MODULES));
} else {
    console.log('[MW-COMPAT] MediaWiki object already exists - skipping initialization');
    if (window.__mw_compat_initializing) {
        console.warn('[MW-COMPAT] Another compatibility layer is initializing - this could cause race conditions');
    }
}

// Basic jQuery compatibility (if not already loaded)
if (typeof window.$ === 'undefined' && typeof window.jQuery !== 'undefined') {
    window.$ = window.jQuery;
}

// Enhanced jQuery-like functionality
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
        
        // Enhanced element selection
        const elements = document.querySelectorAll(selector);
        return {
            length: elements.length,
            each: function(callback) {
                for (let i = 0; i < elements.length; i++) {
                    callback.call(elements[i], i, elements[i]);
                }
                return this;
            },
            ready: function(fn) { $(fn); return this; },
            find: function(childSelector) {
                const results = [];
                elements.forEach(function(el) {
                    const children = el.querySelectorAll(childSelector);
                    results.push(...children);
                });
                return $(results);
            },
            attr: function(name, value) {
                if (typeof value === 'function') {
                    elements.forEach(function(el, index) {
                        el.setAttribute(name, value.call(el, index, el.getAttribute(name)));
                    });
                } else if (value !== undefined) {
                    elements.forEach(function(el) {
                        el.setAttribute(name, value);
                    });
                } else {
                    return elements[0] ? elements[0].getAttribute(name) : undefined;
                }
                return this;
            },
            hasClass: function(className) {
                return elements[0] ? elements[0].classList.contains(className) : false;
            },
            before: function(content) {
                elements.forEach(function(el) {
                    if (typeof content === 'string') {
                        el.insertAdjacentHTML('beforebegin', content);
                    } else if (content.nodeType) {
                        el.parentNode.insertBefore(content, el);
                    }
                });
                return this;
            }
        };
    };
    window.jQuery = window.$;
}