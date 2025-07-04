package com.omiyawaki.osrswiki.bridge

import android.util.Log
import com.omiyawaki.osrswiki.settings.Prefs

/**
 * Creates JavaScript snippets to be injected into the WebView.
 */
object JavaScriptActionHandler {
    private const val TAG = "JActionHandler"

    private const val JS_TOGGLE_TABLES_SCRIPT = """
    document.addEventListener('DOMContentLoaded', function() {
        (function() {
            'use strict';
            if (window.osrsWikiTablesCollapsed) {
                console.log("JActionHandler: Script already ran. Exiting.");
                return;
            }
            window.osrsWikiTablesCollapsed = true;
            console.log("JActionHandler: Running table collapse script.");

            function findAncestor(el, sel) {
                while ((el = el.parentElement) && !el.matches(sel));
                return el;
            }

            function toggleTable(event) {
                event.preventDefault();
                // Corrected selector
                const table = findAncestor(event.target, 'table.wikitable');
                if (!table) { return; }

                const isCollapsed = table.classList.toggle('is-collapsed');
                const toggleElement = table.querySelector('caption .collapsible-toggle');
                if (toggleElement) {
                    toggleElement.innerText = isCollapsed ? '[show]' : '[hide]';
                }

                for (const body of table.tBodies) {
                    // We start at index 1 to skip the first row in the table body,
                    // which often contains a sub-header or other important info.
                    for (let i = 0; i < body.rows.length; i++) {
                         // A better approach is to not hide the header row, instead of a specific index
                        if (body.rows[i].getElementsByTagName('th').length > 0) {
                            continue;
                        }
                        body.rows[i].style.display = isCollapsed ? 'none' : '';
                    }
                }
            }

            // Corrected selector
            const tables = document.querySelectorAll('table.wikitable');
            console.log("JActionHandler: Found " + tables.length + " wikitable tables.");

            tables.forEach(function(table) {
                // To be collapsible, a table must have a caption
                const caption = table.querySelector('caption');
                if (caption && !caption.querySelector('.collapsible-toggle-wrapper')) {
                    const originalText = document.createElement('span');
                    originalText.innerText = caption.innerText;

                    const toggleWrapper = document.createElement('span');
                    toggleWrapper.className = 'collapsible-toggle-wrapper';
                    toggleWrapper.style.display = 'flex';
                    toggleWrapper.style.justifyContent = 'space-between';
                    toggleWrapper.style.width = '100%';

                    const toggle = document.createElement('span');
                    toggle.className = 'collapsible-toggle';
                    toggle.innerText = '[show]';
                    toggle.style.cursor = 'pointer';
                    toggle.style.color = 'var(--link-color, #0645ad)';
                    toggle.style.userSelect = 'none';

                    caption.innerText = '';
                    toggleWrapper.appendChild(originalText);
                    toggleWrapper.appendChild(toggle);
                    caption.appendChild(toggleWrapper);

                    table.classList.add('is-collapsed');
                    for (const body of table.tBodies) {
                         for (let i = 0; i < body.rows.length; i++) {
                            if (body.rows[i].getElementsByTagName('th').length > 0) {
                                continue;
                            }
                            body.rows[i].style.display = 'none';
                        }
                    }
                    caption.addEventListener('click', toggleTable);
                }
            });
            console.log("JActionHandler: Finished processing tables.");
        })();
    });
    """

    fun getToggleTablesScript(): String {
        val isEnabled = Prefs.isCollapseTablesEnabled
        Log.d(TAG, "isCollapseTablesEnabled preference is: $isEnabled")
        return if (isEnabled) {
            Log.d(TAG, "Returning table collapse script.")
            JS_TOGGLE_TABLES_SCRIPT
        } else {
            Log.d(TAG, "Returning empty script because preference is disabled.")
            ""
        }
    }
}
