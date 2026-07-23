package com.omiyawaki.osrswiki.undergroundmaps.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale

@Serializable
data class osrsRealmProduct(
    val label: String,
    @SerialName("application_id") val applicationId: String
)

@Serializable
data class osrsRealmAsset(
    val plane: Int,
    @SerialName("mbtiles_path") val mbtilesPath: String,
    @SerialName("mbtiles_sha256") val mbtilesSha256: String,
    @SerialName("mbtiles_bytes") val mbtilesBytes: Long,
    @SerialName("mask_path") val maskPath: String? = null,
    @SerialName("mask_sha256") val maskSha256: String? = null,
    val width: Int,
    val height: Int,
    val nonblank: Boolean,
    @SerialName("tile_size") val tileSize: Int,
    @SerialName("min_zoom") val minZoom: Int,
    @SerialName("max_zoom") val maxZoom: Int,
    @SerialName("tile_count") val tileCount: Int,
    @SerialName("canvas_size") val canvasSize: Int,
    @SerialName("content_pixel_bounds") val contentPixelBounds: List<Int>,
    @SerialName("content_latlon_bounds") val contentLatlonBounds: List<Double>,
    @SerialName("source_bounds") val sourceBounds: JsonElement? = null,
    @SerialName("display_bounds") val displayBounds: JsonElement? = null,
    @SerialName("layout_components") val layoutComponents: List<osrsRealmLayoutComponent> = emptyList()
) {
    val west: Double get() = contentLatlonBounds[0]
    val south: Double get() = contentLatlonBounds[1]
    val east: Double get() = contentLatlonBounds[2]
    val north: Double get() = contentLatlonBounds[3]
}

@Serializable
data class osrsRealmPixelBounds(
    @SerialName("min_x") val minX: Int,
    @SerialName("min_y") val minY: Int,
    @SerialName("max_x") val maxX: Int,
    @SerialName("max_y") val maxY: Int
) {
    val width: Int get() = maxX - minX
    val height: Int get() = maxY - minY

    fun contains(x: Double, y: Double): Boolean =
        x >= minX && x < maxX && y >= minY && y < maxY
}

@Serializable
data class osrsRealmLayoutComponent(
    @SerialName("source_pixel_bounds") val sourcePixelBounds: osrsRealmPixelBounds,
    @SerialName("asset_pixel_bounds") val assetPixelBounds: osrsRealmPixelBounds,
    @SerialName("source_to_display_dx_pixels") val sourceToDisplayDxPixels: Int,
    @SerialName("source_to_display_dy_pixels") val sourceToDisplayDyPixels: Int,
    @SerialName("provenance_codes") val provenanceCodes: List<Int> = emptyList(),
    @SerialName("assigned_source_pixel_count") val assignedSourcePixelCount: Long = 0L
)

@Serializable
data class osrsRealmLinkPosition(
    val plane: Int,
    val x: Int,
    val y: Int
)

@Serializable
data class osrsRealmLink(
    val id: String,
    @SerialName("from_realm_id") val fromRealmId: String? = null,
    @SerialName("to_realm_id") val toRealmId: String? = null,
    @SerialName("from_position") val fromPosition: osrsRealmLinkPosition,
    @SerialName("to_position") val toPosition: osrsRealmLinkPosition,
    val direction: String,
    val availability: String,
    val authoritative: Boolean,
    val confidence: Double,
    val evidence: List<String> = emptyList(),
    @SerialName("unavailable_reasons") val unavailableReasons: List<String> = emptyList()
) {
    fun endpointSidesFor(realmId: String): List<osrsRealmLinkSide> = buildList {
        if (fromRealmId == realmId) {
            add(osrsRealmLinkSide(this@osrsRealmLink, osrsRealmLinkTraversalDirection.FORWARD))
        }
        if (toRealmId == realmId) {
            add(osrsRealmLinkSide(this@osrsRealmLink, osrsRealmLinkTraversalDirection.REVERSE))
        }
    }

    private fun singleEndpointSideFor(realmId: String): osrsRealmLinkSide? =
        endpointSidesFor(realmId).singleOrNull()

    fun targetRealmId(fromRealmId: String): String? =
        singleEndpointSideFor(fromRealmId)?.targetRealmId

    fun targetPlane(fromRealmId: String): Int? =
        singleEndpointSideFor(fromRealmId)?.targetPosition?.plane

    fun targetPosition(fromRealmId: String): osrsRealmLinkPosition? =
        singleEndpointSideFor(fromRealmId)?.targetPosition

    fun sourcePosition(fromRealmId: String): osrsRealmLinkPosition? =
        singleEndpointSideFor(fromRealmId)?.sourcePosition

    fun traversalDirection(fromRealmId: String): osrsRealmLinkTraversalDirection? =
        singleEndpointSideFor(fromRealmId)?.traversalDirection
}

