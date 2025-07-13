// This script finds map container elements in the WebView, hides them,
// and passes their coordinates to the native Android layer via a JS interface.

(function() {
    // Use a timeout to ensure the DOM is fully rendered.
    setTimeout(function() {
        var mapPlaceholder = document.querySelector('.mw-kartographer-map');
        if (mapPlaceholder) {
            var rect = mapPlaceholder.getBoundingClientRect();
            if (window.NativeMapInterface) {
                window.NativeMapInterface.onMapFound(JSON.stringify({
                    y: rect.top + window.scrollY, // Correct Y-position by adding scroll offset.
                    x: rect.left,
                    width: rect.width,
                    height: rect.height
                }));
                // Make the original placeholder transparent, but keep it in the layout.
                mapPlaceholder.style.opacity = '0';
            }
        }
    }, 500);
})();
