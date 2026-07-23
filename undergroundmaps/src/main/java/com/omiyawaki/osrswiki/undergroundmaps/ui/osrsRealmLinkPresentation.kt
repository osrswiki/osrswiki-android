package com.omiyawaki.osrswiki.undergroundmaps.ui

import com.omiyawaki.osrswiki.undergroundmaps.model.normalizedSearchText
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmEndpointDestination
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmEndpointMapper
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLink
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkPosition
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkSide
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord
import com.omiyawaki.osrswiki.undergroundmaps.model.rasterProjectionOrNull

sealed interface osrsRealmLinksRow {
    val link: osrsRealmLink
    val visiblePrimary: String
    val visibleSecondary: String
    val accessibilityLabel: String
    val actionable: Boolean

    fun matches(query: String): Boolean
}

data class osrsRealmLinkRow(
    override val link: osrsRealmLink,
    val side: osrsRealmLinkSide,
    val targetRealm: osrsRealmRecord,
    val targetPosition: osrsRealmLinkPosition,
    val sourcePosition: osrsRealmLinkPosition,
    val destination: osrsRealmEndpointDestination,
    override val visiblePrimary: String,
    override val visibleSecondary: String,
    override val accessibilityLabel: String,
    private val normalizedSearchValue: String
) : osrsRealmLinksRow {
    override val actionable: Boolean = true

    override fun matches(query: String): Boolean {
        val terms = normalizedSearchText(query).split(' ').filter(String::isNotBlank)
        return terms.all(normalizedSearchValue::contains)
    }
}

data class osrsRealmUnavailableLinkRow(
    override val link: osrsRealmLink,
    override val visiblePrimary: String,
    override val visibleSecondary: String,
    override val accessibilityLabel: String,
    private val normalizedSearchValue: String
) : osrsRealmLinksRow {
    override val actionable: Boolean = false

    override fun matches(query: String): Boolean {
        val terms = normalizedSearchText(query).split(' ').filter(String::isNotBlank)
        return terms.all(normalizedSearchValue::contains)
    }
}