enum class osrsRealmLinkTraversalDirection {
    FORWARD,
    REVERSE
}

/** One navigable endpoint side; identity is the link plus explicit traversal direction. */
data class osrsRealmLinkSide(
    val link: osrsRealmLink,
    val traversalDirection: osrsRealmLinkTraversalDirection
) {
    val key: String
        get() = "${link.id}:${traversalDirection.name.lowercase(Locale.ROOT)}"

    val sourceRealmId: String?
        get() = when (traversalDirection) {
            osrsRealmLinkTraversalDirection.FORWARD -> link.fromRealmId
            osrsRealmLinkTraversalDirection.REVERSE -> link.toRealmId
        }

    val targetRealmId: String?
        get() = when (traversalDirection) {
            osrsRealmLinkTraversalDirection.FORWARD -> link.toRealmId
            osrsRealmLinkTraversalDirection.REVERSE -> link.fromRealmId
        }

    val sourcePosition: osrsRealmLinkPosition
        get() = when (traversalDirection) {
            osrsRealmLinkTraversalDirection.FORWARD -> link.fromPosition
            osrsRealmLinkTraversalDirection.REVERSE -> link.toPosition
        }

    val targetPosition: osrsRealmLinkPosition
        get() = when (traversalDirection) {
            osrsRealmLinkTraversalDirection.FORWARD -> link.toPosition
            osrsRealmLinkTraversalDirection.REVERSE -> link.fromPosition
        }
}

data class osrsRealmRasterProjection(
    val gameMinX: Int,
    val gameMaxY: Int,
    val scale: Int,
    val width: Int,
    val height: Int
) {
    init {
        require(scale > 0) { "Realm raster coordinate scale must be positive" }
        require(width > 0 && height > 0) { "Realm raster dimensions must be positive" }
    }
}

@Serializable
data class osrsRealmRecord(
    val id: String,
    @SerialName("canonical_name") val canonicalName: String,
    val aliases: List<String> = emptyList(),
    val group: String,
    @SerialName("is_surface") val isSurface: Boolean,
    @SerialName("native_file_id") val nativeFileId: Int? = null,
    @SerialName("map_id") val mapId: Int? = null,
    val article: String? = null,
    val center: List<Double>,
    @SerialName("default_plane") val defaultPlane: Int,
    val planes: List<Int>,
    @SerialName("cache_declared_planes") val cacheDeclaredPlanes: List<Int> = emptyList(),
    val components: JsonElement? = null,
    val links: List<osrsRealmLink> = emptyList(),
    @SerialName("source_revisions") val sourceRevisions: JsonElement? = null,
    val confidence: JsonElement? = null,
    val ambiguity: JsonElement? = null,
    @SerialName("accounting_owner_realm_id") val accountingOwnerRealmId: String? = null,
    @SerialName("accounting_pixel_count") val accountingPixelCount: Long? = null,
    val assets: List<osrsRealmAsset>
) {
    fun assetForPlane(plane: Int): osrsRealmAsset? = assets.firstOrNull { it.plane == plane }

    fun selectorText(): String = buildString {
        append(canonicalName)
        aliases.forEach {
            append(' ')
            append(it)
        }
        article?.let {
            append(' ')
            append(it)
        }
        append(' ')
        append(id)
    }
}

