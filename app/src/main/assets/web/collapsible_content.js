/*
 * OSRSWiki Collapsible Content Transformer
 */
(function() {
    'use strict';

    function findAndShowNativeMap(container) {
        if (!container) return;
        var mapPlaceholder = container.querySelector('.mw-kartographer-map');

        if (mapPlaceholder && window.OsrsWikiBridge) {
            var rect = mapPlaceholder.getBoundingClientRect();
            if (rect.width > 0 && rect.height > 0) {
                window.OsrsWikiBridge.onMapFound(JSON.stringify({
                    y: rect.top + window.scrollY,
                    x: rect.left,
                    width: rect.width,
                    height: rect.height,
                    lat: mapPlaceholder.dataset.lat,
                    lon: mapPlaceholder.dataset.lon,
                    zoom: mapPlaceholder.dataset.zoom,
                    plane: mapPlaceholder.dataset.plane
                }));
                mapPlaceholder.style.opacity = '0';
            }
        }
    }

    window.findAndShowNativeMap = findAndShowNativeMap;

    function updateHeaderText(container, titleWrapper, captionText) {
        var isCollapsed = container.classList.contains('collapsed');
        var stateText = isCollapsed ? ': Tap to expand' : ': Tap to collapse';
        titleWrapper.innerHTML = '<strong>' + captionText + '</strong>' + stateText;
    }

    function transformInfobox() {
        var infobox = document.querySelector('table.infobox');
        if (!infobox || infobox.closest('.collapsible-container')) {
            return;
        }

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
                if (window.OsrsWikiBridge) {
                    window.OsrsWikiBridge.onInfoboxExpanded();
                }
                container.dataset.mapLoaded = 'true';
            } else if (container.dataset.mapLoaded === 'true' && window.OsrsWikiBridge) {
                var isVisible = !container.classList.contains('collapsed');
                window.OsrsWikiBridge.setMapVisibility(isVisible);
            }
        });
        window.OsrsWikiBridge?.log('Collapser: Transformed infobox.');
    }

    function transformTables() {
        const tables = document.querySelectorAll('table.wikitable');
        if (tables.length > 0) {
            window.OsrsWikiBridge?.log(`Collapser: Found ${tables.length} wikitable(s) to transform.`);
        }
        tables.forEach(function(table, index) {
            if (table.closest('.collapsible-container')) {
                return; // Already transformed, skip.
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

            // This listener is now simpler, as the scroll interceptor handles everything automatically.
            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                updateHeaderText(container, titleWrapper, captionText);
            });
            window.OsrsWikiBridge?.log(`Collapser: Transformed wikitable #${index + 1} with caption "${captionText}".`);
        });
    }

    function initialize() {
        window.OsrsWikiBridge?.log('Collapser: Initializing content transformers...');
        transformInfobox();
        transformTables();
        window.OsrsWikiBridge?.log('Collapser: Content transformers finished.');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
