/*
 * OSRSWiki Collapsible Content Transformer
 *
 * This script unifies the handling of two types of collapsible content:
 * 1. Wraps all `.wikitable` elements in a standard collapsible container.
 * 2. Finds specific collapsible text sections (marked by `div.mw-collapsible`),
 * and transforms them into the same container style, leaving the original
 * section heading (H2) intact.
 */
(function() {
    'use strict';

    /**
     * Sets header text for tables ("Caption: Tap to collapse").
     */
    function updateTableHeaderText(container, titleWrapper, captionText) {
        var isCollapsed = container.classList.contains('collapsed');
        var stateText = isCollapsed ? ': Tap to expand' : ': Tap to collapse';
        titleWrapper.innerHTML = '<strong>' + captionText + '</strong>' + stateText;
    }

    /**
     * Sets header text for text sections ("Infobox: Click here to hide").
     */
    function updateSectionHeaderText(container, titleWrapper) {
        var isCollapsed = container.classList.contains('collapsed');
        var stateText = isCollapsed ? 'Click here to show' : 'Click here to hide';
        titleWrapper.innerHTML = '<strong>Infobox:</strong> ' + stateText;
    }

    /**
     * Finds all wikitable elements and wraps them in a collapsible container.
     */
    function transformTables() {
        document.querySelectorAll('table.wikitable').forEach(function(table) {
            if (table.closest('.collapsible-container')) {
                return; // Already handled, likely inside a transformed section.
            }

            var container = document.createElement('div');
            container.className = 'collapsible-container collapsed';

            var header = document.createElement('div');
            header.className = 'collapsible-header';

            var titleWrapper = document.createElement('div');
            titleWrapper.className = 'title-wrapper';

            var captionText = 'Table';
            var caption = table.querySelector('caption');
            if (caption && caption.innerText && caption.innerText.trim() !== '') {
                captionText = caption.innerText.trim();
                caption.style.display = 'none';
            }

            var icon = document.createElement('span');
            icon.className = 'icon';

            header.appendChild(titleWrapper);
            header.appendChild(icon);

            table.parentNode.insertBefore(container, table);
            container.appendChild(header);

            var content = document.createElement('div');
            content.className = 'collapsible-content';
            content.appendChild(table);
            container.appendChild(content);

            updateTableHeaderText(container, titleWrapper, captionText);

            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                updateTableHeaderText(container, titleWrapper, captionText);
            });
        });
    }

    /**
     * Finds collapsible text divs and replaces them with the standard container.
     */
    function transformSections() {
        document.querySelectorAll('div.mw-collapsible').forEach(function(collapsibleDiv) {
            // Only transform the divs that use the "CLICK HERE TO SHOW" pattern.
            const triggerSpan = collapsibleDiv.querySelector('.collapsed-sec');
            if (!triggerSpan) {
                return;
            }

            var container = document.createElement('div');
            container.className = 'collapsible-container';
            if (collapsibleDiv.classList.contains('mw-collapsed')) {
                container.classList.add('collapsed');
            }

            var header = document.createElement('div');
            header.className = 'collapsible-header';

            var titleWrapper = document.createElement('div');
            titleWrapper.className = 'title-wrapper';

            var icon = document.createElement('span');
            icon.className = 'icon';

            header.appendChild(titleWrapper);
            header.appendChild(icon);

            var originalContent = collapsibleDiv.querySelector('.mw-collapsible-content');
            if (!originalContent) return;

            var newContent = document.createElement('div');
            newContent.className = 'collapsible-content';
            while (originalContent.firstChild) {
                newContent.appendChild(originalContent.firstChild);
            }

            container.appendChild(header);
            container.appendChild(newContent);

            // Replace the old div with the new container, leaving the H2 untouched.
            collapsibleDiv.parentNode.replaceChild(container, collapsibleDiv);

            updateSectionHeaderText(container, titleWrapper);

            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                updateSectionHeaderText(container, titleWrapper);
            });
        });
    }

    function initialize() {
        transformSections();
        transformTables();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
