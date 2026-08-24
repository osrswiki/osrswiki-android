package com.omiyawaki.osrswiki.page

/** A small WHATWG-style srcset tokenizer that keeps commas inside data URLs intact. */
internal object SrcsetParser {
    internal data class Candidate(val url: String, val descriptor: String)

    fun urls(srcset: String): List<String> = parse(srcset).map(Candidate::url)

    /**
     * One density URL for a slot: the smallest candidate whose resolution meets
     * [devicePixelRatio] (x descriptors) or [widthPx] * DPR (w descriptors).
     * Does not return both a 1x (140px) and 2x (280px) URL for the same img.
     */
    fun choose(
        src: String?,
        srcset: String?,
        widthPx: Int? = null,
        devicePixelRatio: Float = 2f
    ): String? {
        val srcUrl = src?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("data:", ignoreCase = true) }
        val parsed = parse(srcset.orEmpty())
        val candidates = linkedMapOf<String, Candidate>()
        if (srcUrl != null) {
            candidates[srcUrl] = Candidate(srcUrl, "1x")
        }
        parsed.forEach { candidate ->
            if (candidate.url.isNotBlank() && !candidate.url.startsWith("data:", ignoreCase = true)) {
                candidates[candidate.url] = candidate
            }
        }
        val list = candidates.values.toList()
        if (list.isEmpty()) {
            return null
        }
        if (list.size == 1) {
            return list[0].url
        }
        val dpr = devicePixelRatio.coerceAtLeast(1f)
        val xCandidates = list.mapNotNull { candidate ->
            xDensity(candidate)?.let { density -> candidate to density }
        }
        if (xCandidates.isNotEmpty()) {
            val meeting = xCandidates.filter { it.second >= dpr }
            val pick = if (meeting.isNotEmpty()) {
                meeting.minBy { it.second }
            } else {
                xCandidates.maxBy { it.second }
            }
            return pick.first.url
        }
        val layoutWidth = widthPx?.takeIf { it > 0 }
        val wCandidates = list.mapNotNull { candidate ->
            wDescriptor(candidate)?.let { width -> candidate to width }
        }
        if (wCandidates.isNotEmpty() && layoutWidth != null) {
            val needed = layoutWidth * dpr
            val meeting = wCandidates.filter { it.second >= needed }
            val pick = if (meeting.isNotEmpty()) {
                meeting.minBy { it.second }
            } else {
                wCandidates.maxBy { it.second }
            }
            return pick.first.url
        }
        return list.last().url
    }

    private fun xDensity(candidate: Candidate): Float? {
        val descriptor = candidate.descriptor.trim()
        if (descriptor.isEmpty()) {
            return 1f
        }
        if (!descriptor.endsWith("x", ignoreCase = true)) {
            return null
        }
        return descriptor.dropLast(1).toFloatOrNull()
    }

    private fun wDescriptor(candidate: Candidate): Float? {
        val descriptor = candidate.descriptor.trim()
        if (!descriptor.endsWith("w", ignoreCase = true)) {
            return null
        }
        return descriptor.dropLast(1).toFloatOrNull()
    }

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
