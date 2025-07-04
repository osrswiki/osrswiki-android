package com.omiyawaki.osrswiki.page

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class PageHtmlBuilder(private val context: Context) {

    private val wikiContentCss: String by lazy { readAsset("styles/wiki_content.css") }
    private val tablesortJs: String by lazy { readAsset("js/tablesort.min.js") }

    fun buildFullHtmlDocument(title: String, bodyContent: String): String {
        val documentTitle = if (title.isBlank()) "OSRS Wiki" else title
        val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
        val finalBodyContent = titleHeaderHtml + bodyContent

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${documentTitle}</title>
                <style>
                    ${wikiContentCss}
                </style>
                <style>
                    /* Injected H1 title style with margin fix from commit 55fa705 */
                    h1.page-header {
                        font-family: 'PT Serif', 'Palatino', 'Georgia', serif;
                        border-bottom: 1px solid var(--sidebar-color);
                        padding-bottom: 0.2em;
                        margin-top: 0;
                        margin-bottom: 0.6em;
                        font-size: 1.8em;
                        line-height: 1.3;
                        color: var(--heading-color);
                    }
                    /* Robust, border-based sort indicators */
                    th[aria-sort] {
                        cursor: pointer;
                    }
                    th[aria-sort]::after {
                        content: '';
                        display: inline-block;
                        vertical-align: middle;
                        width: 0;
                        height: 0;
                        margin-left: 8px;
                    }
                    th[aria-sort="ascending"]::after {
                        border-left: 5px solid transparent;
                        border-right: 5px solid transparent;
                        border-bottom: 5px solid var(--link-color);
                    }
                    th[aria-sort="descending"]::after {
                        border-left: 5px solid transparent;
                        border-right: 5px solid transparent;
                        border-top: 5px solid var(--link-color);
                    }
                </style>
            </head>
            <body style="visibility: hidden;">
                ${finalBodyContent}
                <script>
                    ${tablesortJs}
                </script>
                <script>
                    // Extend Tablesort.js with a custom parser for comma-separated numbers.
                    // The library checks the first row of data to determine a column's type.
                    Tablesort.extend('numeric-comma', function(item) {
                        // Test if the cell content is a number with commas.
                        return /^-?[\d,]+(?:\.\d+)?$/.test(item);
                    }, function(a, b) {
                        // Clean the strings and compare them as numbers.
                        var cleanA = a.replace(/,/g, '');
                        var cleanB = b.replace(/,/g, '');
                        var numA = parseFloat(cleanA);
                        var numB = parseFloat(cleanB);

                        numA = isNaN(numA) ? 0 : numA;
                        numB = isNaN(numB) ? 0 : numB;

                        return numA - numB;
                    });
                </script>
                <script>
                    document.addEventListener('DOMContentLoaded', function() {
                        var tables = document.querySelectorAll('table.wikitable');

                        tables.forEach(function(table) {
                            if (table.parentElement.className !== 'scrollable-table-wrapper') {
                                var wrapper = document.createElement('div');
                                wrapper.className = 'scrollable-table-wrapper';
                                table.parentNode.insertBefore(wrapper, table);
                                wrapper.appendChild(table);
                            }
                        });

                        var sortableTables = document.querySelectorAll('table.wikitable.sortable');
                        sortableTables.forEach(function(table) {
                            if (!table.querySelector('thead')) {
                                var thead = document.createElement('thead');
                                if (table.rows.length > 0) {
                                    thead.appendChild(table.rows[0]);
                                }
                                table.insertBefore(thead, table.firstChild);
                            }
                            // The custom 'numeric-comma' parser will be applied automatically.
                            new Tablesort(table);
                        });
                        console.log("Tablesort.js initialized on " + sortableTables.length + " table(s).");
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun readAsset(assetPath: String): String {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PageHtmlBuilder", "Failed to read asset: $assetPath", e)
            ""
        }
    }
}
