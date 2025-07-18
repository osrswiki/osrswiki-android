/*
 * OSRSWiki Collapsible Content Transformer
 */
(function() {
    'use strict';

    function measureAndPreloadMaps() {
        if (!window.OsrsWikiBridge) return;
        const mapPlaceholders = document.querySelectorAll('.mw-kartographer-map');
        mapPlaceholders.forEach((mapPlaceholder, index) => {
            const mapId = 'map-placeholder-' + index;
            mapPlaceholder.id = mapId;

            const container = mapPlaceholder.closest('.collapsible-container');
            if (!container) return;
            const content = container.querySelector('.collapsible-content');
            if (!content) return;

            if (container.classList.contains('collapsed')) {
                const originalHeight = content.style.height;
                content.style.height = 'auto'; // Expand to full height for measurement

                requestAnimationFrame(() => {
                    const rect = mapPlaceholder.getBoundingClientRect();
                    if (rect.width > 0 && rect.height > 0) {
                        const rectJson = JSON.stringify({
                            y: rect.top + window.scrollY,
                            x: rect.left,
                            width: rect.width,
                            height: rect.height
                        });
                        const mapDataJson = JSON.stringify({
                            lat: mapPlaceholder.dataset.lat,
                            lon: mapPlaceholder.dataset.lon,
                            zoom: mapPlaceholder.dataset.zoom,
                            plane: mapPlaceholder.dataset.plane
                        });
                        window.OsrsWikiBridge.onMapPlaceholderMeasured(mapId, rectJson, mapDataJson);
                    }
                    content.style.height = originalHeight;
                    // Note: We no longer re-add the 'collapsed' class here, as its only
                    // purpose was to control the now-removed CSS animation.
                });
            }
        });
    }
    window.measureAndPreloadMaps = measureAndPreloadMaps;

    function updateHeaderText(container, titleWrapper, captionText) {
        var isCollapsed = container.classList.contains('collapsed');
        var stateText = isCollapsed ? ': Tap to expand' : ': Tap to collapse';
        titleWrapper.innerHTML = '<strong>' + captionText + '</strong>' + stateText;
    }

    function setupCollapsible(header, container, titleWrapper, captionText) {
        var content = container.querySelector('.collapsible-content');
        if (!content) return;

        if (container.classList.contains('collapsed')) {
            content.style.height = '0px';
        }

        header.addEventListener('click', function() {
            var isCurrentlyCollapsed = container.classList.contains('collapsed');
            var mapPlaceholder = content.querySelector('.mw-kartographer-map');
            var mapId = mapPlaceholder ? mapPlaceholder.id : null;

            if (window.OsrsWikiBridge && mapId) {
                window.OsrsWikiBridge.onCollapsibleToggled(mapId, isCurrentlyCollapsed);
            }

            if (isCurrentlyCollapsed) {
                // --- OPENING ---
                container.classList.remove('collapsed');
                content.style.height = 'auto'; // Instantly expand
            } else {
                // --- CLOSING ---
                container.classList.add('collapsed');
                content.style.height = '0px'; // Instantly collapse
            }
            updateHeaderText(container, titleWrapper, captionText);
        });
    }

    /**
     * A generic function to transform a given element type into a collapsible section.
     * @param {string} selector The CSS selector for the elements to transform (e.g., 'table.infobox').
     * @param {string} defaultTitle The default title to use for the header if one cannot be found.
     * @param {number} index The index of the element, used for special casing the first infobox.
     * @param {HTMLElement} element The element to transform.
     */
    function transformElement(selector, defaultTitle, index, element) {
        // Do not re-process an element that is already inside a collapsible container.
        if (element.closest('.collapsible-container')) {
            return;
        }

        // Special handling for the first infobox on the page.
        if (selector === 'table.infobox' && index === 0) {
            element.classList.add('main-infobox');
            element.style.marginTop = '0px';
        }

        var container = document.createElement('div');
        container.className = 'collapsible-container collapsed';

        var header = document.createElement('div');
        header.className = 'collapsible-header';

        var titleWrapper = document.createElement('div');
        titleWrapper.className = 'title-wrapper';

        // Attempt to find a descriptive caption for the element.
        var captionText = defaultTitle;
        if (selector === 'table.infobox') {
            const bonusesCaption = element.querySelector('.infobox-switch-buttons-caption');
            const primaryCaption = element.querySelector('.infobox-header');
            if (bonusesCaption && bonusesCaption.innerText.trim() !== '') {
                captionText = 'Equipment bonuses';
            } else if (primaryCaption && primaryCaption.innerText.trim() !== '') {
                captionText = primaryCaption.innerText.trim();
            }
        } else {
            const caption = element.querySelector('caption, th'); // Use caption or first table header
            if (caption && caption.innerText.trim() !== '') {
                captionText = caption.innerText.trim();
                if (caption.tagName === 'CAPTION') {
                    caption.style.display = 'none'; // Hide original caption
                }
            }
        }

        var icon = document.createElement('span');
        icon.className = 'icon';

        header.appendChild(titleWrapper);
        header.appendChild(icon);

        // Insert the new container before the original element.
        element.parentNode.insertBefore(container, element);

        // Move the original element inside the new content wrapper.
        container.appendChild(header);
        var content = document.createElement('div');
        content.className = 'collapsible-content';
        content.appendChild(element);
        container.appendChild(content);

        // Final setup.
        updateHeaderText(container, titleWrapper, captionText);
        setupCollapsible(header, container, titleWrapper, captionText);
    }

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
            preloader.decode().catch(function() {});
        });
    }

    function initialize() {
        preloadCollapsibleImages();

        // Use the new generic transformer for all collapsible types.
        document.querySelectorAll('table.infobox').forEach((el, i) => transformElement('table.infobox', 'Infobox', i, el));
        document.querySelectorAll('table.wikitable').forEach((el, i) => transformElement('table.wikitable', 'Table', i, el));
        document.querySelectorAll('table.navbox').forEach((el, i) => transformElement('table.navbox', 'Navigation', i, el));
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
