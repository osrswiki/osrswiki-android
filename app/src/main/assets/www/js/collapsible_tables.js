(function() {
    'use strict';

    /**
     * Finds all tables with the 'wikitable' class and wraps them in a
     * collapsible container structure, applying the necessary event listeners.
     */
    function makeTablesCollapsible() {
        document.querySelectorAll('table.wikitable').forEach(function(table) {
            // Ensure we don't re-wrap a table that has already been processed.
            if (table.closest('.collapsible-container')) {
                return;
            }

            const captionText = table.querySelector('caption')?.textContent.trim() || 'Table';

            // 1. Create the main container
            const container = document.createElement('div');
            container.className = 'collapsible-container collapsed'; // Start as collapsed

            // 2. Create the header element
            const header = document.createElement('div');
            header.className = 'section-header';

            // 3. Create the title element inside the header
            const title = document.createElement('span');
            title.className = 'section-header-title';
            title.textContent = `More information (${captionText})`;

            // 4. Create the icon element inside the header
            const icon = document.createElement('span');
            icon.className = 'section-header-icon icon-expand';

            // 5. Assemble the header
            header.appendChild(title);
            header.appendChild(icon);

            // 6. Create the content wrapper for the table
            const content = document.createElement('div');
            content.className = 'collapsible-content';

            // 7. Assemble the full container
            container.appendChild(header);
            container.appendChild(content);

            // 8. Move the original table into the new structure
            table.parentNode.insertBefore(container, table);
            content.appendChild(table);

            // 9. Add the click listener to the header
            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                const isCollapsed = container.classList.contains('collapsed');
                icon.className = 'section-header-icon ' + (isCollapsed ? 'icon-expand' : 'icon-collapse');
            });
        });
    }

    // Expose a function to the global window object so it can be called from native code.
    window.osrswiki = window.osrswiki || {};
    window.osrswiki.makeTablesCollapsible = makeTablesCollapsible;

}());
