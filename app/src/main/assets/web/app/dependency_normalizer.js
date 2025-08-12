/**
 * MediaWiki Dependency Normalizer (Expert 1's Solution)
 * 
 * Fixes numeric dependency resolution in OSRS Wiki's startup.js.
 * 
 * Problem: OSRS Wiki's startup.js contains dependencies as numeric indices [10]
 * but these need to be resolved to module names like "jquery" for mw.loader.using to work.
 * 
 * Solution: Intercept mw.loader.register() calls to normalize numeric dependencies
 * using the name table passed as the second argument.
 * 
 * Must be loaded BEFORE startup.js to intercept the register calls.
 */
(function () {
    'use strict';
    
    console.log('[DEPENDENCY-NORMALIZER] Installing MediaWiki dependency index normalizer...');
    
    // Ensure we have the basic mw structure
    window.mw = window.mw || {};
    
    // Store pre-startup register calls
    const preCalls = [];
    
    // Install temporary stub until startup defines the real register
    mw.loader = mw.loader || {};
    mw.loader.register = function (...args) {
        console.log('[DEPENDENCY-NORMALIZER] Pre-startup register call captured:', args.length, 'arguments');
        preCalls.push(args);
    };
    
    // Function to normalize dependencies from numeric indices to module names
    function normalizeDependencies(modList, nameTable) {
        if (!Array.isArray(modList)) return modList;
        
        const isNameTable = Array.isArray(nameTable) && nameTable.every(x => typeof x === 'string');
        console.log('[DEPENDENCY-NORMALIZER] Processing', modList.length, 'modules, name table available:', isNameTable);
        
        if (!isNameTable) {
            console.log('[DEPENDENCY-NORMALIZER] No valid name table provided, skipping normalization');
            return modList;
        }
        
        console.log('[DEPENDENCY-NORMALIZER] Name table size:', nameTable.length, 'first few entries:', nameTable.slice(0, 5));
        
        let normalizedCount = 0;
        
        for (const rec of modList) {
            // Common tuple shape: [name, version, deps, group, source, ...]
            const deps = rec[2];
            if (Array.isArray(deps)) {
                for (let i = 0; i < deps.length; i++) {
                    const d = deps[i];
                    if (typeof d === 'number') {
                        const resolvedName = nameTable[d];
                        if (resolvedName) {
                            console.log('[DEPENDENCY-NORMALIZER] Resolved dependency', d, '→', resolvedName, 'for module', rec[0]);
                            deps[i] = resolvedName;
                            normalizedCount++;
                        } else {
                            console.warn('[DEPENDENCY-NORMALIZER] Could not resolve dependency index', d, 'for module', rec[0]);
                        }
                    }
                }
            }
        }
        
        console.log('[DEPENDENCY-NORMALIZER] Normalized', normalizedCount, 'numeric dependencies');
        return modList;
    }
    
    // Function to bind the normalizer after startup loads  
    function bindRegisterNormalizer() {
        console.log('[DEPENDENCY-NORMALIZER] Binding register normalizer...');
        
        if (!mw.loader || typeof mw.loader.register !== 'function') {
            console.error('[DEPENDENCY-NORMALIZER] ERROR: mw.loader.register not available after startup');
            return;
        }
        
        // Store the real register function from startup.js
        const realRegister = mw.loader.register;
        
        // Replace with normalizing wrapper
        mw.loader.register = function (modList, maybeNameTable, ...rest) {
            console.log('[DEPENDENCY-NORMALIZER] Register called with', 
                       Array.isArray(modList) ? modList.length : 'non-array', 
                       'modules, name table type:', typeof maybeNameTable);
            
            // Normalize dependencies before passing to real register
            const normalizedModList = normalizeDependencies(modList, maybeNameTable);
            
            // Call the real register function
            return realRegister.call(mw.loader, normalizedModList, maybeNameTable, ...rest);
        };
        
        // Replay any pre-startup calls (usually none if startup defines register first)
        console.log('[DEPENDENCY-NORMALIZER] Replaying', preCalls.length, 'pre-startup register calls');
        for (const args of preCalls) {
            mw.loader.register.apply(mw.loader, args);
        }
        
        console.log('[DEPENDENCY-NORMALIZER] ✅ Dependency normalizer installed successfully');
    }
    
    // Auto-bind when startup.js defines the real register function
    // Poll until mw.loader.register is available from startup.js
    function waitForStartupAndBind() {
        if (window.mw && window.mw.loader && typeof window.mw.loader.register === 'function') {
            // startup.js has loaded and defined mw.loader.register
            bindRegisterNormalizer();
        } else {
            // Keep checking every 10ms until startup.js loads
            setTimeout(waitForStartupAndBind, 10);
        }
    }
    
    // Start polling immediately
    waitForStartupAndBind();
    
    // Also expose the function for manual binding if needed
    window.__bindRegisterNormalizer = bindRegisterNormalizer;
    
    console.log('[DEPENDENCY-NORMALIZER] Ready to normalize dependencies when startup.js loads');
    
})();