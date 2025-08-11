
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


// Adapted module: ext.Tabber
(function() {
'use strict';

(function($){$.fn.tabber=function(){return this.each(function(){var $this=$(this),tabContent=$this.children('.tabbertab'),nav=$('<ul>').addClass('tabbernav'),loc;tabContent.each(function(){var title=$(this).data('title');$(this).attr('data-hash',mw.util.escapeIdForAttribute(title));var anchor=$('<a>').text(title).attr('alt',title).attr('data-hash',$(this).attr('data-hash')).attr('href','#');$('<li>').append(anchor).appendTo(nav);nav.append($('<wbr>'));});$this.prepend(nav);function showContent(title){var content=tabContent.filter('[data-hash="'+title+'"]');if(content.length!==1){return false;}tabContent.hide();content.show();nav.find('.tabberactive').removeClass('tabberactive');nav.find('a[data-hash="'+title+'"]').parent().addClass('tabberactive');return true;}var tab=new mw.Uri(location.href).fragment;if(tab===''||!showContent(tab)){showContent(tabContent.first().attr('data-hash'));}nav.on('click','a',function(e){var title=$(this).attr('data-hash');e.preventDefault();if(history.replaceState){
history.replaceState(null,null,'#'+title);switchTab();}else{location.hash='#'+title;}});$(window).on('hashchange',function(event){switchTab();});function switchTab(){var tab=new mw.Uri(location.href).fragment;if(!tab.length){showContent(tabContent.first().attr('data-hash'));}if(nav.find('a[data-hash="'+tab+'"]').length){showContent(tab);}}$this.addClass('tabberlive');});};}(jQuery));$(function(){$('.tabber').tabber();});
},{"css":["ul.tabbernav{margin:0;padding:3px 0;border-bottom:1px solid #CCC;font:bold 12px Verdana,sans-serif}ul.tabbernav li{list-style:none;margin:0;display:inline-block;padding-top:1em}ul.tabbernav li a{padding:3px .5em;margin-left:3px;border:1px solid #CCC;border-bottom:none;background:#F2F7FF;text-decoration:none;white-space:pre}ul.tabbernav li a:link{color:#448}ul.tabbernav li a:visited{color:#667}ul.tabbernav li a:hover{color:#000;background:#FFF9F2;border-color:#CCC}ul.tabbernav li.tabberactive a{background-color:#FFF;border-bottom:1px solid #FFF}ul.tabbernav li.tabberactive a:hover{color:#000;background:#FFF;border-bottom:1px solid #FFF}.tabber .tabbertab{padding:5px;border:1px solid #CCC;border-top:0}"]

})();