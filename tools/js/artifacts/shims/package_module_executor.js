/**
 * MediaWiki Package Module Executor (Expert 2's Minimal Architecture)
 * 
 * ONLY enhances mw.loader.impl to handle package modules. Does NOT replace
 * mw.loader.using or dependency resolution - lets MediaWiki handle that.
 * 
 * Architecture: startup.js → package_executor.js → mediawiki.base.js
 * - startup.js: Creates basic mw.loader with impl, register, etc.
 * - package_executor.js: Enhances impl to execute package modules  
 * - mediawiki.base.js: Provides real mw.loader.using with dependency resolution
 * 
 * Must be loaded AFTER startup.js and BEFORE mediawiki.base.js.
 */
(function () {
    'use strict';
    
    console.log('[PACKAGE-EXECUTOR] Initializing minimal package module executor (Expert 2 approach)...');
    
    // Ensure we have the basic mw structure from startup.js
    const mw = window.mw = window.mw || {};
    
    if (!mw.loader) {
        console.error('[PACKAGE-EXECUTOR] ERROR: mw.loader not available. startup.js must load first.');
        return;
    }
    
    // Use the existing module registry from startup.js - do NOT create our own
    const reg = mw.loader.moduleRegistry || Object.create(null);
    console.log('[PACKAGE-EXECUTOR] Using existing module registry with', Object.keys(reg).length, 'modules');

    function ensure(name) { 
        return reg[name] || (reg[name] = { state: 'registered', deps: [], kind: 'plain' }); 
    }

    /**
     * Execute a package module using CommonJS pattern
     * This is the core functionality that was missing - package modules need
     * their inner functions executed to provide functionality like mw.loader.using
     */
    function runPackageModule(mod) {
        if (!mod.pkg) return;
        
        console.log('[PACKAGE-EXECUTOR] Executing package module:', mod.name);
        
        const files = mod.pkg.files || {};
        const cache = Object.create(null);

        // Create a CommonJS-style require function for the package
        function localRequire(path) {
            // Handle relative paths by removing ./ prefix
            const normalizedPath = path.startsWith('./') ? path.substring(2) : path;
            const fn = files[normalizedPath];
            
            if (!fn) {
                console.error('[PACKAGE-EXECUTOR] Package file not found:', path, 'normalized to:', normalizedPath);
                console.error('[PACKAGE-EXECUTOR] Available files:', Object.keys(files));
                throw new Error('Package file not found: ' + path);
            }
            
            if (!(normalizedPath in cache)) {
                const module = { exports: {} };
                console.log('[PACKAGE-EXECUTOR] Executing package file:', path, '(normalized:', normalizedPath + ')');
                
                try {
                    // Handle both functions (JS files) and objects (JSON files)
                    if (typeof fn === 'function') {
                        // Execute file with (require, module, exports) - standard CommonJS pattern
                        fn(localRequire, module, module.exports);
                        cache[normalizedPath] = module.exports;
                        console.log('[PACKAGE-EXECUTOR] Successfully executed JS file:', path);
                    } else if (typeof fn === 'object') {
                        // For JSON files, return the object directly
                        cache[normalizedPath] = fn;
                        console.log('[PACKAGE-EXECUTOR] Successfully loaded JSON file:', path);
                    } else {
                        console.error('[PACKAGE-EXECUTOR] Unknown file type for:', path, 'type:', typeof fn);
                        cache[normalizedPath] = fn;
                    }
                } catch (error) {
                    console.error('[PACKAGE-EXECUTOR] Error executing package file:', path, error);
                    throw error;
                }
            }
            return cache[normalizedPath];
        }

        try {
            // Execute the package's main file - this is where mw.loader.using gets defined
            const main = mod.pkg.main || 'index.js';
            console.log('[PACKAGE-EXECUTOR] Running package main file:', main);
            localRequire(main);
            mod.state = 'ready';
            console.log('[PACKAGE-EXECUTOR] Package module ready:', mod.name);
        } catch (error) {
            console.error('[PACKAGE-EXECUTOR] Failed to execute package module:', mod.name, error);
            mod.state = 'error';
            throw error;
        }
    }

    // Store original impl function from startup.js
    const originalImpl = mw.loader.impl;
    
    if (!originalImpl) {
        console.error('[PACKAGE-EXECUTOR] ERROR: mw.loader.impl not available. startup.js must load first.');
        return;
    }

    /**
     * Enhanced mw.loader.impl that supports both legacy and package module formats
     * This is the key fix - the original impl from startup.js doesn't handle packages
     */
    mw.loader.impl = function impl(a, scripts, styles, messages) {
        console.log('[PACKAGE-EXECUTOR] mw.loader.impl called with:', typeof a, arguments.length);
        
        // PACKAGE FORM: impl(function(){ return ['name@ver', {main, files, deps}] })
        if (typeof a === 'function' && arguments.length === 1) {
            console.log('[PACKAGE-EXECUTOR] Processing package module...');
            
            let packageData;
            try {
                packageData = a(); // Execute the package function immediately
            } catch (error) {
                console.error('[PACKAGE-EXECUTOR] Error executing package function:', error);
                throw new Error('Bad package impl function: ' + error.message);
            }
            
            if (!Array.isArray(packageData) || packageData.length < 2) {
                console.error('[PACKAGE-EXECUTOR] Invalid package impl payload:', packageData);
                throw new Error('Bad package impl payload - expected [name, metadata]');
            }
            
            const rawName = packageData[0]; // e.g., "mediawiki.base@1.39.0-wmf.23"
            const name = String(rawName).split('@')[0]; // Extract module name
            console.log('[PACKAGE-EXECUTOR] Package module name:', name);

            const meta = packageData[1] || {};
            const mod = ensure(name);
            mod.name = name;
            mod.kind = 'package';
            mod.pkg = { 
                main: meta.main, 
                files: meta.files || {} 
            };
            
            // Handle both 'deps' and 'dependencies' fields
            mod.deps = Array.isArray(meta.deps) ? meta.deps : (meta.dependencies || []);
            
            console.log('[PACKAGE-EXECUTOR] Package metadata:', {
                name: name,
                main: meta.main,
                fileCount: Object.keys(meta.files || {}).length,
                deps: mod.deps
            });

            // Core bootstrap packages: execute immediately so downstream has APIs
            if (name === 'mediawiki.base') {
                console.log('[PACKAGE-EXECUTOR] Core package detected - executing immediately:', name);
                runPackageModule(mod);
                
                // Verify mw.loader.using is now available
                if (typeof window.mw.loader.using === 'function') {
                    console.log('[PACKAGE-EXECUTOR] ✅ SUCCESS: mw.loader.using is now available!');
                } else {
                    console.error('[PACKAGE-EXECUTOR] ❌ ERROR: mw.loader.using still not available after package execution');
                }
            } else {
                console.log('[PACKAGE-EXECUTOR] Non-core package - deferring execution:', name);
                mod.state = 'loaded'; // Defer execution until using()
            }
            return;
        }

        // LEGACY FORM: impl('name', [fnOrCode], styles, messages)  
        // Pass through to original implementation - let startup.js handle this
        console.log('[PACKAGE-EXECUTOR] Delegating to original impl for legacy format');
        return originalImpl.apply(this, arguments);
    };

    console.log('[PACKAGE-EXECUTOR] ✅ Minimal package module executor initialized successfully');
    console.log('[PACKAGE-EXECUTOR] Enhanced mw.loader.impl with package support ONLY');
    console.log('[PACKAGE-EXECUTOR] Dependency resolution will be handled by MediaWiki (mediawiki.base)');
    
    // Export for debugging
    window.PackageModuleExecutor = {
        runPackageModule,
        moduleRegistry: reg
    };
    
})();