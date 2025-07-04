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
                    /*
                     * The CSS rules are intentionally inverted.
                     * Tablesort.js has a bug where it reports the inverse sort state
                     * in the 'aria-sort' attribute. These rules correct for that.
                     */
                    th[aria-sort="ascending"]::after {
                        /* Down arrow for ascending state (which is actually descending data) */
                        border-left: 5px solid transparent;
                        border-right: 5px solid transparent;
                        border-top: 5px solid var(--link-color);
                    }
                    th[aria-sort="descending"]::after {
                        /* Up arrow for descending state (which is actually ascending data) */
                        border-left: 5px solid transparent;
                        border-right: 5px solid transparent;
                        border-bottom: 5px solid var(--link-color);
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
                    Tablesort.extend('numeric-comma', function(item) {
                        // Test for numbers with commas, allowing for hyphen or Unicode minus sign.
                        // Also trim whitespace which can interfere with the regex.
                        return /^[−-]?[\d,]+(?:\.\d+)?$/.test(item.trim());
                    }, function(a, b) {
                        // Clean the strings, standardize the minus sign, and compare as numbers.
                        var cleanA = a.trim().replace(/,/g, '').replace('−', '-');
                        var cleanB = b.trim().replace(/,/g, '').replace('−', '-');

                        var numA = parseFloat(cleanA);
                        var numB = parseFloat(cleanB);

                        numA = isNaN(numA) ? 0 : numA;
                        numB = isNaN(numB) ? 0 : numB;

                        return numA - numB;
                    });

                    // Extend Tablesort.js with a custom parser for intensity values.
                    Tablesort.extend('intensity', function(item) {
                        // Test if the cell content is one of the intensity values (case-insensitive).
                        return /^(low|medium|moderate|high)$/i.test(item.trim());
                    }, function(a, b) {
                        // Map intensity strings to numerical values for sorting.
                        var intensityMap = {
                            'low': 0,
                            'medium': 1,
                            'moderate': 1,
                            'high': 2
                        };

                        var valueA = intensityMap[a.toLowerCase().trim()];
                        var valueB = intensityMap[b.toLowerCase().trim()];

                        valueA = (valueA === undefined) ? -1 : valueA;
                        valueB = (valueB === undefined) ? -1 : valueB;

                        return valueA - valueB;
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
                            // The custom parsers will be applied automatically by Tablesort.
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
