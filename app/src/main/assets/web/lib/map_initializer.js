/*
 * This script replaces map placeholders with a message indicating
 * that interactive maps are a future feature.
 */
(function() {
    'use strict';

    function createPlaceholder() {
        const mapElements = document.querySelectorAll('.mw-kartographer-map');
        if (mapElements.length === 0) {
            return;
        }

        mapElements.forEach(function(placeholder) {
            placeholder.style.border = '1px solid #a7a9ad';
            placeholder.style.backgroundColor = '#f8f9fa';
            placeholder.style.display = 'flex';
            placeholder.style.justifyContent = 'center';
            placeholder.style.alignItems = 'center';
            placeholder.style.textAlign = 'center';
            placeholder.style.padding = '16px';
            placeholder.style.boxSizing = 'border-box';
            placeholder.style.color = '#54595d';
            placeholder.style.fontFamily = 'sans-serif';
            placeholder.style.fontSize = '14px';
            
            // Clear the existing content (the static image link)
            while(placeholder.firstChild) {
                placeholder.removeChild(placeholder.firstChild);
            }
            
            placeholder.innerText = 'Interactive map feature coming soon.';
        });
    }

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        createPlaceholder();
    } else {
        document.addEventListener('DOMContentLoaded', createPlaceholder);
    }
})();
