/*
 * OSRSWiki Collapsible Content Transformer
 */
(function() {
    'use strict';

    const tryInitializeSwitcher = () => {
        if (typeof initializeInfoboxSwitcher === 'function') {
            initializeInfoboxSwitcher();
        }
    };

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
                content.style.height = 'auto';
                requestAnimationFrame(() => {
                    const rect = mapPlaceholder.getBoundingClientRect();
                    if (rect.width > 0 && rect.height > 0) {
                        const rectJson = JSON.stringify({ y: rect.top + window.scrollY, x: rect.left, width: rect.width, height: rect.height });
                        const mapDataJson = JSON.stringify({ lat: mapPlaceholder.dataset.lat, lon: mapPlaceholder.dataset.lon, zoom: mapPlaceholder.dataset.zoom, plane: mapPlaceholder.dataset.plane });
                        window.OsrsWikiBridge.onMapPlaceholderMeasured(mapId, rectJson, mapDataJson);
                    }
                    content.style.height = originalHeight;
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
                container.classList.remove('collapsed');
                content.style.height = 'auto';
            } else {
                container.classList.add('collapsed');
                content.style.height = '0px';
            }
            updateHeaderText(container, titleWrapper, captionText);
        });
    }

    function transformElement(selector, defaultTitle, index, elementToWrap, elementForTitle) {
        if (elementToWrap.closest('.collapsible-container')) {
            return;
        }


        if (selector === 'table.infobox' && index === 0) {
            elementForTitle.classList.add('main-infobox');
            elementForTitle.style.marginTop = '0px';
        }

        var container = document.createElement('div');
        container.className = 'collapsible-container collapsed';
        var header = document.createElement('div');
        header.className = 'collapsible-header';
        var titleWrapper = document.createElement('div');
        titleWrapper.className = 'title-wrapper';
        var captionText = defaultTitle;
        if (selector === 'table.infobox') {
            const bonusesCaption = elementForTitle.querySelector('.infobox-switch-buttons-caption');
            const primaryCaption = elementForTitle.querySelector('.infobox-header');
            if (bonusesCaption && bonusesCaption.innerText.trim() !== '') {
                captionText = 'Equipment bonuses';
            } else if (primaryCaption && primaryCaption.innerText.trim() !== '') {
                captionText = primaryCaption.innerText.trim();
            }
        } else {
            const caption = elementForTitle.querySelector('caption, th');
            if (caption && caption.innerText.trim() !== '') {
                captionText = caption.innerText.trim();
                if (caption.tagName === 'CAPTION') {
                    caption.style.display = 'none';
                }
            }
        }

        var icon = document.createElement('span');
        icon.className = 'icon';
        header.appendChild(titleWrapper);
        header.appendChild(icon);
        elementToWrap.parentNode.insertBefore(container, elementToWrap);
        container.appendChild(header);
        var content = document.createElement('div');
        content.className = 'collapsible-content';
        content.appendChild(elementToWrap);
        container.appendChild(content);
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
                    const sources = srcset.split(',').map(s => s.trim().split(/\s+/)[0]);
                    sources.forEach(sourceUrl => imageUrlsToPreload.add(sourceUrl));
                }
            });
        });
        imageUrlsToPreload.forEach(function(url) {
            const preloader = new Image();
            preloader.src = url;
            preloader.decode().catch(() => {});
        });
    }

    function initialize() {
        preloadCollapsibleImages();

        document.querySelectorAll('table.infobox').forEach((table, i) => {
            const switcherContainer = table.closest('.infobox-switch');
            const elementToTransform = switcherContainer || table;
            transformElement('table.infobox', 'Infobox', i, elementToTransform, table);
        });

        document.querySelectorAll('table.wikitable').forEach((el, i) => transformElement('table.wikitable', 'Table', i, el, el));
        document.querySelectorAll('table.navbox').forEach((el, i) => transformElement('table.navbox', 'Navigation', i, el, el));
        
        tryInitializeSwitcher();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();

//
// OSRSWiki App: Signal to the native layer that styling is complete.
//
(function() {
    if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
        // This is the signal our native code will wait for before revealing the page.
        window.RenderTimeline.log('Event: StylingScriptsComplete');
    }
})();