/** Searchable, deterministic presentation of all actionable links on one realm. */
class osrsRealmLinkCatalog(
    val currentRealm: osrsRealmRecord,
    catalog: osrsRealmCatalog,
    realmPresentations: osrsRealmPresentationCatalog
) {
    private val projection = requireNotNull(catalog.manifest.rasterProjectionOrNull()) {
        "Available links require pinned raster projection metadata"
    }
    private val endpointMapper = osrsRealmEndpointMapper(projection)

    val availableRows: List<osrsRealmLinkRow> = currentRealm.links
        .asSequence()
        .filter { it.authoritative && it.availability == "available" }
        .flatMap { link -> link.endpointSidesFor(currentRealm.id).asSequence() }
        .map { side ->
            val link = side.link
            val targetRealmId = requireNotNull(side.targetRealmId) {
                "Link side ${side.key} has no target realm"
            }
            val targetRealm = requireNotNull(catalog.byId[targetRealmId]) {
                "Link side ${side.key} targets unpublished realm $targetRealmId"
            }
            val targetPosition = side.targetPosition
            val sourcePosition = side.sourcePosition
            val traversal = side.traversalDirection
            val destination = requireNotNull(endpointMapper.map(targetRealm, targetPosition)) {
                "Link side ${side.key} endpoint cannot be mapped into $targetRealmId"
            }
            val targetPresentation = realmPresentations[targetRealm]
            val directionLabel = when (traversal) {
                osrsRealmLinkTraversalDirection.FORWARD -> "From → to"
                osrsRealmLinkTraversalDirection.REVERSE -> "To → from"
            }
            val primary = "${targetPresentation.visibleName} • floor ${targetPosition.plane}"
            val layoutContext = if (destination.matchingLayoutCount > 1) {
                " • exact copy 1 of ${destination.matchingLayoutCount}"
            } else {
                ""
            }
            val secondary = "$directionLabel • endpoint ${targetPosition.x}, ${targetPosition.y}" +
                "$layoutContext • ${link.id}"
            val directionDescription = when (traversal) {
                osrsRealmLinkTraversalDirection.FORWARD -> "forward relative to manifest from and to endpoints"
                osrsRealmLinkTraversalDirection.REVERSE -> "reverse relative to manifest from and to endpoints"
            }
            val accessibility = buildString {
                append("Open authoritative link ")
                append(link.id)
                append(" to ")
                append(targetPresentation.accessibilityName)
                append(", floor ")
                append(targetPosition.plane)
                append(", endpoint x ")
                append(targetPosition.x)
                append(" y ")
                append(targetPosition.y)
                append("; from x ")
                append(sourcePosition.x)
                append(" y ")
                append(sourcePosition.y)
                append("; direction ")
                append(directionDescription)
                append("; manifest direction ")
                append(link.direction.replace('_', ' '))
                if (destination.matchingLayoutCount > 1) {
                    append("; exact layout copy 1 of ")
                    append(destination.matchingLayoutCount)
                }
            }
            val searchValue = normalizedSearchText(
                listOf(
                    primary,
                    secondary,
                    accessibility,
                    targetRealm.id,
                    link.direction,
                    side.key
                ).joinToString(" ")
            )
            osrsRealmLinkRow(
                link = link,
                side = side,
                targetRealm = targetRealm,
                targetPosition = targetPosition,
                sourcePosition = sourcePosition,
                destination = destination,
                visiblePrimary = primary,
                visibleSecondary = secondary,
                accessibilityLabel = accessibility,
                normalizedSearchValue = searchValue
            )
        }
        .sortedWith(
            compareBy<osrsRealmLinkRow>(
                { normalizedSearchText(it.visiblePrimary) },
                { it.link.id },
                { it.side.traversalDirection.name }
            )
        )
        .toList()

    val unavailableRows: List<osrsRealmUnavailableLinkRow> = currentRealm.links
        .asSequence()
        .filterNot { it.authoritative && it.availability == "available" }
        .map { link ->
            val reasons = link.unavailableReasons
                .map { it.replace('_', ' ') }
                .ifEmpty { listOf("reason not published") }
            val primary = "Unavailable • ${link.id}"
            val secondary = buildString {
                append(reasons.joinToString("; "))
                append(" • ")
                append(link.fromPosition.x)
                append(", ")
                append(link.fromPosition.y)
                append(" → ")
                append(link.toPosition.x)
                append(", ")
                append(link.toPosition.y)
            }
            val accessibility = buildString {
                append("Unavailable link ")
                append(link.id)
                append(". From floor ")
                append(link.fromPosition.plane)
                append(", x ")
                append(link.fromPosition.x)
                append(" y ")
                append(link.fromPosition.y)
                append("; to floor ")
                append(link.toPosition.plane)
                append(", x ")
                append(link.toPosition.x)
                append(" y ")
                append(link.toPosition.y)
                append(". Reason: ")
                append(reasons.joinToString("; "))
                append(". Not selectable")
            }
            osrsRealmUnavailableLinkRow(
                link = link,
                visiblePrimary = primary,
                visibleSecondary = secondary,
                accessibilityLabel = accessibility,
                normalizedSearchValue = normalizedSearchText(
                    listOf(primary, secondary, accessibility, link.direction).joinToString(" ")
                )
            )
        }
        .sortedBy { it.link.id }
        .toList()

    val unavailableCount: Int = unavailableRows.size
    val allRows: List<osrsRealmLinksRow> = availableRows + unavailableRows

    init {
        require(availableRows.map { it.side.key }.distinct().size == availableRows.size) {
            "Available link-side IDs must be unique within ${currentRealm.id}"
        }
        require(availableRows.map { it.visiblePrimary to it.visibleSecondary }.distinct().size == availableRows.size) {
            "Available links must have unique visible presentation"
        }
        require(availableRows.map { it.accessibilityLabel }.distinct().size == availableRows.size) {
            "Available links must have unique accessibility presentation"
        }
        require(unavailableRows.map { it.link.id }.distinct().size == unavailableRows.size) {
            "Unavailable link IDs must be unique within ${currentRealm.id}"
        }
        require(unavailableRows.all { it.link.unavailableReasons.isNotEmpty() }) {
            "Unavailable links must publish a recorded reason"
        }
    }

    fun filter(query: String): List<osrsRealmLinksRow> =
        if (query.isBlank()) allRows else allRows.filter { it.matches(query) }
}

data class osrsRealmLinkCatalogLookup(
    val catalog: osrsRealmLinkCatalog,
    val cacheHit: Boolean
)

/** Main-thread cache for immutable link presentation derived from one pinned realm manifest. */
class osrsRealmLinkCatalogCache(
    private val realmCatalog: osrsRealmCatalog,
    private val realmPresentations: osrsRealmPresentationCatalog
) {
    private val catalogsByRealmId = mutableMapOf<String, osrsRealmLinkCatalog>()

    fun get(realm: osrsRealmRecord): osrsRealmLinkCatalogLookup {
        require(realmCatalog.byId[realm.id] == realm) {
            "Realm ${realm.id} does not belong to this pinned catalog"
        }
        catalogsByRealmId[realm.id]?.let { cached ->
            return osrsRealmLinkCatalogLookup(cached, cacheHit = true)
        }
        val created = osrsRealmLinkCatalog(realm, realmCatalog, realmPresentations)
        catalogsByRealmId[realm.id] = created
        return osrsRealmLinkCatalogLookup(created, cacheHit = false)
    }

    val size: Int
        get() = catalogsByRealmId.size
}
