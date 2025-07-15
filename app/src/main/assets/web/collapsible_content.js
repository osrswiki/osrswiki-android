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
                window.OsrsWikiBridge.onMapFound(JSON.stringify({ y: rect.top + window.scrollY, x: rect.left, width: rect.width, height: rect.height, lat: mapPlaceholder.dataset.lat, lon: mapPlaceholder.dataset.lon, zoom: mapPlaceholder.dataset.zoom, plane: mapPlaceholder.dataset.plane }));
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

    function transformInfoboxes() {
        var allInfoboxes = document.querySelectorAll('table.infobox');
        
        allInfoboxes.forEach(function(infobox, index) {
            if (infobox.closest('.collapsible-container')) { return; }

            if (index === 0) {
                infobox.classList.add('main-infobox');
                infobox.style.marginTop = '0px';
            }

            var container = document.createElement('div');
            container.className = 'collapsible-container collapsed';
            var header = document.createElement('div');
            header.className = 'collapsible-header';
            var titleWrapper = document.createElement('div');
            titleWrapper.className = 'title-wrapper';

            var captionText = 'Infobox';
            var bonusesCaption = infobox.querySelector('.infobox-switch-buttons-caption');
            var primaryCaption = infobox.querySelector('.infobox-header');
            if (bonusesCaption && bonusesCaption.innerText.trim() !== '') {
                 captionText = 'Equipment bonuses';
            } else if (primaryCaption && primaryCaption.innerText.trim() !== '') {
                captionText = primaryCaption.innerText.trim();
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
                var wasCollapsed = container.classList.contains('collapsed');
                container.classList.toggle('collapsed');
                updateHeaderText(container, titleWrapper, captionText);

                if (wasCollapsed && window.OsrsWikiBridge) {
                    var isVisible = !container.classList.contains('collapsed');
                    // Additional logic for map visibility can be handled here if needed
                }
            });
        });
    }

    function transformTables() {
        const tables = document.querySelectorAll('table.wikitable');
        tables.forEach(function(table) {
            if (table.closest('.collapsible-container')) { return; }
            var container = document.createElement('div');
            container.className = 'collapsible-container collapsed';
            var header = document.createElement('div');
            header.className = 'collapsible-header';
            var titleWrapper = document.createElement('div');
            titleWrapper.className = 'title-wrapper';
            var captionText = 'Table';
            var caption = table.querySelector('caption');
            if (caption && caption.innerText.trim() !== '') {
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

    function initialize() {
        transformInfoboxes();
        transformTables();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
