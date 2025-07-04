(function() {
    'use strict';

    function transformTables() {
        document.querySelectorAll('.wikitable').forEach(function(table) {
            if (table.parentNode.classList.contains('collapsible-container')) {
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
            }
            
            if (caption) {
                caption.style.display = 'none';
            }
            
            // Create separate elements for the main title and subtitle for easier manipulation.
            var strongTitle = document.createElement('strong');
            strongTitle.textContent = captionText + ':';

            var subtitleSpan = document.createElement('span');
            subtitleSpan.textContent = ' Tap to expand'; // Initial text

            titleWrapper.appendChild(strongTitle);
            titleWrapper.appendChild(subtitleSpan);

            var icon = document.createElement('span');
            icon.className = 'icon';

            header.appendChild(titleWrapper);
            header.appendChild(icon);

            table.parentNode.insertBefore(container, table);
            container.appendChild(header);
            container.appendChild(table);
            table.classList.add('collapsible-content');

            // Add click listener to the header
            header.addEventListener('click', function() {
                container.classList.toggle('collapsed');
                // Check the state AFTER toggling and update the text.
                if (container.classList.contains('collapsed')) {
                    subtitleSpan.textContent = ' Tap to expand';
                } else {
                    subtitleSpan.textContent = ' Tap to collapse';
                }
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', transformTables);
    } else {
        transformTables();
    }
})();
