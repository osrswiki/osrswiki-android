// YouTube Player API with Custom Share Button
(function() {
    'use strict';

    function log(message) {
        if (window.OsrsWikiBridge && typeof window.OsrsWikiBridge.log === 'function') {
            window.OsrsWikiBridge.log('[ResponsiveVideos] ' + message);
        }
    }

    log('YouTube Player API script starting');

    // Load YouTube IFrame Player API
    function loadYouTubeAPI() {
        if (window.YT && window.YT.Player) {
            log('YouTube API already loaded');
            initializeYouTubePlayers();
            return;
        }

        log('Loading YouTube IFrame Player API');
        var tag = document.createElement('script');
        tag.src = 'https://www.youtube.com/iframe_api';
        var firstScriptTag = document.getElementsByTagName('script')[0];
        firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
    }

    // Initialize YouTube players when API is ready
    window.onYouTubeIframeAPIReady = function() {
        log('YouTube API ready');
        initializeYouTubePlayers();
    };

    function initializeYouTubePlayers() {
        var iframes = document.querySelectorAll('iframe[src*="youtube.com/embed/"], iframe[src*="youtube-nocookie.com/embed/"]');
        log('Found ' + iframes.length + ' YouTube iframes');

        iframes.forEach(function(iframe, index) {
            try {
                var src = iframe.src;
                var videoId = extractVideoId(src);
                if (!videoId) {
                    log('Could not extract video ID from: ' + src);
                    return;
                }

                log('Processing video ID: ' + videoId);
                createCustomPlayer(iframe, videoId, index);
            } catch (error) {
                log('Error processing iframe ' + index + ': ' + error.message);
            }
        });
    }

    function extractVideoId(url) {
        var match = url.match(/embed\/([^?&]+)/);
        return match ? match[1] : null;
    }

    function createCustomPlayer(originalIframe, videoId, index) {
        try {
            // Create container
            var container = document.createElement('div');
            container.className = 'youtube-player-container';
            container.style.position = 'relative';
            container.style.width = originalIframe.style.width || '100%';
            container.style.height = originalIframe.style.height || '315px';

            // Create player div
            var playerDiv = document.createElement('div');
            playerDiv.id = 'youtube-player-' + index;
            container.appendChild(playerDiv);

            // Replace original iframe
            originalIframe.parentNode.insertBefore(container, originalIframe);
            originalIframe.parentNode.removeChild(originalIframe);

            // Create YouTube player
            log('Creating YouTube player for video: ' + videoId);
            var player = new YT.Player(playerDiv.id, {
                width: container.style.width,
                height: container.style.height,
                videoId: videoId,
                playerVars: {
                    autoplay: 0,
                    controls: 1,
                    rel: 0
                },
                events: {
                    onReady: function(event) {
                        log('Player ready for video: ' + videoId);
                    },
                    onError: function(event) {
                        log('Player error for video ' + videoId + ': ' + event.data);
                    }
                }
            });

        } catch (error) {
            log('Error creating custom player: ' + error.message);
        }
    }

    // Start the process
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', loadYouTubeAPI);
    } else {
        loadYouTubeAPI();
    }

})();