package com.omiyawaki.osrswiki.page

/** A small WHATWG-style srcset tokenizer that keeps commas inside data URLs intact. */
internal object SrcsetParser {
    internal data class Candidate(val url: String, val descriptor: String)

    fun urls(srcset: String): List<String> = parse(srcset).map(Candidate::url)

    fun rewriteUrls(srcset: String, transform: (String) -> String): String = parse(srcset)
        .joinToString(", ") { candidate ->
            val rewritten = transform(candidate.url)
            if (candidate.descriptor.isBlank()) rewritten else "$rewritten ${candidate.descriptor}"
        }

    private fun parse(srcset: String): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        var index = 0
        while (index < srcset.length) {
            while (index < srcset.length && (srcset[index].isWhitespace() || srcset[index] == ',')) {
                index += 1
            }
            if (index >= srcset.length) break

            val quoted = srcset[index] == '\'' || srcset[index] == '"'
            val quote = srcset[index].takeIf { quoted }
            if (quoted) index += 1
            val urlStart = index
            val isDataUrl = srcset.regionMatches(index, "data:", 0, 5, ignoreCase = true)
            while (index < srcset.length) {
                val character = srcset[index]
                if ((quote != null && character == quote) ||
                    (quote == null && character.isWhitespace()) ||
                    (quote == null && !isDataUrl && character == ',')
                ) {
                    break
                }
                index += 1
            }
            val url = srcset.substring(urlStart, index).trim()
            if (quote != null && index < srcset.length && srcset[index] == quote) index += 1

            while (index < srcset.length && srcset[index].isWhitespace()) index += 1
            val descriptorStart = index
            while (index < srcset.length && srcset[index] != ',') index += 1
            val descriptor = srcset.substring(descriptorStart, index).trim()
            if (index < srcset.length && srcset[index] == ',') index += 1
            if (url.isNotEmpty()) candidates += Candidate(url, descriptor)
        }
        return candidates
    }
}
