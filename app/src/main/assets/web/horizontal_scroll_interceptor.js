(function() {
    'use strict';

    // This script helps prevent the native Android swipe gestures (like back navigation)
    // from firing when the user is trying to scroll a horizontally-scrollable
    // element within the WebView, such as a wide table.

    let isHorizontallyScrollable = false;

    /**
     * Recursively checks if an element or any of its parents can scroll horizontally.
     * @param {HTMLElement} element The element to start checking from.
     * @returns {boolean} True if a horizontally scrollable element is found.
     */
    function checkHorizontalScroll(element) {
        if (!element || typeof element.scrollWidth === 'undefined' || typeof element.clientWidth === 'undefined') {
            return false;
        }
        // If the element's scrollable width is greater than its visible width,
        // it is horizontally scrollable.
        if (element.scrollWidth > element.clientWidth) {
            return true;
        }
        // If not, continue checking up the DOM tree.
        return checkHorizontalScroll(element.parentElement);
    }

    /**
     * Touch start event listener.
     * Checks if the touch is on a scrollable element and notifies the native app.
     */
    document.addEventListener('touchstart', function(event) {
        // Check if the touch target is inside a scrollable container.
        isHorizontallyScrollable = checkHorizontalScroll(event.target);
        if (window.NativeMapInterface && typeof window.NativeMapInterface.setHorizontalScroll === 'function') {
            // Inform the native layer whether a horizontal scroll is in progress.
            window.NativeMapInterface.setHorizontalScroll(isHorizontallyScrollable);
        }
    }, { passive: true });

    /**
     * Resets the scroll state when the touch gesture ends.
     */
    function resetScrollState() {
        // Only send a reset call if the state was previously true, to avoid unnecessary calls.
        if (isHorizontallyScrollable) {
            if (window.NativeMapInterface && typeof window.NativeMapInterface.setHorizontalScroll === 'function') {
                window.NativeMapInterface.setHorizontalScroll(false);
            }
            isHorizontallyScrollable = false;
        }
    }

    // Add listeners to reset the state when the touch gesture ends for any reason.
    document.addEventListener('touchend', resetScrollState, { passive: true });
    document.addEventListener('touchcancel', resetScrollState, { passive: true });

})();
