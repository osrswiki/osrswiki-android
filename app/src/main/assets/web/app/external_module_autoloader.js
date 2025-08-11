/**
 * External Module Autoloader
 *
 * Robust, runtime DOM-based detector that loads extracted MediaWiki modules
 * based on CSS selectors defined in deployment_report.json.
 *
 * Works alongside native (Kotlin) pre-scan. If native pre-scan misses a case,
 * this script ensures modules are still loaded when the DOM is ready.
 */

(function () {
  'use strict';

  var LOG_PREFIX = '[AUTOLOADER]';
  var REGISTRY_URL = 'https://appassets.androidplatform.net/assets/deployment_report.json';
  var ASSET_BASE = 'https://appassets.androidplatform.net/assets/';
  var EXTERNAL_DIR = 'web/external/';

  // Track injected external libraries to avoid duplicates
  var injectedExternals = Object.create(null);

  function log() {
    try { console.log.apply(console, [LOG_PREFIX].concat([].slice.call(arguments))); } catch (_) {}
  }
  function warn() {
    try { console.warn.apply(console, [LOG_PREFIX].concat([].slice.call(arguments))); } catch (_) {}
  }

  function loadJsonSync(url) {
    try {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', url, false); // synchronous
      xhr.send();
      if (xhr.status >= 200 && xhr.status < 300) {
        return JSON.parse(xhr.responseText);
      }
    } catch (e) {
      warn('Failed to load JSON from', url, e);
    }
    return null;
  }

  function injectExternalScript(filename, onload) {
    if (!filename) return;
    // Skip libraries we polyfill or do not bundle
    var skip = { 'jquery.js': true };
    if (skip[filename]) {
      log('Skipping external dependency (polyfilled):', filename);
      if (onload) { onload(); }
      return;
    }
    if (injectedExternals[filename]) {
      if (onload) { onload(); }
      return;
    }
    injectedExternals[filename] = true;
    var script = document.createElement('script');
    script.src = ASSET_BASE + EXTERNAL_DIR + filename;
    script.async = true;
    if (onload) script.onload = onload;
    script.onerror = function () { warn('Failed to load external dependency', filename); };
    document.head.appendChild(script);
  }

  function selectorMatches(selector) {
    if (!selector || !document || !document.querySelector) return false;
    try {
      return !!document.querySelector(selector);
    } catch (e) {
      // If selector is not a valid CSS selector, try simple fallbacks
      // Allow .class, #id, and tag fallbacks similar to native analyzer
      selector = String(selector).trim();
      if (selector[0] === '.') {
        var cls = selector.slice(1);
        return new RegExp('class\\s*=\\s*["\'][^"\']*\\b' + cls + '\\b').test(document.body.innerHTML);
      } else if (selector[0] === '#') {
        var id = selector.slice(1);
        return document.getElementById(id) != null;
      } else {
        return !!document.getElementsByTagName(selector).length;
      }
    }
  }

  function anySelectorMatches(conditional) {
    if (!conditional) return false;
    var parts = String(conditional).split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    for (var i = 0; i < parts.length; i++) {
      if (selectorMatches(parts[i])) return true;
    }
    return false;
  }

  function toModuleKey(extractedName) {
    // Convert extracted file name to module key used by mw_compatibility_shared.js
    // Example: ext_gadget_GECharts-core.js => ext.gadget.GECharts-core
    return String(extractedName || '')
      .replace(/\.js$/i, '')
      .replace(/_/g, '.')
      .replace(/\.gadget\./, '.gadget.');
  }

  function loadModuleByRecord(record) {
    if (!record) return;
    var moduleKey = toModuleKey(record.extracted_name);
    if (!moduleKey) return;

    // Load external file dependencies first (e.g., jquery.js, highcharts-stock.js)
    var deps = (record.analysis && record.analysis.external_dependencies) || [];
    if (deps.length) {
      log('Injecting external dependencies for', moduleKey, deps);
    }
    var remaining = deps.length;
    function afterDeps() {
      // Trigger module load via mw.loader
      if (window.mw && window.mw.loader && typeof window.mw.loader.load === 'function') {
        log('Loading module via mw.loader:', moduleKey);
        try { window.mw.loader.load(moduleKey); } catch (e) { warn('mw.loader.load failed for', moduleKey, e); }
      } else {
        warn('mw.loader not available when loading', moduleKey);
      }
    }
    if (!remaining) {
      afterDeps();
    } else {
      deps.forEach(function (dep) {
        injectExternalScript(dep, function () {
          remaining -= 1;
          if (remaining === 0) {
            afterDeps();
          }
        });
      });
    }
  }

  function runAutoload() {
    var report = loadJsonSync(REGISTRY_URL);
    if (!report || !Array.isArray(report.deployed_modules)) {
      warn('No deployed modules found in deployment report');
      return;
    }

    log('Scanning DOM to autoload modules...');
    report.deployed_modules.forEach(function (record) {
      var conditional = record && record.config && record.config.conditional;
      if (anySelectorMatches(conditional)) {
        log('Matched conditional for', record.deployed_name, '->', conditional);
        loadModuleByRecord(record);
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', runAutoload);
  } else {
    // DOM already ready
    runAutoload();
  }
})();
