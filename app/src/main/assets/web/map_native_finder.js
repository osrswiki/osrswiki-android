// This script finds map container elements in the WebView, hides them,
// and passes their coordinates and configuration to the native Android layer.

(function() {
    // Use a timeout to ensure the DOM is fully rendered.
    setTimeout(function() {
        var infobox = document.querySelector('.infobox');
        var mapPlaceholder = document.querySelector('.mw-kartographer-map');
        var infoboxImage = document.querySelector('.infobox-image img');

        if (infobox && mapPlaceholder && infoboxImage && window.NativeMapInterface) {
            var infoboxRect = infobox.getBoundingClientRect();
            var mapRect = mapPlaceholder.getBoundingClientRect();
            var imageRect = infoboxImage.getBoundingClientRect();

            window.NativeMapInterface.onMapFound(JSON.stringify({
                // Diagnostic bounds data
                infoboxBounds: { x: infoboxRect.left, y: infoboxRect.top, width: infoboxRect.width, height: infoboxRect.height },
                imageBounds: { x: imageRect.left, y: imageRect.top, width: imageRect.width, height: imageRect.height },
                mapBounds: { x: mapRect.left, y: mapRect.top, width: mapRect.width, height: mapRect.height },

                // Map configuration data from data-* attributes
                lat: mapPlaceholder.dataset.lat,
                lon: mapPlaceholder.dataset.lon,
                zoom: mapPlaceholder.dataset.zoom,
                plane: mapPlaceholder.dataset.plane
            }));
            // Make the original placeholder transparent, but keep it in the layout.
            mapPlaceholder.style.opacity = '0';
        }
    }, 500);
})();
