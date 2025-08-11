
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


// Adapted module: ext.cite.ux-enhancements
(function() {
'use strict';

'use strict';mw.hook('wikipage.content').add(($content)=>{const accessibilityLabelOne=mw.msg('cite_references_link_accessibility_label');const accessibilityLabelMany=mw.msg('cite_references_link_many_accessibility_label');$content.find('.mw-cite-backlink').each((i,el)=>{const $links=$(el).find('a');if($links.length>1){$links.eq(0).prepend($('<span>').addClass('cite-accessibility-label').text(accessibilityLabelMany+' '));}else{$links.attr('aria-label',accessibilityLabelOne).attr('title',accessibilityLabelOne);}});});
'use strict';(function(){function isNamedReference(id){return/^cite_ref-\D/.test(id);}function isReusedNamedReference(id,$content){if(!isNamedReference(id)){return false;}return id.slice(-2)!=='-0'||$content.find('.references a[href="#'+$.escapeSelector(id.slice(0,-1))+'1"]').length;}function makeUpArrowLink($backlinkWrapper){let textNode=$backlinkWrapper[0].firstChild;const accessibilityLabel=mw.msg('cite_references_link_accessibility_back_label');const $upArrowLink=$('<a>').addClass('mw-cite-up-arrow-backlink').attr('aria-label',accessibilityLabel).attr('title',accessibilityLabel);if(!textNode){return $upArrowLink;}while(textNode.firstChild){textNode=textNode.firstChild;}if(textNode.nodeType!==Node.TEXT_NODE||textNode.data.trim()===''){return $upArrowLink;}const upArrow=textNode.data.trim();textNode.data=textNode.data.replace(upArrow,'');$backlinkWrapper.prepend($('<span>').addClass('mw-cite-up-arrow').text(upArrow),$upArrowLink.text(upArrow));return $upArrowLink;}function updateUpArrowLink($backlink){
const $backlinkWrapper=$backlink.closest('.mw-cite-backlink, li');let $upArrowLink=$backlinkWrapper.find('.mw-cite-up-arrow-backlink');if(!$upArrowLink.length&&$backlinkWrapper.length){$upArrowLink=makeUpArrowLink($backlinkWrapper);}$upArrowLink.attr('href',$backlink.attr('href'));}mw.hook('wikipage.content').add(($content)=>{$content.find('.reference[id] > a').on('click',function(){const id=$(this).parent().attr('id');$content.find('.mw-cite-targeted-backlink').removeClass('mw-cite-targeted-backlink');if(!isReusedNamedReference(id,$content)){return;}const $backlink=$content.find('.references a[href="#'+$.escapeSelector(id)+'"]:not(.mw-cite-up-arrow-backlink)').first().addClass('mw-cite-targeted-backlink');if($backlink.length){updateUpArrowLink($backlink);}});});}());
'use strict';const CITE_BASELINE_LOGGING_SCHEMA='ext.cite.baseline';const REFERENCE_PREVIEWS_LOGGING_SCHEMA='event.ReferencePreviewsPopups';mw.loader.using('ext.eventLogging').then(()=>{$(()=>{if(!navigator.sendBeacon||!mw.config.get('wgIsArticle')){return;}mw.trackSubscribe(REFERENCE_PREVIEWS_LOGGING_SCHEMA,(type,data)=>{if(data.action.indexOf('anonymous')!==-1){mw.config.set('wgCiteReferencePreviewsVisible',data.action==='anonymousEnabled');}});$('#mw-content-text').on('click','.reference a[ href*="#" ], .mw-reference-text a, .reference-text a',function(){const isInReferenceBlock=$(this).parents('.references').length>0;mw.eventLog.dispatch(CITE_BASELINE_LOGGING_SCHEMA,{action:(isInReferenceBlock?'clickedReferenceContentLink':'clickedFootnote'),with_ref_previews:mw.config.get('wgCiteReferencePreviewsVisible')});});});});
},{"css":[".cite-accessibility-label{ top:-99999px;clip:rect(1px,1px,1px,1px); position:absolute !important;padding:0 !important;border:0 !important;height:1px !important;width:1px !important; overflow:hidden}:target .mw-cite-targeted-backlink{font-weight:bold}.mw-cite-up-arrow-backlink{display:none}:target .mw-cite-up-arrow-backlink{display:inline}:target .mw-cite-up-arrow{display:none}"]

})();