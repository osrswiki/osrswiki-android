package com.omiyawaki.osrswiki.page

import android.util.Log

class PageHtmlBuilder {
    companion object {
        private const val TAG = "PageHtmlBuilder"
    }

    /**
     * Wraps the raw HTML fragment from the API in a full HTML document.
     * This method injects CSS for mobile scaling and table scrolling,
     * and the necessary JavaScript for interactive table sorting.
     *
     * @param content The HTML fragment from the MediaWiki API.
     * @return A full, well-formed HTML document as a string.
     */
    fun buildFullHtmlDocument(content: String): String {
        val fullHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 8px;
                        overflow-x: hidden; /* Prevents the body itself from scrolling horizontally */
                    }
                    .scrollable-table-wrapper {
                        width: 100%;
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch; /* Enables momentum scrolling on iOS */
                        border: 1px solid #555; /* Optional: adds a border to indicate scrollable area */
                    }
                    /* Style the scrollbar to be less obtrusive */
                    .scrollable-table-wrapper::-webkit-scrollbar {
                        height: 8px;
                    }
                    .scrollable-table-wrapper::-webkit-scrollbar-thumb {
                        background: #888;
                        border-radius: 4px;
                    }
                </style>
                <script src="file:///android_asset/js/jquery.js"></script>
                <script src="file:///android_asset/js/jquery.tablesorter.js"></script>
            </head>
            <body>
                $content
                <script>
                    // This script runs after the DOM is parsed to wrap wide tables.
                    var tables = document.querySelectorAll('table.wikitable, table.align-right-2');
                    tables.forEach(function(table) {
                        if (table.parentElement.className !== 'scrollable-table-wrapper') {
                            var wrapper = document.createElement('div');
                            wrapper.className = 'scrollable-table-wrapper';
                            table.parentNode.insertBefore(wrapper, table);
                            wrapper.appendChild(table);
                        }
                    });
                </script>
                <script>
                    // Initialize the tablesorter on any wikitable that is sortable.
                    try {
                        $(document).ready(function() {
                            console.log("Document ready. Initializing tablesorter.");
                            var tables = $('table.wikitable.sortable');
                            if (tables.length > 0) {
                                console.log("Found " + tables.length + " sortable tables.");
                                tables.tablesorter();
                                console.log("Tablesorter initialized successfully.");
                            } else {
                                console.log("No sortable tables found on this page.");
                            }
                        });
                    } catch (e) {
                        console.error("Error initializing tablesorter: " + e.message);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        Log.d(TAG, "Constructed full HTML document. Length: ${fullHtml.length}")
        // To debug the exact HTML, uncomment the following line:
        // Log.v(TAG, fullHtml)
        return fullHtml
    }
}
