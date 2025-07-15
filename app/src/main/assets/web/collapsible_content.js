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

    function setupCollapsible(header, container, titleWrapper, captionText) {
        var content = container.querySelector('.collapsible-content');
        if (!content) return;

        // Initialize content height for JS-driven animations.
        if (container.classList.contains('collapsed')) {
            content.style.height = '0px';
        }

        header.addEventListener('click', function() {
            var isCurrentlyCollapsed = container.classList.contains('collapsed');

            if (isCurrentlyCollapsed) {
                // --- Was collapsed, now OPENING ---
                container.classList.remove('collapsed');
                content.style.height = content.scrollHeight + 'px';

                var onTransitionEnd = function() {
                    content.style.height = 'auto';
                    content.removeEventListener('transitionend', onTransitionEnd);
                };
                content.addEventListener('transitionend', onTransitionEnd);

            } else {
                // --- Was open, now CLOSING ---
                content.style.height = content.scrollHeight + 'px';
                setTimeout(function() {
                    container.classList.add('collapsed');
                }, 10);
            }
            updateHeaderText(container, titleWrapper, captionText);
        });
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
            setupCollapsible(header, container, titleWrapper, captionText);
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
            setupCollapsible(header, container, titleWrapper, captionText);
        });
    }

    /**
     * Finds all images within collapsible content and preloads them to prevent
     * layout jank on first expansion. This mirrors the strategy used for switchable
     * infoboxes.
     */
    function preloadCollapsibleImages() {
        const imageUrlsToPreload = new Set();
        const containers = document.querySelectorAll('.collapsible-container');

        containers.forEach(function(container) {
            const images = container.querySelectorAll('img');
            images.forEach(function(img) {
                const src = img.getAttribute('src');
                if (src) { imageUrlsToPreload.add(src); }

                const srcset = img.getAttribute('srcset');
                if (srcset) {
                    const sources = srcset.split(',').map(function(s) {
                        return s.trim().split(/\s+/)[0];
                    });
                    sources.forEach(function(sourceUrl) {
                        imageUrlsToPreload.add(sourceUrl);
                    });
                }
            });
        });

        imageUrlsToPreload.forEach(function(url) {
            const preloader = new Image();
            preloader.src = url;
            // Attempt to decode, but don't block initialization. The primary
            // benefit comes from getting the image into the network cache.
            preloader.decode().catch(function() {
                // Ignore decoding errors for now.
            });
        });
    }

    function initialize() {
        // Preload all images first to prevent jank.
        preloadCollapsibleImages();

        // Then, transform the DOM.
        transformInfoboxes();
        transformTables();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
