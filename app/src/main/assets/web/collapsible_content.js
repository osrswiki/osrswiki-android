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

        // Debug logging for navbox tables
        if (selector === 'table.navbox') {
            // Log table-level computed styles
            const tableStyles = window.getComputedStyle(elementToWrap);
            const tableInfo = {
                borderCollapse: tableStyles.borderCollapse,
                borderSpacing: tableStyles.borderSpacing,
                border: tableStyles.border,
                backgroundColor: tableStyles.backgroundColor
            };

            // Analyze all cells, categorizing by background color to identify problematic types
            const cells = Array.from(elementToWrap.querySelectorAll('td, th'));
            const cellsByType = {
                darkBackground: [],
                transparentBackground: [],
                other: []
            };
            
            cells.forEach((cell, idx) => {
                const computedStyle = window.getComputedStyle(cell);
                const bgColor = computedStyle.backgroundColor;
                const cellInfo = {
                    index: idx,
                    tagName: cell.tagName,
                    classes: cell.className,
                    border: computedStyle.border,
                    backgroundColor: bgColor,
                    borderColor: computedStyle.borderColor,
                    hasVisibleBorder: computedStyle.borderWidth !== '0px' && computedStyle.borderStyle !== 'none'
                };
                
                if (bgColor === 'rgb(49, 42, 37)') {
                    cellsByType.darkBackground.push(cellInfo);
                } else if (bgColor === 'rgba(0, 0, 0, 0)' || bgColor === 'transparent') {
                    cellsByType.transparentBackground.push(cellInfo);
                } else {
                    cellsByType.other.push(cellInfo);
                }
            });

            // Analyze ALL cells to get complete picture, with detailed border analysis for problematic ones
            const detailedAnalysis = cells.map((cell, idx) => {
                const computedStyle = window.getComputedStyle(cell);
                const hasDarkBackground = computedStyle.backgroundColor === 'rgb(49, 42, 37)';
                const hasVisibleBorder = computedStyle.borderWidth !== '0px' && computedStyle.borderStyle !== 'none';
                const hasDarkBorderSides = computedStyle.borderColor && computedStyle.borderColor.includes('rgb(40, 34, 29)');
                const isProblematic = hasDarkBackground && hasVisibleBorder && hasDarkBorderSides;
                
                const cellInfo = {
                    index: idx,
                    tagName: cell.tagName,
                    classes: cell.className,
                    border: computedStyle.border,
                    backgroundColor: computedStyle.backgroundColor,
                    borderColor: computedStyle.borderColor,
                    borderWidth: computedStyle.borderWidth,
                    isProblematic: isProblematic
                };
                
                // Add detailed border analysis for problematic cells
                if (isProblematic) {
                    cellInfo.detailedBorders = {
                        top: {
                            width: computedStyle.borderTopWidth,
                            style: computedStyle.borderTopStyle,
                            color: computedStyle.borderTopColor
                        },
                        right: {
                            width: computedStyle.borderRightWidth,
                            style: computedStyle.borderRightStyle,
                            color: computedStyle.borderRightColor
                        },
                        bottom: {
                            width: computedStyle.borderBottomWidth,
                            style: computedStyle.borderBottomStyle,
                            color: computedStyle.borderBottomColor
                        },
                        left: {
                            width: computedStyle.borderLeftWidth,
                            style: computedStyle.borderLeftStyle,
                            color: computedStyle.borderLeftColor
                        }
                    };
                    
                    // Analyze which sides have which colors
                    const sides = ['top', 'right', 'bottom', 'left'];
                    const darkBorderSides = sides.filter(side => 
                        cellInfo.detailedBorders[side].color === 'rgb(40, 34, 29)' && 
                        cellInfo.detailedBorders[side].width !== '0px'
                    );
                    const lightBorderSides = sides.filter(side => 
                        cellInfo.detailedBorders[side].color === 'rgb(244, 234, 234)' && 
                        cellInfo.detailedBorders[side].width !== '0px'
                    );
                    
                    cellInfo.borderAnalysis = {
                        darkSides: darkBorderSides,
                        lightSides: lightBorderSides,
                        mixedColors: darkBorderSides.length > 0 && lightBorderSides.length > 0,
                        predominantPattern: darkBorderSides.length > lightBorderSides.length ? 'mostly-dark' : 'mostly-light'
                    };
                }
                
                return cellInfo;
            });

            // Test CSS selector matching for our navbox rules
            const selectorTests = {
                mainNavboxMatches: elementToWrap.matches('.navbox'),
                cellsMatchingNavboxSelector: elementToWrap.querySelectorAll('.navbox > * > tr > th, .navbox > * > tr > td').length,
                cellsMatchingNestedSelector: elementToWrap.querySelectorAll('.navbox .navbox > * > tr > th, .navbox .navbox > * > tr > td').length,
                navboxListCells: elementToWrap.querySelectorAll('.navbox-list').length,
                totalCells: elementToWrap.querySelectorAll('td, th').length
            };

            // Categorize problematic cells by class type and border patterns
            const problematicCells = detailedAnalysis.filter(cell => cell.isProblematic);
            const problematicByClass = {};
            let mixedBorderCount = 0;
            const borderPatterns = {};
            
            problematicCells.forEach(cell => {
                const className = cell.classes || 'no-class';
                if (!problematicByClass[className]) {
                    problematicByClass[className] = [];
                }
                problematicByClass[className].push(cell.index);
                
                // Track border patterns
                if (cell.borderAnalysis) {
                    if (cell.borderAnalysis.mixedColors) {
                        mixedBorderCount++;
                    }
                    
                    const pattern = `dark:${cell.borderAnalysis.darkSides.join(',')} light:${cell.borderAnalysis.lightSides.join(',')}`;
                    if (!borderPatterns[pattern]) {
                        borderPatterns[pattern] = [];
                    }
                    borderPatterns[pattern].push(cell.index);
                }
            });

            const debugInfo = {
                index: index,
                classes: elementToWrap.className,
                classesClean: elementToWrap.className.replace(/<[^>]*>/g, ''), // Remove malformed HTML
                parentClasses: elementToWrap.parentElement?.className || 'no-parent',
                tableStyles: tableInfo,
                selectorTests: selectorTests,
                summary: {
                    totalCells: cells.length,
                    darkBackgroundCells: cellsByType.darkBackground.length,
                    transparentBackgroundCells: cellsByType.transparentBackground.length,
                    problematicCellCount: problematicCells.length,
                    mixedBorderColorCount: mixedBorderCount,
                    problematicByClass: problematicByClass,
                    borderPatterns: borderPatterns,
                    mixedBorderPercentage: Math.round((mixedBorderCount / problematicCells.length) * 100)
                },
                // Only include first 20 cells in detailed analysis to avoid log truncation
                detailedAnalysisPreview: detailedAnalysis.slice(0, 20),
                allProblematicCells: problematicCells
            };
            
            console.log('NAVBOX CSS ANALYSIS:', JSON.stringify(debugInfo, null, 2));
            if (window.RenderTimeline) {
                window.RenderTimeline.log('NAVBOX CSS ANALYSIS: ' + JSON.stringify(debugInfo));
            }
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
