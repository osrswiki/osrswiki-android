/**
 * Enhanced Module Executor for MediaWiki Gadgets
 * 
 * This script extends the package module executor to handle:
 * 1. Package modules with require/exports
 * 2. URL-based module implementation (for external scripts)
 * 3. Gadget module registration and execution
 */

(function() {
    'use strict';
    
    console.log('[ENHANCED-EXECUTOR] Initializing enhanced module executor');
    
    // Module registry to track loaded modules
    const moduleRegistry = {};
    const urlModules = {};
    const loadingModules = {};
    
    // Create a module context with require/exports support
    function createModuleContext(moduleName, files) {
        const moduleExports = {};
        const module = { exports: moduleExports };
        
        // Create a require function for this module
        function require(path) {
            // Check if it's a registered module
            if (moduleRegistry[path]) {
                return moduleRegistry[path];
            }
            
            // Check if it's a URL module that's been loaded
            if (urlModules[path]) {
                return urlModules[path];
            }
            
            // Handle relative paths
            if (path.startsWith('./')) {
                const fileName = path.substring(2);
                
                // Check if it's a file in the module's files object
                if (files && files[fileName]) {
                    // Execute the file and return its exports
                    const fileModule = { exports: {} };
                    const fileExports = fileModule.exports;
                    
                    // Execute the file function
                    if (typeof files[fileName] === 'function') {
                        files[fileName].call(window, require, fileModule, fileExports);
                        return fileModule.exports;
                    }
                    
                    // If it's not a function, return it directly (e.g., JSON)
                    return files[fileName];
                }
            }
            
            console.warn('[MODULE-EXECUTOR] Module not found:', path);
            return {};
        }
        
        return { require, module, exports: moduleExports };
    }
    
    // Execute a package module
    function executePackageModule(moduleName, moduleData) {
        console.log('[ENHANCED-EXECUTOR] Executing package module:', moduleName);
        
        const mainFile = moduleData.main || 'index.js';
        const files = moduleData.files || {};
        
        // Check if main file exists
        if (!files[mainFile]) {
            console.error('[ENHANCED-EXECUTOR] Main file not found:', mainFile);
            return null;
        }
        
        // Create module context
        const context = createModuleContext(moduleName, files);
        
        // Execute the main file
        try {
            if (typeof files[mainFile] === 'function') {
                files[mainFile].call(window, context.require, context.module, context.exports);
                
                // Store the module's exports
                moduleRegistry[moduleName] = context.module.exports;
                
                console.log('[ENHANCED-EXECUTOR] Module executed successfully:', moduleName);
                return context.module.exports;
            }
        } catch (error) {
            console.error('[ENHANCED-EXECUTOR] Error executing module:', moduleName, error);
        }
        
        return null;
    }
    
    // Queue and load scripts sequentially like MediaWiki's queueModuleScript
    function queueModuleScript(scripts, moduleName, callback) {
        console.log('[ENHANCED-EXECUTOR] Queueing scripts for module:', moduleName, scripts);
        
        let currentIndex = 0;
        
        function loadNextScript() {
            if (currentIndex >= scripts.length) {
                console.log('[ENHANCED-EXECUTOR] All scripts loaded for module:', moduleName);
                // Mark module as ready - don't store the result, just indicate loaded
                urlModules[moduleName] = true;
                if (callback) callback();
                return;
            }
            
            const url = scripts[currentIndex];
            console.log('[ENHANCED-EXECUTOR] Loading script:', url, 'for module:', moduleName);
            
            const script = document.createElement('script');
            script.src = url;
            
            script.onload = function() {
                console.log('[ENHANCED-EXECUTOR] Script loaded:', url);
                currentIndex++;
                loadNextScript();
            };
            
            script.onerror = function() {
                console.error('[ENHANCED-EXECUTOR] Failed to load script:', url);
                currentIndex++;
                loadNextScript(); // Continue with next script even if one fails
            };
            
            document.head.appendChild(script);
        }
        
        loadNextScript();
    }
    
    // Override mw.loader.implement to handle URL-based modules
    if (window.mw && window.mw.loader) {
        const originalImplement = window.mw.loader.implement;
        
        window.mw.loader.implement = function(moduleName, scripts, styles, messages) {
            console.log('[ENHANCED-EXECUTOR] Intercepting mw.loader.implement for:', moduleName);
            
            // Check if module is already being loaded to prevent duplicates
            if (loadingModules[moduleName]) {
                console.log('[ENHANCED-EXECUTOR] Module already being loaded:', moduleName);
                return;
            }
            
            // Check if scripts is an array of URLs
            if (Array.isArray(scripts) && scripts.length > 0 && typeof scripts[0] === 'string') {
                console.log('[ENHANCED-EXECUTOR] Registering URL-based module:', moduleName, scripts);
                
                // Mark as loading to prevent duplicates
                loadingModules[moduleName] = new Promise((resolve, reject) => {
                    queueModuleScript(scripts, moduleName, () => {
                        console.log('[ENHANCED-EXECUTOR] URL module loading complete:', moduleName);
                        resolve(true);
                    });
                });
                
                // Register with ResourceLoader using empty function (scripts handled separately)
                if (originalImplement) {
                    originalImplement.call(this, moduleName, function() {
                        // Empty function - actual loading handled by queueModuleScript
                    }, styles, messages);
                }
                
                return;
            }
            
            // For non-URL modules, use original implementation
            if (originalImplement) {
                originalImplement.apply(this, arguments);
            }
        };
        
        // Override mw.loader.impl for package modules
        const originalImpl = window.mw.loader.impl;
        
        window.mw.loader.impl = function(declarator) {
            console.log('[ENHANCED-EXECUTOR] Intercepting mw.loader.impl call');
            
            // Call the original impl if it exists
            if (originalImpl) {
                originalImpl.call(this, declarator);
            }
            
            // Also execute the module immediately
            try {
                const data = declarator();
                const moduleKey = data[0]; // e.g., "mediawiki.base@version"
                const moduleName = moduleKey.split('@')[0];
                const moduleData = data[1];
                
                if (moduleData && typeof moduleData === 'object' && moduleData.files) {
                    console.log('[ENHANCED-EXECUTOR] Executing package module:', moduleName);
                    
                    // Execute the package module
                    const exports = executePackageModule(moduleName, moduleData);
                    
                    // For mediawiki.base, the module defines mw.loader.using directly
                    if (moduleName === 'mediawiki.base') {
                        console.log('[ENHANCED-EXECUTOR] Checking if mw.loader.using is now available...');
                        if (typeof window.mw.loader.using === 'function') {
                            console.log('[ENHANCED-EXECUTOR] SUCCESS: mw.loader.using is now available!');
                        }
                    }
                }
            } catch (error) {
                console.error('[ENHANCED-EXECUTOR] Error processing module:', error);
            }
        };
        
        // Enhance mw.loader.using to handle URL modules
        // NOTE: Don't save originalUsing immediately - mw.loader.using might not exist yet
        let originalUsing = null;
        let originalUsingSaved = false;
        
        window.mw.loader.using = function(dependencies, ready, error) {
            console.log('[ENHANCED-EXECUTOR] mw.loader.using called with:', dependencies);
            
            // Save originalUsing the first time we're called (when mw.loader.using actually exists)
            if (!originalUsingSaved) {
                // Look for the real mw.loader.using function on the prototype or in the MediaWiki loader
                if (window.mw && window.mw.loader && window.mw.loader.constructor && window.mw.loader.constructor.prototype.using) {
                    originalUsing = window.mw.loader.constructor.prototype.using;
                    console.log('[ENHANCED-EXECUTOR] Saved originalUsing from prototype:', typeof originalUsing);
                } else if (window.mw && window.mw.loader && window.mw.loader.using && window.mw.loader.using !== arguments.callee) {
                    originalUsing = window.mw.loader.using;
                    console.log('[ENHANCED-EXECUTOR] Saved originalUsing from current:', typeof originalUsing);
                }
                originalUsingSaved = true;
            }
            
            // Convert to array if needed
            if (!Array.isArray(dependencies)) {
                dependencies = [dependencies];
            }
            
            // Check if any dependencies are URL modules that need loading
            const urlDeps = dependencies.filter(dep => loadingModules[dep]);
            
            if (urlDeps.length > 0) {
                console.log('[ENHANCED-EXECUTOR] Waiting for URL modules:', urlDeps);
                
                // Wait for URL modules to load
                Promise.all(urlDeps.map(dep => loadingModules[dep])).then(() => {
                    console.log('[ENHANCED-EXECUTOR] URL modules loaded, calling callback with enhanced require');
                    
                    // Create require function that returns global exports (like real MediaWiki)
                    const enhancedRequire = function(moduleName) {
                        console.log('[ENHANCED-EXECUTOR] require() called for:', moduleName);
                        
                        // Handle specific known modules that set global variables
                        if (moduleName === 'rs.highcharts') {
                            console.log('[ENHANCED-EXECUTOR] Returning window.Highcharts:', window.Highcharts);
                            return window.Highcharts;
                        }
                        
                        // Check if it's a loaded URL module
                        if (urlModules[moduleName]) {
                            // For URL modules, check common global variable patterns
                            const globalName = moduleName.replace(/^ext\.gadget\./, '').replace(/^rs\./, '');
                            if (window[globalName]) {
                                return window[globalName];
                            }
                            if (window[moduleName]) {
                                return window[moduleName];
                            }
                            // Return true to indicate module is loaded
                            return true;
                        }
                        
                        // Fall back to regular require
                        if (window.mw.loader.require) {
                            return window.mw.loader.require(moduleName);
                        }
                        return moduleRegistry[moduleName] || {};
                    };
                    
                    // Call the ready callback with enhanced require
                    if (ready) {
                        ready(enhancedRequire);
                    }
                }).catch(err => {
                    console.error('[ENHANCED-EXECUTOR] Error loading URL modules:', err);
                    if (error) {
                        error(err);
                    }
                });
                
                // Return a promise for compatibility
                return {
                    then: function(callback) {
                        Promise.all(urlDeps.map(dep => loadingModules[dep])).then(() => {
                            if (callback) {
                                const enhancedRequire = function(moduleName) {
                                    if (moduleName === 'rs.highcharts') {
                                        return window.Highcharts;
                                    }
                                    if (urlModules[moduleName]) {
                                        const globalName = moduleName.replace(/^ext\.gadget\./, '').replace(/^rs\./, '');
                                        if (window[globalName]) return window[globalName];
                                        if (window[moduleName]) return window[moduleName];
                                        return true;
                                    }
                                    if (window.mw.loader.require) {
                                        return window.mw.loader.require(moduleName);
                                    }
                                    return moduleRegistry[moduleName] || {};
                                };
                                callback(enhancedRequire);
                            }
                        });
                        return this;
                    },
                    catch: function(callback) {
                        Promise.all(urlDeps.map(dep => loadingModules[dep])).catch(callback);
                        return this;
                    }
                };
            }
            
            // No URL modules, use original or try to find the real mw.loader.using
            console.log('[ENHANCED-EXECUTOR] No URL modules detected, calling original mw.loader.using');
            if (originalUsing && typeof originalUsing === 'function') {
                return originalUsing.apply(this, arguments);
            } else {
                console.warn('[ENHANCED-EXECUTOR] originalUsing not available, trying to find real mw.loader.using');
                
                // Try to find the real mw.loader.using function
                let realUsing = null;
                
                // Check if we can find it in the MediaWiki object structure
                if (window.mw && window.mw.loader) {
                    // Look in various places where the real function might be
                    if (window.mw.loader.__proto__ && window.mw.loader.__proto__.using) {
                        realUsing = window.mw.loader.__proto__.using;
                        console.log('[ENHANCED-EXECUTOR] Found real using in __proto__');
                    } else if (window.mw.loader.constructor && window.mw.loader.constructor.prototype && window.mw.loader.constructor.prototype.using) {
                        realUsing = window.mw.loader.constructor.prototype.using;
                        console.log('[ENHANCED-EXECUTOR] Found real using in constructor.prototype');
                    }
                }
                
                if (realUsing && typeof realUsing === 'function') {
                    // Save it for next time
                    originalUsing = realUsing;
                    return realUsing.apply(this, arguments);
                } else {
                    console.warn('[ENHANCED-EXECUTOR] Could not find real mw.loader.using, using basic implementation');
                    // Basic implementation for when original using isn't available yet
                    if (ready && typeof ready === 'function') {
                        try {
                            // Call ready function with basic require that uses moduleRegistry
                            const basicRequire = function(moduleName) {
                                return moduleRegistry[moduleName] || {};
                            };
                            ready(basicRequire);
                        } catch (e) {
                            console.error('[ENHANCED-EXECUTOR] Error in basic using implementation:', e);
                            if (error && typeof error === 'function') {
                                error(e);
                            }
                        }
                    }
                }
            }
        };
        
        console.log('[ENHANCED-EXECUTOR] Enhanced module executor installed');
    } else {
        console.warn('[ENHANCED-EXECUTOR] mw.loader not available, cannot install executor');
    }
    
    // Export for debugging
    window.EnhancedModuleExecutor = {
        executePackageModule,
        moduleRegistry,
        urlModules,
        loadingModules
    };
})();