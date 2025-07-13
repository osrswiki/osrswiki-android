/*
 * OSRSWiki Collapsible Content Transformer
 *
 * This script unifies the handling of three types of collapsible content:
 * 1. Wraps the main `.infobox` element in a standard collapsible container.
 * 2. Wraps all `.wikitable` elements in the same container style.
 * 3. Finds specific collapsible text sections (marked by `div.mw-collapsible`),
 * and transforms them into the same container style.
 */
(function() {
    'use strict';

    /**
     * Finds the map placeholder within an expanded infobox and passes its
     * details to the native layer. This is called only once, triggered by native code
     * after the layout is stable.
     */
    function findAndShowNativeMap(container) {
        if (!container) return;
        var mapPlaceholder = container.querySelector('.mw-kartographer-map');

        // The NativeMapInterface check is now redundant if only native calls this,
        // but it provides a good defensive check.
        if (mapPlaceholder && window.NativeMapInterface) {
            var rect = mapPlaceholder.getBoundingClientRect();
            // Only proceed if the map placeholder is actually visible.
            if (rect.width > 0 && rect.height > 0) {
                 window.NativeMapInterface.onMapFound(JSON.stringify({
                     y: rect.top + window.scrollY,
                     x: rect.left,
                     width: rect.width,
                     height: rect.height,
                     // Pass data attributes for map configuration.
                     lat: mapPlaceholder.dataset.lat,
                     lon: mapPlaceholder.dataset.lon,
                     zoom: mapPlaceholder.dataset.zoom,
                     plane: mapPlaceholder.dataset.plane
                 }));
                // Make the original placeholder transparent, but keep it in the layout.
                mapPlaceholder.style.opacity = '0';
            }
        }
    }

    // Make the map measurement function globally accessible to be called from native code.
    window.findAndShowNativeMap = findAndShowNativeMap;

    /**
     * Sets header text for tables and infoboxes.
     * Example: "Varrock: Tap to collapse"
     */
    function updateHeaderText(container, titleWrapper, captionText) {
        var isCollapsed = container.classList.contains('collapsed');
        var stateText = isCollapsed ? ': Tap to expand' : ': Tap to collapse';
        titleWrapper.innerHTML = '<strong>' + captionText + '</strong>' + stateText;
    }

    /**
     * Sets header text for generic text sections.
     * Example: "Infobox: Click here to hide"
     */
    function updateSectionHeaderText(container, titleWrapper) {
        var isCollapsed = container.classList.contains('collapsed');
        var stateText = isCollapsed ? 'Click here to show' : 'Click here to hide';
        titleWrapper.innerHTML = '<strong>Infobox:</strong> ' + stateText;
    }

    /**
     * Finds the main infobox and wraps it in a collapsible container.
     */
    function transformInfobox() {
        var infobox = document.querySelector('table.infobox');
        if (!infobox || infobox.closest('.collapsible-container')) {
            return;
        }

        // Programmatically override inline styles from the server.
        infobox.style.width = '100%';
        infobox.style.marginTop = '0px';

        var container = document.createElement('div');
        container.className = 'collapsible-container collapsed';
        container.dataset.mapLoaded = 'false';

        var header = document.createElement('div');
        header.className = 'collapsible-header';

        var titleWrapper = document.createElement('div');
        titleWrapper.className = 'title-wrapper';

        var captionText = 'Infobox';
        var infoboxHeader = infobox.querySelector('.infobox-header');
        if (infoboxHeader && infoboxHeader.innerText.trim() !== '') {
            captionText = infoboxHeader.innerText.trim();
        }

        var icon = document.createElement('span');
        icon.className = 'icon';

        header.appendChild(titleWrapper);
        header.appendChild(icon);

        infobox.parentNode.insertBefore(container, infobox);
        container.appendChild(header);

        var content = document.createElement('div');
        content.className = 'collapsible-content';
        content.appendChild(infobox);
        container.appendChild(content);

        updateHeaderText(container, titleWrapper, captionText);

        header.addEventListener('click', function() {
            var isFirstExpansion = container.classList.contains('collapsed') && container.dataset.mapLoaded === 'false';

            container.classList.toggle('collapsed');
            updateHeaderText(container, titleWrapper, captionText);

            if (isFirstExpansion) {
                // On the first expansion, simply notify the native layer.
                // The native layer will be responsible for waiting for the layout
                // to be stable before calling back into the JS to find the map.
                if (window.NativeMapInterface) {
                    window.NativeMapInterface.onInfoboxExpanded();
                }
                container.dataset.mapLoaded = 'true';
            }
        });
    }

    /**
     * Finds all wikitable elements and wraps them in a collapsible container.
     */
    function transformTables() {
        document.querySelectorAll('table.wikitable').forEach(function(table) {
            if (table.closest('.collapsible-container')) {
                return;
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

            updateHeaderText(container, titleWrapper, captionText);

            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                updateHeaderText(container, titleWrapper, captionText);
            });
        });
    }

    /**
     * Finds collapsible text divs and replaces them with the standard container.
     */
    function transformSections() {
        document.querySelectorAll('div.mw-collapsible').forEach(function(collapsibleDiv) {
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

            collapsibleDiv.parentNode.replaceChild(container, collapsibleDiv);

            updateSectionHeaderText(container, titleWrapper);

            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                updateSectionHeaderText(container, titleWrapper);
            });
        });
    }

    function initialize() {
        transformInfobox();
        transformSections();
        transformTables();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
