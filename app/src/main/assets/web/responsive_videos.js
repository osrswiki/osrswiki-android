(function() {
    'use strict';

    // Helper function to log to Android
    function log(message) {
        if (window.OsrsWikiBridge && typeof window.OsrsWikiBridge.log === 'function') {
            window.OsrsWikiBridge.log('[ResponsiveVideos] ' + message);
        }
    }

    /**
     * Makes video iframes responsive by removing HTML dimension attributes
     * and wrapping them in responsive containers.
     */
    function makeVideosResponsive() {
        // Find all video iframes
        const videoSelectors = [
            'iframe[src*="youtube.com"]',
            'iframe[src*="youtu.be"]', 
            'iframe[src*="youtube-nocookie.com"]',
            'iframe[src*="vimeo.com"]',
            'iframe[src*="dailymotion.com"]',
            'iframe[src*="twitch.tv"]'
        ];
        
        const videoIframes = document.querySelectorAll(videoSelectors.join(', '));
        log('Found ' + videoIframes.length + ' video iframes to make responsive');
        
        videoIframes.forEach(function(iframe, index) {
            try {
                makeIframeResponsive(iframe, index);
            } catch (error) {
                log('Error making iframe ' + index + ' responsive: ' + error.message);
            }
        });
    }

    /**
     * Makes a single iframe responsive by using existing MediaWiki containers
     * as responsive containers instead of creating competing wrappers.
     */
    function makeIframeResponsive(iframe, index) {
        const originalWidth = iframe.getAttribute('width');
        const originalHeight = iframe.getAttribute('height');
        const src = iframe.src;
        
        log('Processing iframe ' + index + ': ' + src + 
            ' (original: ' + originalWidth + 'x' + originalHeight + ')');
        
        // Skip if already processed (check for our marker class)
        if (iframe.classList.contains('responsive-video-processed')) {
            log('Iframe ' + index + ' already processed, skipping');
            return;
        }
        
        // Calculate aspect ratio from original dimensions if available
        let aspectRatio = 56.25; // Default 16:9 ratio
        if (originalWidth && originalHeight) {
            const width = parseInt(originalWidth, 10);
            const height = parseInt(originalHeight, 10);
            if (width > 0 && height > 0) {
                aspectRatio = (height / width) * 100;
                log('Calculated aspect ratio for iframe ' + index + ': ' + aspectRatio.toFixed(2) + '%');
            }
        }
        
        // Handle MediaWiki parent containers - use them as responsive containers
        const responsiveContainer = setupMediaWikiResponsiveContainer(iframe, index, aspectRatio);
        
        if (responsiveContainer) {
            // Remove dimension attributes that override CSS
            iframe.removeAttribute('width');
            iframe.removeAttribute('height');
            iframe.removeAttribute('style'); // Remove any inline styles
            
            // Make iframe fill the responsive container
            iframe.style.position = 'absolute';
            iframe.style.top = '0';
            iframe.style.left = '0';
            iframe.style.width = '100%';
            iframe.style.height = '100%';
            iframe.style.border = 'none';
            
            // Add responsive class to iframe for additional styling
            iframe.classList.add('responsive-video-iframe', 'responsive-video-processed');
            
            log('Successfully made iframe ' + index + ' responsive using MediaWiki container with ' + aspectRatio.toFixed(2) + '% aspect ratio');
        } else {
            log('No MediaWiki container found for iframe ' + index + ', skipping responsive setup');
        }
    }

    /**
     * Sets up MediaWiki containers as responsive containers using the padding-bottom technique.
     * Returns the container that was set up as responsive, or null if none found.
     */
    function setupMediaWikiResponsiveContainer(iframe, index, aspectRatio) {
        // Look for MediaWiki embed containers
        const embedVideoFigure = iframe.closest('figure.embedvideo');
        const embedVideoWrapper = iframe.closest('.embedvideo-wrapper');
        
        // Clean up figure container
        if (embedVideoFigure) {
            log('Found MediaWiki embedvideo figure for iframe ' + index + ', making responsive');
            
            // Remove constraining inline styles
            embedVideoFigure.removeAttribute('style');
            embedVideoFigure.style.width = '100%';
            embedVideoFigure.style.maxWidth = '100%';
            embedVideoFigure.style.margin = '0.5em auto';
            
            // Add responsive class
            embedVideoFigure.classList.add('responsive-embed-figure');
        }
        
        // Use the wrapper as the responsive container
        if (embedVideoWrapper) {
            log('Found MediaWiki embedvideo-wrapper for iframe ' + index + ', setting up as responsive container');
            
            // Remove constraining inline styles
            embedVideoWrapper.removeAttribute('style');
            
            // Apply responsive container styling with padding-bottom technique
            embedVideoWrapper.style.position = 'relative';
            embedVideoWrapper.style.width = '100%';
            embedVideoWrapper.style.height = '0';
            embedVideoWrapper.style.paddingBottom = aspectRatio + '%';
            embedVideoWrapper.style.overflow = 'hidden';
            
            // Add responsive class
            embedVideoWrapper.classList.add('responsive-embed-wrapper');
            
            // Clean up any other constraining parent containers
            cleanupConstrainingParents(iframe, index);
            
            return embedVideoWrapper;
        }
        
        log('No MediaWiki embedvideo-wrapper found for iframe ' + index);
        return null;
    }

    /**
     * Cleans up any other parent containers that might have constraining styles.
     */
    function cleanupConstrainingParents(iframe, index) {
        let current = iframe.parentElement;
        let depth = 0;
        
        while (current && current !== document.body && depth < 5) {
            depth++;
            
            // Skip the embedvideo-wrapper since we're using it as our responsive container
            if (current.classList.contains('embedvideo-wrapper')) {
                current = current.parentElement;
                continue;
            }
            
            // Check for elements with fixed width/height inline styles
            const currentStyle = current.getAttribute('style');
            if (currentStyle && (currentStyle.includes('width:') || currentStyle.includes('height:'))) {
                log('Found constraining parent container at depth ' + depth + ' for iframe ' + index + ', cleaning up');
                
                // Make it responsive but preserve other styles
                const styles = currentStyle.split(';');
                const newStyles = styles.filter(function(style) {
                    const trimmed = style.trim();
                    return !(trimmed.startsWith('width:') || trimmed.startsWith('height:'));
                });
                
                current.setAttribute('style', newStyles.join(';'));
                current.style.width = '100%';
                current.style.maxWidth = '100%';
                current.style.height = 'auto';
            }
            
            current = current.parentElement;
        }
    }

    /**
     * Handles any iframes that might be loaded dynamically after page load
     */
    function handleDynamicIframes() {
        // Use MutationObserver to watch for new iframes
        if (typeof MutationObserver !== 'undefined') {
            const observer = new MutationObserver(function(mutations) {
                let hasNewIframes = false;
                
                mutations.forEach(function(mutation) {
                    if (mutation.type === 'childList') {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === Node.ELEMENT_NODE) {
                                // Check if the added node is an iframe or contains iframes
                                const iframes = node.tagName === 'IFRAME' ? [node] : 
                                               node.querySelectorAll ? Array.from(node.querySelectorAll('iframe')) : [];
                                
                                if (iframes.length > 0) {
                                    hasNewIframes = true;
                                }
                            }
                        });
                    }
                });
                
                if (hasNewIframes) {
                    log('Detected new iframes, re-running responsive video processing');
                    setTimeout(makeVideosResponsive, 100); // Small delay to let content settle
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            log('Set up MutationObserver for dynamic iframe detection');
        }
    }

    /**
     * Initialize responsive video processing
     */
    function initialize() {
        log('Initializing responsive video processing');
        
        // Process existing videos immediately
        makeVideosResponsive();
        
        // Set up dynamic iframe handling
        handleDynamicIframes();
        
        // Re-run after a delay to catch any slow-loading content
        setTimeout(function() {
            log('Running delayed responsive video check');
            makeVideosResponsive();
        }, 2000);
    }

    // Run when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        // DOM is already ready
        initialize();
    }

    // Also run on window load to catch any late-loading content
    window.addEventListener('load', function() {
        log('Window loaded, running final responsive video check');
        setTimeout(makeVideosResponsive, 500);
    });

})();