@Serializable
data class osrsUndergroundRealmManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    val candidate: String,
    val product: osrsRealmProduct,
    val inputs: JsonElement? = null,
    val accounting: JsonElement? = null,
    val realms: List<osrsRealmRecord>,
    val selector: JsonElement? = null
)

data class osrsRealmCatalog(
    val manifest: osrsUndergroundRealmManifest,
    val byId: Map<String, osrsRealmRecord>,
    val surface: osrsRealmRecord,
    val sections: Map<String, List<osrsRealmRecord>>
) {
    val realmCount: Int get() = manifest.realms.size
    val selectorCount: Int get() = sections.values.sumOf { it.size }
}

class osrsRealmManifestParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }
) {
    fun parse(text: String): osrsRealmCatalog {
        val manifest = json.decodeFromString<osrsUndergroundRealmManifest>(text)
        validate(manifest)

        val byId = manifest.realms.associateBy { it.id }
        val surface = manifest.realms.single { it.isSurface }
        val sections = OSRS_REALM_GROUPS.associateWith { group ->
            manifest.realms
                .asSequence()
                .filter { it.group == group }
                .sortedWith(compareBy<osrsRealmRecord>({ normalizedSearchText(it.canonicalName) }, { it.id }))
                .toList()
        }
        return osrsRealmCatalog(manifest, byId, surface, sections)
    }

    private fun validate(manifest: osrsUndergroundRealmManifest) {
        require(manifest.schemaVersion == OSRS_REALM_SCHEMA_VERSION) {
            "Unsupported realm manifest schema ${manifest.schemaVersion}"
        }
        require(manifest.candidate.isNotBlank()) { "Manifest candidate must not be blank" }
        require(manifest.product.label == OSRS_UNDERGROUND_MAPS_LABEL) {
            "Unexpected product label ${manifest.product.label}"
        }
        require(manifest.product.applicationId == OSRS_UNDERGROUND_MAPS_APPLICATION_ID) {
            "Unexpected application ID ${manifest.product.applicationId}"
        }
        require(manifest.realms.isNotEmpty()) { "Manifest contains no published maps" }
        require(manifest.realms.map { it.id }.distinct().size == manifest.realms.size) {
            "Realm IDs must be unique"
        }
        require(manifest.realms.count { it.isSurface } == 1) {
            "Manifest must contain exactly one true surface"
        }

        manifest.realms.forEach(::validateRealm)
        val realmsById = manifest.realms.associateBy { it.id }
        val realmIds = realmsById.keys
        val allLinks = manifest.realms.flatMap { it.links }
        require(allLinks.groupBy { it.id }.values.all { copies -> copies.distinct().size == 1 }) {
            "Duplicate link IDs must describe the same authoritative record"
        }
        allLinks.distinctBy { it.id }.forEach { link ->
            require(link.id.isNotBlank()) { "Link ID must not be blank" }
            require(link.confidence.isFinite() && link.confidence in 0.0..1.0) {
                "Link ${link.id} has invalid confidence"
            }
            require(link.availability in setOf("available", "unavailable")) {
                "Link ${link.id} has unknown availability ${link.availability}"
            }
            if (link.authoritative || link.availability == "available") {
                require(link.authoritative && link.availability == "available") {
                    "Link ${link.id} has inconsistent authority and availability"
                }
                require(link.fromRealmId in realmIds && link.toRealmId in realmIds) {
                    "Link ${link.id} targets an unpublished realm"
                }
                require(link.fromPosition.plane in realmsById.getValue(link.fromRealmId!!).planes) {
                    "Link ${link.id} starts on an unpublished plane"
                }
                require(link.toPosition.plane in realmsById.getValue(link.toRealmId!!).planes) {
                    "Link ${link.id} ends on an unpublished plane"
                }
            } else {
                require(link.unavailableReasons.isNotEmpty()) {
                    "Unavailable link ${link.id} must publish a recorded reason"
                }
            }
        }

        val availableLinks = allLinks.distinctBy { it.id }.filter {
            it.authoritative && it.availability == "available"
        }
        if (availableLinks.isNotEmpty()) {
            val projection = requireNotNull(manifest.rasterProjectionOrNull()) {
                "Available links require pinned game-to-raster projection metadata"
            }
            val endpointMapper = osrsRealmEndpointMapper(projection)
            availableLinks.forEach { link ->
                val fromRealm = realmsById.getValue(link.fromRealmId!!)
                val toRealm = realmsById.getValue(link.toRealmId!!)
                require(endpointMapper.map(fromRealm, link.fromPosition) != null) {
                    "Link ${link.id} from endpoint cannot be mapped into ${fromRealm.id}"
                }
                require(endpointMapper.map(toRealm, link.toPosition) != null) {
                    "Link ${link.id} to endpoint cannot be mapped into ${toRealm.id}"
                }
            }
        }
    }

    private fun validateRealm(realm: osrsRealmRecord) {
        require(realm.id.isNotBlank()) { "Realm ID must not be blank" }
        require(realm.canonicalName.isNotBlank()) { "Realm ${realm.id} has no canonical name" }
        require(realm.group in OSRS_REALM_GROUPS) { "Realm ${realm.id} has unknown group ${realm.group}" }
        require(realm.isSurface == (realm.group == OSRS_REALM_GROUP_SURFACE)) {
            "Realm ${realm.id} surface flag disagrees with its group"
        }
        require(realm.center.size == 2 && realm.center.all(Double::isFinite)) {
            "Realm ${realm.id} has an invalid game-coordinate center"
        }
        require(realm.planes.isNotEmpty() && realm.planes.distinct().size == realm.planes.size) {
            "Realm ${realm.id} must declare unique planes"
        }
        require(realm.defaultPlane in realm.planes) {
            "Realm ${realm.id} default plane is not published"
        }
        require(realm.assets.map { it.plane }.sorted() == realm.planes.sorted()) {
            "Realm ${realm.id} plane assets do not match its published planes"
        }
        realm.assets.forEach { validateAsset(realm, it) }
    }

    private fun validateAsset(realm: osrsRealmRecord, asset: osrsRealmAsset) {
        require(isSafeRelativeAssetPath(asset.mbtilesPath) && asset.mbtilesPath.endsWith(".mbtiles")) {
            "Realm ${realm.id} has unsafe MBTiles path ${asset.mbtilesPath}"
        }
        require(OSRS_SHA256.matches(asset.mbtilesSha256)) {
            "Realm ${realm.id} has an invalid MBTiles checksum"
        }
        require(asset.mbtilesBytes >= 0L) { "Realm ${realm.id} has a negative asset size" }
        require(asset.width > 0 && asset.height > 0 && asset.canvasSize > 0) {
            "Realm ${realm.id} has invalid local dimensions"
        }
        require(asset.nonblank) { "Realm ${realm.id} publishes a blank asset" }
        require(asset.tileSize in 1..4096 && asset.tileCount > 0) {
            "Realm ${realm.id} has invalid tile metadata"
        }
        require(asset.minZoom >= 0 && asset.maxZoom >= asset.minZoom) {
            "Realm ${realm.id} has invalid zoom metadata"
        }
        require(asset.contentPixelBounds.size == 4) {
            "Realm ${realm.id} has invalid content pixel bounds"
        }
        require(asset.contentLatlonBounds.size == 4 && asset.contentLatlonBounds.all(Double::isFinite)) {
            "Realm ${realm.id} has invalid realm-local map bounds"
        }
        require(asset.west < asset.east && asset.south < asset.north) {
            "Realm ${realm.id} has empty realm-local map bounds"
        }
        require(asset.west >= -180.0 && asset.east <= 180.0 && asset.south >= -90.0 && asset.north <= 90.0) {
            "Realm ${realm.id} has out-of-range realm-local map bounds"
        }
        asset.layoutComponents.forEach { component ->
            require(component.sourcePixelBounds.width > 0 && component.sourcePixelBounds.height > 0) {
                "Realm ${realm.id} has empty link source geometry"
            }
            require(component.assetPixelBounds.width == component.sourcePixelBounds.width &&
                component.assetPixelBounds.height == component.sourcePixelBounds.height) {
                "Realm ${realm.id} has non-size-preserving link geometry"
            }
            require(component.assetPixelBounds.minX >= 0 && component.assetPixelBounds.minY >= 0 &&
                component.assetPixelBounds.maxX <= asset.width &&
                component.assetPixelBounds.maxY <= asset.height) {
                "Realm ${realm.id} has link geometry outside its asset"
            }
        }
    }

    companion object {
        fun isSafeRelativeAssetPath(path: String): Boolean {
            if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
            if ('\\' in path) return false
            return path.split('/').none { it.isBlank() || it == "." || it == ".." }
        }
    }
}

