package com.omiyawaki.osrswiki.undergroundmaps.model

/**
 * One raster in the visible realm composition, ordered from bottom to top.
 *
 * Canonical realms that publish plane 0 use it as the persistent underlay for every selected
 * upper plane. A small set of canonical realms intentionally publishes only upper-numbered
 * planes; those retain their selected canonical asset without manufacturing a plane-0 resource.
 */
data class osrsRasterCompositionLayer(
    val asset: osrsRealmAsset,
    val opacity: Float,
    val selected: Boolean
) {
    val plane: Int
        get() = asset.plane
}

data class osrsRasterComposition(
    val realmId: String,
    val selectedPlane: Int,
    val layersBottomToTop: List<osrsRasterCompositionLayer>,
    val canonicalPlaneZeroAvailable: Boolean
) {
    init {
        require(layersBottomToTop.isNotEmpty()) { "Raster composition must contain a layer" }
        require(layersBottomToTop.count { it.selected } == 1) {
            "Raster composition must contain exactly one selected layer"
        }
        require(layersBottomToTop.last().selected) {
            "Selected raster must be the top composition layer"
        }
        require(layersBottomToTop.map { it.plane }.distinct().size == layersBottomToTop.size) {
            "Raster composition planes must be unique"
        }
    }
}

fun osrsRasterCompositionFor(
    realm: osrsRealmRecord,
    selectedPlane: Int
): osrsRasterComposition {
    val selectedAsset = requireNotNull(realm.assetForPlane(selectedPlane)) {
        "Realm ${realm.id} does not publish plane $selectedPlane"
    }
    val planeZero = realm.assetForPlane(OSRS_BASE_RASTER_PLANE)
    val layers = when {
        selectedPlane == OSRS_BASE_RASTER_PLANE -> listOf(
            osrsRasterCompositionLayer(
                asset = selectedAsset,
                opacity = OSRS_SELECTED_RASTER_OPACITY,
                selected = true
            )
        )
        planeZero != null -> listOf(
            osrsRasterCompositionLayer(
                asset = planeZero,
                opacity = OSRS_UPPER_FLOOR_BASE_OPACITY,
                selected = false
            ),
            osrsRasterCompositionLayer(
                asset = selectedAsset,
                opacity = OSRS_SELECTED_RASTER_OPACITY,
                selected = true
            )
        )
        else -> listOf(
            osrsRasterCompositionLayer(
                asset = selectedAsset,
                opacity = OSRS_SELECTED_RASTER_OPACITY,
                selected = true
            )
        )
    }
    return osrsRasterComposition(
        realmId = realm.id,
        selectedPlane = selectedPlane,
        layersBottomToTop = layers,
        canonicalPlaneZeroAvailable = planeZero != null
    )
}

data class osrsRasterResourceIdentity(
    val realmId: String,
    val plane: Int,
    val assetSha256: String,
    val sourceId: String,
    val layerId: String,
    val opacity: Float,
    val selected: Boolean
)

fun osrsRasterResourceIdentity(
    styleGeneration: Int,
    realmId: String,
    layer: osrsRasterCompositionLayer
): osrsRasterResourceIdentity {
    require(styleGeneration > 0) { "Style generation must be positive" }
    val realmToken = realmId
        .map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') {
                character
            } else {
                '-'
            }
        }
        .joinToString(separator = "")
        .take(OSRS_RASTER_ID_REALM_TOKEN_LIMIT)
        .ifBlank { "realm" }
    val shaToken = layer.asset.mbtilesSha256.take(OSRS_RASTER_ID_SHA_TOKEN_LENGTH)
    val resourceToken =
        "$styleGeneration-$realmToken-${layer.asset.plane}-$shaToken"
    return osrsRasterResourceIdentity(
        realmId = realmId,
        plane = layer.asset.plane,
        assetSha256 = layer.asset.mbtilesSha256,
        sourceId = "osrs-realm-source-$resourceToken",
        layerId = "osrs-realm-layer-$resourceToken",
        opacity = layer.opacity,
        selected = layer.selected
    )
}

data class osrsRasterCompositionTransition(
    val desiredBottomToTop: List<osrsRasterResourceIdentity>,
    val reused: List<osrsRasterResourceIdentity>,
    val additionsBottomToTop: List<osrsRasterResourceIdentity>,
    val obsoleteLayersTopToBottom: List<osrsRasterResourceIdentity>,
    val obsoleteSources: List<osrsRasterResourceIdentity>
) {
    init {
        require(desiredBottomToTop.isNotEmpty()) {
            "Raster transition must retain at least one desired resource"
        }
    }

    /**
     * The installer stages every desired asset first, prepares additions next, commits desired
     * ordering/opacities, and only then consumes these obsolete lists.
     */
    val replacementPreparedBeforeRemoval: Boolean
        get() = obsoleteLayersTopToBottom.all { obsolete ->
            desiredBottomToTop.none { it.layerId == obsolete.layerId }
        } && obsoleteSources.all { obsolete ->
            desiredBottomToTop.none { it.sourceId == obsolete.sourceId }
        }
}

fun osrsRasterCompositionTransition(
    previousBottomToTop: List<osrsRasterResourceIdentity>,
    desiredBottomToTop: List<osrsRasterResourceIdentity>
): osrsRasterCompositionTransition {
    val desiredLayerIds = desiredBottomToTop.mapTo(mutableSetOf()) { it.layerId }
    val desiredSourceIds = desiredBottomToTop.mapTo(mutableSetOf()) { it.sourceId }
    val previousByLayerId = previousBottomToTop.associateBy { it.layerId }
    return osrsRasterCompositionTransition(
        desiredBottomToTop = desiredBottomToTop,
        reused = desiredBottomToTop.filter { it.layerId in previousByLayerId },
        additionsBottomToTop = desiredBottomToTop.filterNot { it.layerId in previousByLayerId },
        obsoleteLayersTopToBottom = previousBottomToTop
            .asReversed()
            .filterNot { it.layerId in desiredLayerIds },
        obsoleteSources = previousBottomToTop.filterNot { it.sourceId in desiredSourceIds }
    )
}

const val OSRS_BASE_RASTER_PLANE = 0
const val OSRS_SELECTED_RASTER_OPACITY = 1.0f
const val OSRS_UPPER_FLOOR_BASE_OPACITY = 0.5f

private const val OSRS_RASTER_ID_REALM_TOKEN_LIMIT = 48
private const val OSRS_RASTER_ID_SHA_TOKEN_LENGTH = 16
