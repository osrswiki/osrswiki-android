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

    function logTimeline(message) {
        if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
            window.RenderTimeline.log(message);
        }
    }

    function restoreDeferredImages(root) {
        if (!root || !root.querySelectorAll) return 0;
        const deferredImages = root.querySelectorAll('img[data-osrs-deferred-src]');
        deferredImages.forEach(function(img) {
            const src = img.getAttribute('data-osrs-deferred-src');
            if (src) {
                img.setAttribute('src', src);
                img.removeAttribute('data-osrs-deferred-src');
            }
            const srcset = img.getAttribute('data-osrs-deferred-srcset');
            if (srcset) {
                img.setAttribute('srcset', srcset);
                img.removeAttribute('data-osrs-deferred-srcset');
            }
            const sizes = img.getAttribute('data-osrs-deferred-sizes');
            if (sizes) {
                img.setAttribute('sizes', sizes);
                img.removeAttribute('data-osrs-deferred-sizes');
            }
            img.classList.remove('osrs-deferred-table-image');
        });
        return deferredImages.length;
    }

    const ARTICLE_FAMILY_MARKERS = {
        moneyMakingGuidePrefix: 'Money making guide/',
        calculatorNamespace: 'Calculator:',
        trailblazerTasksTitle: 'Trailblazer Reloaded League/Tasks',
        payToPlayTrainingPrefix: 'Pay-to-play'
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
        titleWrapper.textContent = '';

        var label = document.createElement('span');
        label.className = 'collapsible-label';
        label.textContent = captionText;

        var state = document.createElement('span');
        state.className = 'collapsible-state';
        state.textContent = stateText;

        titleWrapper.appendChild(label);
        titleWrapper.appendChild(state);
    }

    function toggleCollapsible(container, titleWrapper, captionText, scrollToTop) {
        var content = container.querySelector('.collapsible-content');
        if (!content) return;
        
        var isCurrentlyCollapsed = container.classList.contains('collapsed');
        var mapPlaceholder = content.querySelector('.mw-kartographer-map');
        var mapId = mapPlaceholder ? mapPlaceholder.id : null;
        
        if (window.OsrsWikiBridge && mapId) {
            window.OsrsWikiBridge.onCollapsibleToggled(mapId, isCurrentlyCollapsed);
        }
        
        if (isCurrentlyCollapsed) {
            const restoredCount = restoreDeferredImages(content);
            if (restoredCount > 0) {
                logTimeline('Event: DeferredImagesRestored count=' + restoredCount);
            }
            container.classList.remove('collapsed');
            content.style.height = 'auto';
        } else {
            container.classList.add('collapsed');
            content.style.height = '0px';
            
            // Scroll to top of collapsed container if requested (from footer)
            if (scrollToTop) {
                setTimeout(function() {
                    container.scrollIntoView({ 
                        behavior: 'smooth', 
                        block: 'start' 
                    });
                }, 100); // Small delay to let collapse animation start
            }
        }
        
        updateHeaderText(container, titleWrapper, captionText);
        if (typeof window.refreshHorizontalScrollAffordances === 'function') {
            requestAnimationFrame(window.refreshHorizontalScrollAffordances);
        }
    }

    function setupCollapsible(header, container, titleWrapper, captionText) {
        var content = container.querySelector('.collapsible-content');
        if (!content) return;
        if (!container.classList.contains('collapsed')) {
            restoreDeferredImages(content);
        }
        
        // Create close footer that mirrors the header design
        var closeFooter = document.createElement('div');
        closeFooter.className = 'collapsible-close-footer';
        var closeButton = document.createElement('div');
        closeButton.className = 'collapsible-close-button';
        closeButton.setAttribute('role', 'button');
        closeButton.setAttribute('tabindex', '0');
        closeButton.setAttribute('aria-label', 'Collapse ' + captionText);
        
        var footerTitleWrapper = document.createElement('div');
        footerTitleWrapper.className = 'title-wrapper';
        footerTitleWrapper.textContent = 'Close';
        
        var icon = document.createElement('span');
        icon.className = 'icon';
        
        closeButton.appendChild(footerTitleWrapper);
        closeButton.appendChild(icon);
        closeFooter.appendChild(closeButton);
        content.appendChild(closeFooter);
        
        // Header click handler (no scroll)
        header.addEventListener('click', function() {
            toggleCollapsible(container, titleWrapper, captionText, false);
        });
        
        // Close footer click handler (scroll to top)
        closeButton.addEventListener('click', function(e) {
            e.stopPropagation(); // Prevent bubbling to container
            // Only collapse if currently expanded
            if (!container.classList.contains('collapsed')) {
                toggleCollapsible(container, titleWrapper, captionText, true);
            }
        });
        
        // Keyboard support for close footer (scroll to top)
        closeButton.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                e.stopPropagation();
                if (!container.classList.contains('collapsed')) {
                    toggleCollapsible(container, titleWrapper, captionText, true);
                }
            }
        });
    }

    function normalizeText(text) {
        return (text || '').replace(/\s+/g, ' ').trim();
    }

    function firstText(root, selector) {
        var element = root ? root.querySelector(selector) : null;
        return element ? normalizeText(element.textContent) : '';
    }

    function getArticleContext() {
        var title = '';
        if (window.RLCONF) {
            title = window.RLCONF.wgTitle || window.RLCONF.wgPageName || '';
        }
        title = title || firstText(document, 'h1.page-header') || document.title || '';
        var normalizedTitle = normalizeText(title).replace(/_/g, ' ');
        var lowerTitle = normalizedTitle.toLowerCase();
        var hasMoneyMakingMethod = !!document.querySelector('table.mmg-table');
        var hasTaskTable = !!document.querySelector('table.tbrl-tasks');
        var hasCalculatorControls = !!document.querySelector('input, select, button, textarea');

        return {
            title: normalizedTitle,
            isMoneyMakingGuide: hasMoneyMakingMethod ||
                lowerTitle.indexOf(ARTICLE_FAMILY_MARKERS.moneyMakingGuidePrefix.toLowerCase()) === 0,
            isCalculator: lowerTitle.indexOf(ARTICLE_FAMILY_MARKERS.calculatorNamespace.toLowerCase()) === 0 ||
                hasCalculatorControls ||
                lowerTitle.indexOf(' calculator') !== -1,
            isTaskPage: hasTaskTable ||
                lowerTitle === ARTICLE_FAMILY_MARKERS.trailblazerTasksTitle.toLowerCase() ||
                lowerTitle.indexOf('/tasks') !== -1 ||
                lowerTitle.indexOf(' task') !== -1,
            isTrainingPage: lowerTitle.indexOf('training') !== -1 ||
                lowerTitle.indexOf(ARTICLE_FAMILY_MARKERS.payToPlayTrainingPrefix.toLowerCase()) === 0 ||
                lowerTitle.indexOf('guide/') !== -1,
            isEquipmentPage: !!document.querySelector('table.infobox-bonuses, table.combat-styles')
        };
    }

    function tableHeadings(table) {
        return Array.prototype.slice.call(table.querySelectorAll('tr:first-child th, thead th'))
            .map(function(cell) { return normalizeText(cell.textContent); })
            .filter(Boolean)
            .slice(0, 4);
    }

    function firstDataRowText(table) {
        var rows = Array.prototype.slice.call(table.querySelectorAll('tr'));
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].querySelector('td')) {
                return normalizeText(rows[i].textContent);
            }
        }
        return '';
    }

    function isGiantTaskTable(elementForTitle) {
        if (!elementForTitle || !elementForTitle.matches('table')) return false;
        var rowCount = elementForTitle.querySelectorAll('tr').length;
        var textLength = normalizeText(elementForTitle.textContent).length;
        return elementForTitle.classList.contains('tbrl-tasks') ||
            elementForTitle.classList.contains('sticky-header') ||
            rowCount > 140 ||
            textLength > 45000;
    }

    function isGenericCollapseLabel(label, defaultTitle) {
        var normalized = normalizeText(label).toLowerCase();
        var genericLabels = ['table', 'infobox', 'navigation', 'section'];
        return !normalized ||
            genericLabels.indexOf(normalized) !== -1 ||
            normalized === normalizeText(defaultTitle).toLowerCase();
    }

    function isTaskCriticalTable(context, elementForTitle, captionText, index) {
        if (!elementForTitle || !elementForTitle.matches('table')) return false;
        var label = normalizeText(captionText).toLowerCase();
        var classes = elementForTitle.className || '';
        var headings = tableHeadings(elementForTitle).join(' ').toLowerCase();
        var combined = [label, classes.toLowerCase(), headings].join(' ');

        if (context.isMoneyMakingGuide) {
            return classes.indexOf('mmg-table') !== -1 || index === 0;
        }
        if (context.isCalculator) {
            return elementForTitle.querySelector('input, select, button, textarea') !== null ||
                combined.indexOf('calculator') !== -1;
        }
        if (context.isTaskPage) {
            return classes.indexOf('tbrl-tasks') !== -1 ||
                combined.indexOf('task') !== -1 ||
                combined.indexOf('area') !== -1 ||
                label.indexOf('trailblazer reloaded') !== -1;
        }
        if (context.isEquipmentPage) {
            return classes.indexOf('combat-styles') !== -1 ||
                classes.indexOf('infotable-bonuses') !== -1 ||
                combined.indexOf('combat style') !== -1 ||
                combined.indexOf('attack bonuses') !== -1;
        }
        if (context.isTrainingPage) {
            return index < 2 ||
                combined.indexOf('training') !== -1 ||
                combined.indexOf('level') !== -1 ||
                combined.indexOf('equipment') !== -1 ||
                combined.indexOf('device') !== -1;
        }
        return false;
    }

    function buildCollapsedSummary(elementForTitle, captionText) {
        if (!elementForTitle || !elementForTitle.matches('table')) return '';
        var headings = tableHeadings(elementForTitle);
        var firstRow = firstDataRowText(elementForTitle);
        var pieces = [];

        if (headings.length) {
            pieces.push(headings.join(', '));
        }
        if (firstRow) {
            pieces.push(firstRow.substring(0, 170));
        }
        if (!pieces.length) {
            pieces.push(normalizeText(captionText));
        }
        return pieces.filter(Boolean).join(' - ');
    }

    function priorityTreatment(selector, index, elementForTitle, captionText) {
        var context = getArticleContext();
        var taskCritical = selector === 'table.wikitable' &&
            isTaskCriticalTable(context, elementForTitle, captionText, index);
        var giantTaskTable = taskCritical && isGiantTaskTable(elementForTitle);

        if (selector === 'table.navbox') {
            return { mode: 'default', labelKind: 'secondary', taskCritical: false };
        }
        if (taskCritical && giantTaskTable) {
            return { mode: 'summary', labelKind: 'primary-summary', taskCritical: true };
        }
        if (taskCritical) {
            return { mode: 'expanded', labelKind: 'primary', taskCritical: true };
        }
        return {
            mode: 'default',
            labelKind: isGenericCollapseLabel(captionText, defaultTitleFromSelector(selector)) ? 'generic' : 'secondary',
            taskCritical: false
        };
    }

    function defaultTitleFromSelector(selector) {
        if (selector === 'table.infobox') return 'Infobox';
        if (selector === 'table.navbox') return 'Navigation';
        if (selector === 'table.questdetails') return 'Quest details';
        if (selector === 'table.mw-collapsible') return 'Table';
        if (selector === 'table.wikitable') return 'Table';
        return 'Section';
    }

    function findContextHeading(element) {
        var cursor = element;
        while (cursor && cursor !== document.body) {
            var previous = cursor.previousElementSibling;
            while (previous) {
                var heading = previous.matches && previous.matches('h1, h2, h3, h4, h5, h6')
                    ? previous
                    : previous.querySelector && previous.querySelector('.mw-heading h1, .mw-heading h2, .mw-heading h3, .mw-heading h4, .mw-heading h5, .mw-heading h6, h1, h2, h3, h4, h5, h6');
                var headingText = heading ? normalizeText(heading.textContent) : '';
                if (headingText) {
                    return headingText;
                }
                previous = previous.previousElementSibling;
            }
            cursor = cursor.parentElement;
        }
        return '';
    }

    function deriveCaptionText(selector, defaultTitle, elementToWrap, elementForTitle) {
        if (selector === 'table.infobox') {
            if (elementForTitle.classList.contains('infobox-bonuses')) {
                return findContextHeading(elementToWrap) ||
                    firstText(elementForTitle, '.infobox-subheader') ||
                    'Equipment bonuses';
            }
            return firstText(elementForTitle, '.infobox-header') ||
                firstText(elementForTitle, 'caption') ||
                findContextHeading(elementToWrap) ||
                defaultTitle;
        }

        if (selector === 'table.navbox') {
            return firstText(elementForTitle, '.navbox-title-name') ||
                firstText(elementForTitle, '.navbox-title') ||
                'Navigation';
        }

        if (selector === 'table.questdetails') {
            return findContextHeading(elementToWrap) ||
                firstText(elementForTitle, 'caption') ||
                'Quest details';
        }

        if (selector === 'table.mw-collapsible') {
            return firstText(elementForTitle, 'caption') ||
                firstText(elementForTitle, 'th') ||
                findContextHeading(elementToWrap) ||
                defaultTitle;
        }

        var caption = firstText(elementForTitle, 'caption') ||
            findContextHeading(elementToWrap) ||
            firstText(elementForTitle, 'th') ||
            defaultTitle;

        var className = elementForTitle.className || '';
        if (selector === 'table.wikitable') {
            if (className.indexOf('mmg-table') !== -1) {
                return caption + ' method table';
            }
            if (className.indexOf('tbrl-tasks') !== -1) {
                return 'League task list';
            }
            if (className.indexOf('combat-styles') !== -1) {
                return 'Combat styles table';
            }
            if (className.indexOf('infotable-bonuses') !== -1) {
                return 'Equipment comparison table';
            }
            if (caption === 'Level') {
                return 'Level unlocks table';
            }
            if (caption === defaultTitle) {
                var headings = tableHeadings(elementForTitle);
                if (headings.length) {
                    return headings.slice(0, 2).join(' / ') + ' table';
                }
            }
        }

        return caption;
    }

    function isAlwaysExpandedContent(selector, index, elementForTitle) {
        return selector === 'table.infobox' &&
            (index === 0 || elementForTitle.classList.contains('infobox-bonuses'));
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
        var captionText = deriveCaptionText(selector, defaultTitle, elementToWrap, elementForTitle);
        var treatment = priorityTreatment(selector, index, elementForTitle, captionText);
        // Check global preference variable for initial collapse state
        const globalCollapsePreference = (typeof window.OSRS_TABLE_COLLAPSED !== 'undefined') ?
            window.OSRS_TABLE_COLLAPSED : true; // Default to collapsed if not set
        const shouldStartCollapsed = globalCollapsePreference &&
            !isAlwaysExpandedContent(selector, index, elementForTitle) &&
            treatment.mode !== 'expanded';
        
        // Base container classes
        var containerClasses = ['collapsible-container'];
        if (shouldStartCollapsed) {
            containerClasses.push('collapsed');
        }
        if (treatment.mode === 'expanded') {
            containerClasses.push('collapsible-priority-primary');
        } else if (treatment.mode === 'summary') {
            containerClasses.push('collapsible-priority-summary');
        }
        
        // Add content-type classes for CSS styling (replaces :has() selectors)
        if (selector === 'table.wikitable') {
            containerClasses.push('collapsible-wikitable');
        } else if (selector === 'table.questdetails') {
            containerClasses.push('collapsible-questdetails');
        } else if (selector === 'table.mw-collapsible') {
            containerClasses.push('collapsible-explicit-mw-collapsible');
        } else if (selector === 'table.navbox') {
            containerClasses.push('collapsible-navbox');
        } else if (selector === 'table.infobox') {
            containerClasses.push('collapsible-infobox');
            if (index === 0) {
                containerClasses.push('collapsible-primary-infobox');
            }
            if (elementForTitle.classList.contains('infobox-bonuses')) {
                containerClasses.push('collapsible-bonuses-infobox');
            }
            
            // Determine floating behavior for infoboxes
            if (elementToWrap.classList.contains('skill-info') || 
                elementToWrap.classList.contains('infobox-bonuses') ||
                elementToWrap.matches('[class*="-center"]') ||
                elementToWrap.classList.contains('mw-halign-center') ||
                elementToWrap.classList.contains('mw-halign-none')) {
                containerClasses.push('collapsible-no-float');
            } else {
                // Most infoboxes float right by default
                containerClasses.push('collapsible-float-right');
            }
        }
        
        // Check for explicit floating classes on the element
        if (elementToWrap.matches('[class*="floatright"], [class*="-right"]') ||
            elementToWrap.classList.contains('archivelist') ||
            elementToWrap.classList.contains('shortcut') ||
            elementToWrap.classList.contains('mw-halign-right') ||
            elementToWrap.classList.contains('multi-infobox')) {
            containerClasses.push('collapsible-float-right');
        } else if (elementToWrap.matches('[class*="floatleft"], [class*="-left"]') ||
                   elementToWrap.classList.contains('mw-halign-left')) {
            containerClasses.push('collapsible-float-left');
        }
        
        container.className = containerClasses.join(' ');
        container.setAttribute('data-collapse-label-kind', treatment.labelKind);
        if (treatment.taskCritical) {
            container.setAttribute('data-task-critical', 'true');
        }
        var header = document.createElement('div');
        header.className = 'collapsible-header';
        var titleWrapper = document.createElement('div');
        titleWrapper.className = 'title-wrapper';

        var icon = document.createElement('span');
        icon.className = 'icon';
        header.appendChild(titleWrapper);
        header.appendChild(icon);
        elementToWrap.parentNode.insertBefore(container, elementToWrap);
        container.appendChild(header);
        if (shouldStartCollapsed && treatment.mode === 'summary') {
            var summaryText = buildCollapsedSummary(elementForTitle, captionText);
            if (summaryText) {
                var summary = document.createElement('div');
                summary.className = 'collapsible-summary';
                summary.textContent = summaryText;
                container.appendChild(summary);
            }
        }
        var content = document.createElement('div');
        content.className = 'collapsible-content';
        content.appendChild(elementToWrap);
        container.appendChild(content);
        updateHeaderText(container, titleWrapper, captionText);
        setupCollapsible(header, container, titleWrapper, captionText);
    }

    function transformSections() {
        document.querySelectorAll('div.mw-collapsible').forEach(function(collapsibleDiv, index) {
            // Skip if already transformed
            if (collapsibleDiv.closest('.collapsible-container')) {
                return;
            }

            const triggerSpan = collapsibleDiv.querySelector('.collapsed-sec');
            if (!triggerSpan) {
                return;
            }

            // Find the content div
            const originalContent = collapsibleDiv.querySelector('.mw-collapsible-content');
            if (!originalContent) {
                return;
            }

            // Determine initial state - check global preference first, then fallback to mw-collapsed class
            const globalPreference = (typeof window.OSRS_TABLE_COLLAPSED !== 'undefined') ? window.OSRS_TABLE_COLLAPSED : null;
            const shouldStartCollapsed = (globalPreference !== null) ? 
                globalPreference : 
                collapsibleDiv.classList.contains('mw-collapsed');

            // Create container structure
            var container = document.createElement('div');
            container.className = shouldStartCollapsed ? 'collapsible-container collapsed' : 'collapsible-container';
            container.setAttribute('data-collapse-label-kind', 'secondary');
            var header = document.createElement('div');
            header.className = 'collapsible-header';
            var titleWrapper = document.createElement('div');
            titleWrapper.className = 'title-wrapper';
            
            // Try to determine a good title
            var captionText = 'Section';
            // Look for preceding heading or other context clues
            const prevHeading = collapsibleDiv.previousElementSibling;
            if (prevHeading && (prevHeading.tagName.match(/^H[1-6]$/))) {
                captionText = prevHeading.textContent.trim();
            }

            var icon = document.createElement('span');
            icon.className = 'icon';
            header.appendChild(titleWrapper);
            header.appendChild(icon);

            // Create content container and move content
            var content = document.createElement('div');
            content.className = 'collapsible-content';
            while (originalContent.firstChild) {
                content.appendChild(originalContent.firstChild);
            }

            // Assemble the new structure
            container.appendChild(header);
            container.appendChild(content);

            // Replace the original element
            collapsibleDiv.parentNode.insertBefore(container, collapsibleDiv);
            collapsibleDiv.parentNode.removeChild(collapsibleDiv);

            // Set up header text and behavior
            updateHeaderText(container, titleWrapper, captionText);
            setupCollapsible(header, container, titleWrapper, captionText);
        });
    }

    function shouldTransformExplicitCollapsibleTable(table) {
        if (!table || !table.matches || !table.matches('table.mw-collapsible')) {
            return false;
        }
        if (table.closest('.collapsible-container')) {
            return false;
        }
        return !table.matches(
            'table.infobox, table.wikitable, table.navbox, ' +
            'table.messagebox, table.ambox, table.mbox, table.notebox, ' +
            'table.gallery, table[role="presentation"]'
        );
    }

    function shouldTransformQuestDetailsTable(table) {
        if (!table || !table.matches || !table.matches('table.questdetails')) {
            return false;
        }
        if (table.closest('.collapsible-container')) {
            return false;
        }
        if (table.parentElement && table.parentElement.closest('table')) {
            return false;
        }
        return true;
    }

    function collectCollapseMetrics() {
        var containers = Array.prototype.slice.call(document.querySelectorAll('.collapsible-container'));
        var controls = containers.map(function(container) {
            var label = firstText(container, '.collapsible-label');
            return {
                label: label,
                labelKind: container.getAttribute('data-collapse-label-kind') || '',
                collapsed: container.classList.contains('collapsed'),
                taskCritical: container.getAttribute('data-task-critical') === 'true',
                hasSummary: !!container.querySelector(':scope > .collapsible-summary')
            };
        });

        return {
            collapseControls: controls.length,
            collapsedControls: controls.filter(function(control) { return control.collapsed; }).length,
            genericCollapseLabels: controls.filter(function(control) {
                return control.labelKind === 'generic' || isGenericCollapseLabel(control.label, '');
            }).length,
            collapsedTaskCriticalControls: controls.filter(function(control) {
                return control.taskCritical && control.collapsed && !control.hasSummary;
            }).length,
            summarizedTaskCriticalControls: controls.filter(function(control) {
                return control.taskCritical && control.collapsed && control.hasSummary;
            }).length,
            controls: controls
        };
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
        const startedAt = performance.now ? performance.now() : Date.now();
        preloadCollapsibleImages();

        document.querySelectorAll('table.infobox').forEach((table, i) => {
            const switcherContainer = table.closest('.infobox-switch');
            const elementToTransform = switcherContainer || table;
            transformElement('table.infobox', 'Infobox', i, elementToTransform, table);
        });

        document.querySelectorAll('table.wikitable').forEach((el, i) => transformElement('table.wikitable', 'Table', i, el, el));
        document.querySelectorAll('table.navbox').forEach((el, i) => transformElement('table.navbox', 'Navigation', i, el, el));
        document.querySelectorAll('table.questdetails').forEach((el, i) => {
            if (shouldTransformQuestDetailsTable(el)) {
                transformElement('table.questdetails', 'Quest details', i, el, el);
            }
        });
        document.querySelectorAll('table.mw-collapsible').forEach((el, i) => {
            if (shouldTransformExplicitCollapsibleTable(el)) {
                transformElement('table.mw-collapsible', 'Table', i, el, el);
            }
        });
        
        transformSections();
        
        tryInitializeSwitcher();

        // Add CSS class to signal transforms are complete
        document.body.classList.add('js-transforms-complete');
        const finishedAt = performance.now ? performance.now() : Date.now();
        const transformDuration = Math.round(finishedAt - startedAt);
        logTimeline(
            'Event: CollapsibleTransformsComplete durationMs=' + transformDuration +
            ' containers=' + document.querySelectorAll('.collapsible-container').length +
            ' deferredImages=' + document.querySelectorAll('img[data-osrs-deferred-src]').length
        );
        
        // Signal to native that styling and transforms are complete,
        // so the page can be revealed without FOUC.
        logTimeline('Event: StylingScriptsComplete');
        window.OSRSCollapseMetrics = collectCollapseMetrics();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
