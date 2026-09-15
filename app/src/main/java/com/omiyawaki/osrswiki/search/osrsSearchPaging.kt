package com.omiyawaki.osrswiki.search

/**
 * MediaWiki `prop=pageimages` on `generator=search` omits thumbnails for some
 * ranked hits when the generator page is large. Live wiki check 2026-09-15
 * for `gsrsearch=dragon* scimitar*` / `pithumbsize=240`:
 * - `gsrlimit=20` (iOS `SearchViewModel.searchLimit`): **Rune dragon** has
 *   `https://oldschool.runescape.wiki/images/thumb/Rune_dragon.png/240px-...`
 * - `gsrlimit=60` (Paging 3 default `initialLoadSize = pageSize * 3`): the same
 *   title is returned without `thumbnail`. `pilimit=max` does not restore it.
 * Direct `titles=Rune dragon` still returns the PNG. Android was not dropping a
 * parsed URL; the first typeahead request asked for more hits than pageimages
 * would annotate.
 */
internal const val OSRS_SEARCH_PAGEIMAGES_SAFE_GENERATOR_LIMIT = 20

internal fun osrsSearchGeneratorLimit(requested: Int): Int =
    requested.coerceAtMost(OSRS_SEARCH_PAGEIMAGES_SAFE_GENERATOR_LIMIT)
