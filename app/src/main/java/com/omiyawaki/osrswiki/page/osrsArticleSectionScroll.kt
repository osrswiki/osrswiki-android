package com.omiyawaki.osrswiki.page

/**
 * Scrolls the article document to a wiki heading id. MediaWiki heading ids can
 * start with digits and contain apostrophes; never concatenate them into a
 * raw JS string and never assume the node exists.
 */
object osrsArticleSectionScroll {
    fun javaScript(anchor: String): String {
        val literal = jsStringLiteral(anchor)
        return """
            (function() {
                try {
                var id = $literal;
                var el = document.getElementById(id);
                if (!el && window.CSS && CSS.escape) {
                    try { el = document.querySelector('#' + CSS.escape(id)); } catch (e) {}
                }
                if (!el) {
                    var wanted = id.replace(/_/g, ' ').trim().toLowerCase();
                    var nodes = document.querySelectorAll('h1, h2, h3, h4, .mw-headline, caption');
                    for (var i = 0; i < nodes.length; i++) {
                        var node = nodes[i];
                        var clone = node.cloneNode(true);
                        var hideSel = document.body.classList.contains('floornumber-setting-us')
                            ? '.floornumber-gb, .floornumber-help'
                            : '.floornumber-us, .floornumber-help';
                        var marks = clone.querySelectorAll(hideSel);
                        for (var m = 0; m < marks.length; m++) marks[m].remove();
                        var text = (clone.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
                        if (text === wanted || (node.id || '').toLowerCase() === id.toLowerCase()) {
                            el = node;
                            break;
                        }
                    }
                }
                if (!el) return null;
                var headerOffset = Math.max(8, Math.min(96, Math.round(window.innerHeight * 0.08)));
                el.style.scrollMarginTop = headerOffset + 'px';
                return el.getBoundingClientRect().top - headerOffset;
                } catch (err) {
                    return null;
                }
            })();
        """.trimIndent()
    }

    internal fun jsStringLiteral(value: String): String {
        val escaped = buildString(value.length + 2) {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\'' -> append("\\'")
                    else -> append(ch)
                }
            }
            append('"')
        }
        return escaped
    }
}