fun osrsUndergroundRealmManifest.rasterProjectionOrNull(): osrsRealmRasterProjection? {
    val raster = inputs
        ?.runCatching { jsonObject }
        ?.getOrNull()
        ?.get("source_snapshots")
        ?.runCatching { jsonObject }
        ?.getOrNull()
        ?.get("raster")
        ?.runCatching { jsonObject }
        ?.getOrNull()
        ?: return null
    val bounds = raster["game_bounds"]
        ?.runCatching { jsonObject }
        ?.getOrNull()
        ?: return null
    val gameMinX = bounds["min_x"]?.jsonPrimitive?.intOrNull ?: return null
    val gameMaxY = bounds["max_y"]?.jsonPrimitive?.intOrNull ?: return null
    val scale = raster["game_coord_scale"]?.jsonPrimitive?.intOrNull ?: return null
    val width = raster["width"]?.jsonPrimitive?.intOrNull ?: return null
    val height = raster["height"]?.jsonPrimitive?.intOrNull ?: return null
    return runCatching {
        osrsRealmRasterProjection(gameMinX, gameMaxY, scale, width, height)
    }.getOrNull()
}

object osrsRealmSearch {
    fun matches(realm: osrsRealmRecord, query: String): Boolean {
        val terms = normalizedSearchText(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        val haystack = normalizedSearchText(realm.selectorText())
        return terms.all(haystack::contains)
    }
}

fun normalizedSearchText(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(OSRS_COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(OSRS_WHITESPACE, " ")
        .trim()
}

fun osrsRealmGroupLabel(group: String): String = when (group) {
    OSRS_REALM_GROUP_SURFACE -> "Surface"
    OSRS_REALM_GROUP_REALMS -> "Realms"
    OSRS_REALM_GROUP_OTHER_MAPS -> "Other maps"
    else -> group
}

const val OSRS_REALM_SCHEMA_VERSION = 1
const val OSRS_UNDERGROUND_MAPS_LABEL = "OSRS Underground Maps"
const val OSRS_UNDERGROUND_MAPS_APPLICATION_ID = "com.omiyawaki.osrswiki.undergroundmaps"
const val OSRS_REALM_GROUP_SURFACE = "surface"
const val OSRS_REALM_GROUP_REALMS = "realms"
const val OSRS_REALM_GROUP_OTHER_MAPS = "other_maps"

val OSRS_REALM_GROUPS = listOf(
    OSRS_REALM_GROUP_SURFACE,
    OSRS_REALM_GROUP_REALMS,
    OSRS_REALM_GROUP_OTHER_MAPS
)

private val OSRS_SHA256 = Regex("^[0-9a-f]{64}$")
private val OSRS_COMBINING_MARKS = Regex("\\p{M}+")
private val OSRS_WHITESPACE = Regex("\\s+")
