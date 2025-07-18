package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.theme.Theme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

class PageHtmlBuilder(private val context: Context) {

    private val styleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/layout.css",
        "styles/components.css",
        "styles/navbox_styles.css",
        "web/collapsible_tables.css",
        "styles/fixes.css"
    )

    private val wikiContentCss: String by lazy {
        styleSheetAssets.joinToString(separator = "\n") { assetPath ->
            readAsset(assetPath)
        }
    }
    private val tablesortJs: String by lazy { readAsset("js/tablesort.min.js") }
    private val collapsibleContentJs: String by lazy { readAsset("web/collapsible_content.js") }

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        val documentTitle = if (title.isBlank()) "OSRS Wiki" else title
        val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
        val finalBodyContent = titleHeaderHtml + bodyContent
        val themeClass = when (theme) {
            Theme.OSRS_DARK -> "theme-osrs-dark"
            Theme.WIKI_LIGHT -> "theme-wikipedia-light"
            Theme.WIKI_DARK -> "theme-wikipedia-dark"
            Theme.WIKI_BLACK -> "theme-wikipedia-black"
            else -> "" // OSRS Light is the default theme in CSS, no class needed.
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${documentTitle}</title>
                <style>
                    ${wikiContentCss}
                </style>
            </head>
            <body class="$themeClass" style="visibility: hidden;">
                ${finalBodyContent}
                <script>
                    ${tablesortJs}
                </script>
                <script>
                    ${collapsibleContentJs}
                </script>
                <script>
                    // Extend Tablesort.js with a custom parser for comma-separated numbers.
                    Tablesort.extend('numeric-comma', function(item) {
                        return /^[−-]?[\d,]+(?:\.\d+)?$/.test(item.trim());
                    }, function(a, b) {
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
                        return /^(low|medium|moderate|high)$/i.test(item.trim());
                    }, function(a, b) {
                        var intensityMap = {
                            'low': 0, 'medium': 1, 'moderate': 1, 'high': 2
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
                        // Find the infobox and the original location table.
                        var infobox = document.querySelector('.infobox');
                        var locationTable = document.querySelector('table.relative-location');

                        // If both elements exist, deconstruct the location table and merge it into the infobox.
                        if (infobox && locationTable) {
                            var infoboxBody = infobox.querySelector('tbody');
                            var locationHeader = locationTable.querySelector('.relative-location-header');

                            // Ensure all necessary parts exist before proceeding.
                            if (infoboxBody && locationHeader) {
                                // Dynamically determine the correct colspan for the infobox table.
                                let maxCols = 0;
                                const rows = infobox.querySelectorAll('tbody > tr');
                                rows.forEach(function(row) {
                                    let currentColCount = 0;
                                    // Sum the colspan property of each cell in the current row.
                                    for (const cell of row.cells) {
                                        currentColCount += cell.colSpan;
                                    }
                                    if (currentColCount > maxCols) {
                                        maxCols = currentColCount;
                                    }
                                });
                                // If no columns were found (e.g., empty table), default to a safe value.
                                const colspan = maxCols > 0 ? maxCols : 2;
                                console.log("Determined infobox colspan to be: " + colspan);

                                // 1. Create a new subheader row for the infobox.
                                var headerRow = document.createElement('tr');
                                var headerCell = document.createElement('td');
                                headerCell.className = 'infobox-header';
                                headerCell.colSpan = colspan; // Use the dynamically calculated value.
                                headerCell.innerHTML = locationHeader.innerHTML;
                                headerRow.appendChild(headerCell);

                                // 2. Create a new content row for the infobox to hold the map grid.
                                var contentRow = document.createElement('tr');
                                var contentCell = document.createElement('td');
                                contentCell.colSpan = colspan; // Use the dynamically calculated value.

                                // 3. Move the location table itself into the new cell.
                                contentCell.appendChild(locationTable);
                                contentRow.appendChild(contentCell);

                                // 4. Clean up the moved location table.
                                locationTable.removeAttribute('style'); // Remove inline styles.
                                locationHeader.parentElement.remove(); // Remove its original header row.

                                // 5. Append the new rows to the infobox.
                                infoboxBody.appendChild(headerRow);
                                infoboxBody.appendChild(contentRow);

                                console.log("Deconstructed and merged location table into infobox.");
                            }
                        }

                        // Initialize sorting on any sortable wikitables.
                        var sortableTables = document.querySelectorAll('table.wikitable.sortable');
                        sortableTables.forEach(function(table) {
                            if (!table.querySelector('thead')) {
                                var thead = document.createElement('thead');
                                if (table.rows.length > 0) {
                                    thead.appendChild(table.rows[0]);
                                }
                                table.insertBefore(thead, table.firstChild);
                            }
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
        } catch (e: IOException) {
            android.util.Log.e("PageHtmlBuilder", "Failed to read asset: $assetPath", e)
            ""
        }
    }
}
