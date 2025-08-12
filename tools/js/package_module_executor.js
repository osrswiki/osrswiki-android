/**
 * Package Module Executor
 * 
 * This script provides the infrastructure to execute MediaWiki package modules
 * outside of the full ResourceLoader environment.
 */

(function() {
    'use strict';
    
    // Module registry to track loaded modules
    const moduleRegistry = {};
    
    // Create a module context with require/exports support
    function createModuleContext(moduleName, files) {
        const moduleExports = {};
        const module = { exports: moduleExports };
        
        // Create a require function for this module
        function require(path) {
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
            
            // Handle absolute module requires
            if (moduleRegistry[path]) {
                return moduleRegistry[path];
            }
            
            console.warn('[MODULE-EXECUTOR] Module not found:', path);
            return {};
        }
        
        return { require, module, exports: moduleExports };
    }
    
    // Execute a package module
    function executePackageModule(moduleName, moduleData) {
        console.log('[MODULE-EXECUTOR] Executing package module:', moduleName);
        
        const mainFile = moduleData.main || 'index.js';
        const files = moduleData.files || {};
        
        // Check if main file exists
        if (!files[mainFile]) {
            console.error('[MODULE-EXECUTOR] Main file not found:', mainFile);
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
                
                console.log('[MODULE-EXECUTOR] Module executed successfully:', moduleName);
                return context.module.exports;
            }
        } catch (error) {
            console.error('[MODULE-EXECUTOR] Error executing module:', moduleName, error);
        }
        
        return null;
    }
    
    // Override mw.loader.impl to execute package modules
    if (window.mw && window.mw.loader) {
        const originalImpl = window.mw.loader.impl;
        
        window.mw.loader.impl = function(declarator) {
            console.log('[MODULE-EXECUTOR] Intercepting mw.loader.impl call');
            
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
                    console.log('[MODULE-EXECUTOR] Executing package module:', moduleName);
                    
                    // Execute the package module
                    const exports = executePackageModule(moduleName, moduleData);
                    
                    // For mediawiki.base, the module defines mw.loader.using directly
                    // We need to check if it did that
                    if (moduleName === 'mediawiki.base') {
                        console.log('[MODULE-EXECUTOR] Checking if mw.loader.using is now available...');
                        if (typeof window.mw.loader.using === 'function') {
                            console.log('[MODULE-EXECUTOR] SUCCESS: mw.loader.using is now available!');
                        } else {
                            console.log('[MODULE-EXECUTOR] WARNING: mw.loader.using still not available after executing mediawiki.base');
                        }
                    }
                }
            } catch (error) {
                console.error('[MODULE-EXECUTOR] Error processing module:', error);
            }
        };
        
        console.log('[MODULE-EXECUTOR] Package module executor installed');
    } else {
        console.warn('[MODULE-EXECUTOR] mw.loader not available, cannot install executor');
    }
    
    // Export for debugging
    window.PackageModuleExecutor = {
        executePackageModule,
        moduleRegistry
    };
})();