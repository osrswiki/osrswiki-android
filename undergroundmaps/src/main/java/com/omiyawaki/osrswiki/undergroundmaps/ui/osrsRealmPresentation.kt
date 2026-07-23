package com.omiyawaki.osrswiki.undergroundmaps.ui

import com.omiyawaki.osrswiki.undergroundmaps.model.normalizedSearchText
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord

/**
 * User-facing identity for a published realm.
 *
 * Canonical names remain the searchable game/Wiki names. A qualifier is added only when the
 * manifest publishes more than one realm with the same normalized canonical name. Qualifiers are
 * derived exclusively from structured manifest identity, never from inferred semantic names.
 */
data class osrsRealmPresentationLabel(
    val realmId: String,
    val canonicalName: String,
    val qualifier: String?
) {
    val visibleName: String = qualifier?.let { "$canonicalName — $it" } ?: canonicalName
    val accessibilityName: String = qualifier?.let { "$canonicalName, $it" } ?: canonicalName

    fun selectorAccessibilityLabel(selected: Boolean): String = if (selected) {
        "Selected map, $accessibilityName"
    } else {
        "Select map $accessibilityName"
    }
}

/**
 * Computes presentation labels once from the complete manifest so filtering cannot remove the
 * context that makes duplicate canonical names distinguishable.
 */
class osrsRealmPresentationCatalog(realms: Iterable<osrsRealmRecord>) {
    private val sourceRealms = realms.toList()

    init {
        require(sourceRealms.map { it.id }.distinct().size == sourceRealms.size) {
            "Realm presentation IDs must be unique"
        }
    }

    private val labelsByRealmId: Map<String, osrsRealmPresentationLabel> = buildMap {
        sourceRealms
            .groupBy { normalizedSearchText(it.canonicalName) }
            .values
            .forEach { sameNameRealms ->
                sameNameRealms.forEach { realm ->
                    put(
                        realm.id,
                        osrsRealmPresentationLabel(
                            realmId = realm.id,
                            canonicalName = realm.canonicalName,
                            qualifier = if (sameNameRealms.size > 1) {
                                realm.osrsStructuredDuplicateQualifier(sameNameRealms)
                            } else {
                                null
                            }
                        )
                    )
                }
            }
    }

    val orderedLabels: List<osrsRealmPresentationLabel> = sourceRealms
        .sortedWith(compareBy<osrsRealmRecord>({ normalizedSearchText(it.canonicalName) }, { it.id }))
        .map { labelsByRealmId.getValue(it.id) }

    private val normalizedSearchByRealmId: Map<String, String> = sourceRealms.associate { realm ->
        val presentation = labelsByRealmId.getValue(realm.id)
        realm.id to normalizedSearchText(
            listOf(
                realm.selectorText(),
                presentation.visibleName,
                presentation.accessibilityName
            ).joinToString(" ")
        )
    }

    init {
        sourceRealms
            .groupBy { normalizedSearchText(it.canonicalName) }
            .values
            .filter { it.size > 1 }
            .forEach { sameNameRealms ->
                val labels = sameNameRealms.map { labelsByRealmId.getValue(it.id) }
                require(labels.map { it.visibleName }.distinct().size == labels.size) {
                    "Duplicate realm names must have unique visible labels"
                }
                require(labels.map { it.accessibilityName }.distinct().size == labels.size) {
                    "Duplicate realm names must have unique accessibility labels"
                }
            }
    }

    operator fun get(realm: osrsRealmRecord): osrsRealmPresentationLabel = get(realm.id)

    operator fun get(realmId: String): osrsRealmPresentationLabel =
        requireNotNull(labelsByRealmId[realmId]) { "Unknown realm presentation ID $realmId" }

    fun matches(realm: osrsRealmRecord, query: String): Boolean {
        return matches(realm, normalizedTerms(query))
    }

    internal fun normalizedTerms(query: String): List<String> = normalizedSearchText(query)
        .split(' ')
        .filter(String::isNotBlank)

    internal fun matches(realm: osrsRealmRecord, normalizedTerms: List<String>): Boolean {
        val searchable = requireNotNull(normalizedSearchByRealmId[realm.id]) {
            "Unknown realm presentation ID ${realm.id}"
        }
        return normalizedTerms.all(searchable::contains)
    }
}

private fun osrsRealmRecord.osrsStructuredDuplicateQualifier(
    sameNameRealms: List<osrsRealmRecord>
): String {
    if (mapId != null && sameNameRealms.count { it.mapId == mapId } == 1) {
        return "Map ID $mapId"
    }
    if (nativeFileId != null && sameNameRealms.count { it.nativeFileId == nativeFileId } == 1) {
        return "Cache definition $nativeFileId"
    }
    return "Realm ID $id"
}
