/*
 * JavaScript for MediaWiki collapsible elements.
 * This script handles multiple types of collapsible sections found in the wiki content.
 */
(function() {
    'use strict';

    /**
     * Toggles the 'mw-collapsed' class on a given container element.
     * @param {Element} container The element whose class should be toggled.
     */
    function toggle(container) {
        if (!container) return;
        container.classList.toggle('mw-collapsed');
    }

    /**
     * Initializes sections that use a non-standard 'CLICK HERE TO SHOW' span.
     * It finds these sections, makes the span clickable, and sets up the toggle logic.
     */
    function initInertCollapsibles() {
        var containers = document.querySelectorAll('.mw-collapsible:not(:has(.mw-collapsible-toggle))');
        containers.forEach(function(container) {
            var toggleSpan = container.querySelector('.collapsed-sec');
            if (toggleSpan && !toggleSpan.dataset.handlerAttached) {
                toggleSpan.dataset.handlerAttached = 'true';
                // Add a conventional [show] link for consistency.
                var toggleLink = document.createElement('a');
                toggleLink.href = '#';
                toggleLink.textContent = '[show]';

                var toggleContainer = document.createElement('span');
                toggleContainer.className = 'mw-collapsible-toggle';
                toggleContainer.appendChild(toggleLink);

                // Replace the "CLICK HERE..." text with the new toggle link.
                toggleSpan.innerHTML = '';
                toggleSpan.appendChild(toggleContainer);

                toggleLink.addEventListener('click', function(e) {
                    e.preventDefault();
                    toggle(container);
                    // Update the text after toggling.
                    var isCollapsed = container.classList.contains('mw-collapsed');
                    this.textContent = isCollapsed ? '[show]' : '[hide]';
                });
            }
        });
    }

    /**
     * Handles clicks from older `onclick="mfTempOpenSection(id)"` attributes on headings.
     * @param {number} id The numeric ID of the section to toggle.
     */
    function mfTempOpenSection(id) {
        var section = document.getElementById('mf-section-' + id);
        toggle(section);
    }

    // --- Global Assignments and Execution ---

    window.mfTempOpenSection = mfTempOpenSection;

    // --- Run Initializers ---

    function runAllInitializers() {
        initInertCollapsibles();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', runAllInitializers);
    } else {
        runAllInitializers();
    }
})();
