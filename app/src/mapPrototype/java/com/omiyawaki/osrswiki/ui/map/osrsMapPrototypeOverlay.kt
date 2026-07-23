package com.omiyawaki.osrswiki.ui.map

import org.maplibre.android.geometry.LatLng
import kotlin.math.PI
import kotlin.math.ln

object osrsMapPrototypeOverlay {
    private const val BASE_MAPLIBRE_ZOOM = 7.3414426741929

    const val osrsSourceId = "osrs-surface-prototype-source"
    const val osrsLabelBadgeLayerId = "osrs-surface-prototype-label-badges"
    const val osrsRegionalLabelLayerId = "osrs-surface-prototype-regional-labels"
    const val osrsLocalLabelLayerId = "osrs-surface-prototype-local-labels"
    const val osrsPoiHaloLayerId = "osrs-surface-prototype-poi-halos"
    const val osrsPoiLayerId = "osrs-surface-prototype-pois"
    const val osrsMapLinkLayerId = "osrs-surface-prototype-map-links"
    const val osrsKeyHighlightLayerId = "osrs-surface-prototype-key-highlight"

    const val osrsPoiDefaultIconId = "osrs-prototype-poi"
    const val osrsPoiBankIconId = "osrs-prototype-bank"
    const val osrsPoiTransportIconId = "osrs-prototype-transport"
    const val osrsPoiDungeonIconId = "osrs-prototype-dungeon"
    const val osrsMapLinkIconId = "osrs-prototype-map-link"

    val semanticLayerIds = listOf(
        osrsLabelBadgeLayerId,
        osrsRegionalLabelLayerId,
        osrsLocalLabelLayerId,
        osrsPoiHaloLayerId,
        osrsPoiLayerId,
        osrsMapLinkLayerId,
        osrsKeyHighlightLayerId
    )

    val labelLayerIds = listOf(osrsLabelBadgeLayerId, osrsRegionalLabelLayerId, osrsLocalLabelLayerId)
    val poiLayerIds = listOf(osrsPoiHaloLayerId, osrsPoiLayerId, osrsKeyHighlightLayerId)
    val hitTestLayerIds = arrayOf(
        osrsMapLinkLayerId,
        osrsPoiLayerId,
        osrsPoiHaloLayerId,
        osrsLabelBadgeLayerId,
        osrsLocalLabelLayerId,
        osrsRegionalLabelLayerId
    )

    fun runtimePolicy(explicitlyEnabled: Boolean): osrsMapPrototypeRuntimePolicy {
        return osrsMapPrototypeRuntimePolicy(
            showControls = explicitlyEnabled,
            installOverlay = explicitlyEnabled,
            installHitTesting = explicitlyEnabled
        )
    }

    val referenceStops = listOf(
        osrsMapReferenceStop(percent = 37, scalePxPerSquare = 1.48, mapLibreZoom = stopZoom(37)),
        osrsMapReferenceStop(percent = 50, scalePxPerSquare = 2.0, mapLibreZoom = stopZoom(50)),
        osrsMapReferenceStop(percent = 75, scalePxPerSquare = 3.0, mapLibreZoom = stopZoom(75)),
        osrsMapReferenceStop(percent = 100, scalePxPerSquare = 4.0, mapLibreZoom = BASE_MAPLIBRE_ZOOM),
        osrsMapReferenceStop(percent = 200, scalePxPerSquare = 8.0, mapLibreZoom = stopZoom(200))
    )

    const val initialZoom = BASE_MAPLIBRE_ZOOM

    fun initialCenter(): LatLng = gameToLatLng(3198.0, 3224.0)

    val categoryManifest = listOf(
        osrsMapCategoryDefinition(osrsMapCategory.BANK, "Banks", "B", 0xff00c853.toInt()),
        osrsMapCategoryDefinition(osrsMapCategory.TRANSPORTATION, "Transport", "T", 0xff00b0ff.toInt()),
        osrsMapCategoryDefinition(osrsMapCategory.DUNGEON, "Dungeons", "D", 0xffb388ff.toInt()),
        osrsMapCategoryDefinition(osrsMapCategory.GENERAL, "Places", "*", 0xfff9d66b.toInt())
    )

    val surfaceManifest = listOf(
        osrsMapSurfaceDefinition("gielinor-surface", "Gielinor Surface", true, "offline surface dataset"),
        osrsMapSurfaceDefinition(
            "kharidian-desert-underground",
            "Kharidian Desert Underground",
            false,
            "unavailable: transform evidence pending"
        ),
        osrsMapSurfaceDefinition(
            "misthalin-underground",
            "Misthalin Underground",
            false,
            "unavailable: transform evidence pending"
        ),
        osrsMapSurfaceDefinition("zanaris", "Zanaris", false, "unavailable: bounded surface asset pending")
    )

    val features = listOf(
        osrsMapFeature(
            id = "label-misthalin",
            name = "Kingdom of Misthalin",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.REGIONAL,
            gameX = 3235.0,
            gameY = 3330.0,
            priority = 10
        ),
        osrsMapFeature(
            id = "label-asgarnia",
            name = "Kingdom of Asgarnia",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.REGIONAL,
            gameX = 2965.0,
            gameY = 3415.0,
            priority = 10
        ),
        osrsMapFeature(
            id = "label-lumbridge",
            name = "SEM Lumbridge",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3222.0,
            gameY = 3218.0,
            priority = 30
        ),
        osrsMapFeature(
            id = "label-lumbridge-swamp",
            name = "SEM Lumbridge Swamp",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3198.0,
            gameY = 3160.0,
            priority = 35
        ),
        osrsMapFeature(
            id = "label-lumbridge-castle",
            name = "SEM Lumbridge Castle",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3221.0,
            gameY = 3210.0,
            priority = 32
        ),
        osrsMapFeature(
            id = "label-lumbridge-river",
            name = "SEM River Lum",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3240.0,
            gameY = 3235.0,
            priority = 34
        ),
        osrsMapFeature(
            id = "label-semantic-center",
            name = "SEM Surface Test",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3198.0,
            gameY = 3218.0,
            priority = 28
        ),
        osrsMapFeature(
            id = "label-draynor",
            name = "SEM Draynor",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3093.0,
            gameY = 3244.0,
            priority = 25
        ),
        osrsMapFeature(
            id = "label-al-kharid",
            name = "SEM Al Kharid",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3293.0,
            gameY = 3183.0,
            priority = 25
        ),
        osrsMapFeature(
            id = "label-wizards-tower",
            name = "SEM Wizards' Tower",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 3109.0,
            gameY = 3164.0,
            priority = 30
        ),
        osrsMapFeature(
            id = "label-falador",
            name = "SEM Falador",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.LOCAL,
            gameX = 2965.0,
            gameY = 3378.0,
            priority = 20,
            hitOverlapFixture = true
        ),
        osrsMapFeature(
            id = "label-karamja",
            name = "SEM Karamja",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.REGIONAL,
            gameX = 2918.0,
            gameY = 3175.0,
            priority = 20
        ),
        osrsMapFeature(
            id = "label-ardent-ocean",
            name = "Ardent Ocean",
            kind = osrsMapFeatureKind.LABEL,
            labelTier = osrsMapLabelTier.REGIONAL,
            gameX = 2520.0,
            gameY = 2550.0,
            priority = 8,
            maxZoom = 6.55
        ),
        osrsMapFeature(
            id = "poi-lumbridge-bank",
            name = "SEM Lumbridge bank",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.BANK,
            gameX = 3208.0,
            gameY = 3220.0,
            priority = 20
        ),
        osrsMapFeature(
            id = "poi-lumbridge-castle",
            name = "SEM Lumbridge Castle",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.GENERAL,
            gameX = 3221.0,
            gameY = 3219.0,
            priority = 25
        ),
        osrsMapFeature(
            id = "poi-lumbridge-swamp-caves",
            name = "SEM Lumbridge Swamp Caves",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.DUNGEON,
            gameX = 3168.0,
            gameY = 3172.0,
            priority = 40
        ),
        osrsMapFeature(
            id = "poi-draynor-bank",
            name = "SEM Draynor bank",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.BANK,
            gameX = 3092.0,
            gameY = 3245.0,
            priority = 20
        ),
        osrsMapFeature(
            id = "poi-al-kharid-bank",
            name = "SEM Al Kharid bank",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.BANK,
            gameX = 3269.0,
            gameY = 3167.0,
            priority = 20
        ),
        osrsMapFeature(
            id = "poi-wizards-tower",
            name = "SEM Wizards' Tower",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.GENERAL,
            gameX = 3109.0,
            gameY = 3164.0,
            priority = 30
        ),
        osrsMapFeature(
            id = "poi-lumbridge-canoe",
            name = "SEM Lumbridge canoe",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.TRANSPORTATION,
            gameX = 3241.0,
            gameY = 3235.0,
            priority = 30
        ),
        osrsMapFeature(
            id = "poi-semantic-center",
            name = "SEM central test POI",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.GENERAL,
            gameX = 3198.0,
            gameY = 3224.0,
            priority = 18
        ),
        osrsMapFeature(
            id = "poi-falador-bank",
            name = "SEM Falador west bank",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.BANK,
            gameX = 2946.0,
            gameY = 3369.0,
            priority = 18
        ),
        osrsMapFeature(
            id = "poi-falador-square-overlap",
            name = "SEM Falador square",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.GENERAL,
            gameX = 2965.0,
            gameY = 3378.0,
            priority = 16,
            hitOverlapFixture = true
        ),
        osrsMapFeature(
            id = "poi-ardent-anchorage",
            name = "SEM Ardent anchorage",
            kind = osrsMapFeatureKind.POI,
            category = osrsMapCategory.TRANSPORTATION,
            gameX = 2520.0,
            gameY = 2550.0,
            priority = 12
        ),
        osrsMapFeature(
            id = "link-draynor-recenter",
            name = "SEM Recenter Draynor",
            kind = osrsMapFeatureKind.MAP_LINK,
            category = osrsMapCategory.TRANSPORTATION,
            gameX = 3110.0,
            gameY = 3200.0,
            priority = 15,
            action = osrsMapAction.RECENTER,
            destinationSurface = "Draynor semantic test cluster",
            destinationGameX = 3093.0,
            destinationGameY = 3244.0
        ),
        osrsMapFeature(
            id = "link-al-kharid-recenter",
            name = "SEM Recenter Al Kharid",
            kind = osrsMapFeatureKind.MAP_LINK,
            category = osrsMapCategory.TRANSPORTATION,
            gameX = 3234.0,
            gameY = 3198.0,
            priority = 15,
            action = osrsMapAction.RECENTER,
            destinationSurface = "Al Kharid semantic test cluster",
            destinationGameX = 3293.0,
            destinationGameY = 3183.0
        ),
        osrsMapFeature(
            id = "link-kharidian-underground-pending",
            name = "Kharidian underground link",
            kind = osrsMapFeatureKind.MAP_LINK,
            category = osrsMapCategory.TRANSPORTATION,
            gameX = 3208.0,
            gameY = 3207.0,
            priority = 12,
            action = osrsMapAction.UNKNOWN_PENDING_EVIDENCE,
            destinationSurface = "Kharidian underground unavailable",
            destinationSurfaceId = "kharidian-desert-underground"
        )
    )

    val searchSuggestions: List<String> = features
        .filter { it.kind == osrsMapFeatureKind.LABEL || it.kind == osrsMapFeatureKind.POI }
        .map { it.searchName() }
        .distinct()
        .sorted()

    fun search(query: String): osrsMapFeature? {
        val normalized = query.normalizedSearchText()
        if (normalized.isBlank()) return null
        return features
            .asSequence()
            .filter { it.kind == osrsMapFeatureKind.LABEL || it.kind == osrsMapFeatureKind.POI }
            .map { it to it.searchName().normalizedSearchText() }
            .sortedWith(
                compareBy<Pair<osrsMapFeature, String>>(
                    { if (it.second == normalized) 0 else 1 },
                    { if (it.second.startsWith(normalized)) 0 else 1 },
                    { it.first.priority }
                )
            )
            .firstOrNull { (_, candidate) -> candidate.contains(normalized) || normalized.contains(candidate) }
            ?.first
    }

    fun geoJson(): String {
        return buildString {
            append("""{"type":"FeatureCollection","features":[""")
            features.forEachIndexed { index, feature ->
                if (index > 0) append(',')
                append(feature.toGeoJsonFeature())
            }
            append("]}")
        }
    }

    fun gameToLatLng(gx: Double, gy: Double): LatLng {
        return LatLng(
            osrsMapDefaultView.latitudeForGameY(gy),
            osrsMapDefaultView.longitudeForGameX(gx)
        )
    }

    fun latLngToGame(latLng: LatLng): Pair<Double, Double> {
        val gameX = osrsMapDefaultView.GAME_MIN_X +
            ((latLng.longitude + 180.0) / 360.0) *
            osrsMapDefaultView.CANVAS_SIZE / osrsMapDefaultView.GAME_COORD_SCALE
        val latitudeRadians = Math.toRadians(latLng.latitude)
        val mercatorY = ln(kotlin.math.tan(PI / 4.0 + latitudeRadians / 2.0))
        val normalizedY = (1.0 - mercatorY / PI) / 2.0
        val gameY = osrsMapDefaultView.GAME_MAX_Y -
            normalizedY * osrsMapDefaultView.CANVAS_SIZE / osrsMapDefaultView.GAME_COORD_SCALE
        return gameX to gameY
    }

    fun nearestReferenceStop(zoom: Double): osrsMapReferenceStop {
        return referenceStops.minByOrNull { kotlin.math.abs(it.mapLibreZoom - zoom) } ?: referenceStops[3]
    }

    private fun stopZoom(percent: Int): Double {
        return BASE_MAPLIBRE_ZOOM + log2(percent / 100.0)
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)

    private fun osrsMapFeature.toGeoJsonFeature(): String {
        val latLng = gameToLatLng(gameX, gameY)
        return buildString {
            append("""{"type":"Feature","id":""")
            append(json(id))
            append(""","geometry":{"type":"Point","coordinates":[""")
            append(latLng.longitude)
            append(',')
            append(latLng.latitude)
            append("""]},"properties":{""")
            append(""""id":""")
            append(json(id))
            append(""","name":""")
            append(json(name))
            append(""","kind":""")
            append(json(kind.value))
            append(""","category":""")
            append(json(category.value))
            append(""","label_tier":""")
            append(json(labelTier.value))
            append(""","action":""")
            append(json(action.value))
            append(""","destination_surface":""")
            append(json(destinationSurface))
            append(""","destination_surface_id":""")
            append(json(destinationSurfaceId))
            append(""","destination_game_x":""")
            append(destinationGameX ?: gameX)
            append(""","destination_game_y":""")
            append(destinationGameY ?: gameY)
            append(""","priority":""")
            append(priority)
            append(""","game_x":""")
            append(gameX)
            append(""","game_y":""")
            append(gameY)
            append(""","hit_overlap_fixture":""")
            append(hitOverlapFixture)
            append("}}")
        }
    }

    private fun json(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }

    private fun osrsMapFeature.searchName(): String = name.removePrefix("SEM ")

    private fun String.normalizedSearchText(): String {
        return lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
    }
}

data class osrsMapReferenceStop(
    val percent: Int,
    val scalePxPerSquare: Double,
    val mapLibreZoom: Double
)

data class osrsMapFeature(
    val id: String,
    val name: String,
    val kind: osrsMapFeatureKind,
    val labelTier: osrsMapLabelTier = osrsMapLabelTier.NONE,
    val category: osrsMapCategory = osrsMapCategory.NONE,
    val action: osrsMapAction = osrsMapAction.NONE,
    val destinationSurface: String = "",
    val destinationSurfaceId: String = "gielinor-surface",
    val destinationGameX: Double? = null,
    val destinationGameY: Double? = null,
    val gameX: Double,
    val gameY: Double,
    val priority: Int,
    val minZoom: Double = Double.NEGATIVE_INFINITY,
    val maxZoom: Double = Double.POSITIVE_INFINITY,
    val hitOverlapFixture: Boolean = false
)

data class osrsMapCategoryDefinition(
    val category: osrsMapCategory,
    val title: String,
    val glyph: String,
    val color: Int
)

data class osrsMapSurfaceDefinition(
    val id: String,
    val title: String,
    val available: Boolean,
    val availability: String
)

enum class osrsMapFeatureKind(val value: String) {
    LABEL("label"),
    POI("poi"),
    MAP_LINK("map_link")
}

enum class osrsMapLabelTier(val value: String) {
    NONE("none"),
    REGIONAL("regional"),
    LOCAL("local")
}

enum class osrsMapCategory(val value: String) {
    NONE("none"),
    GENERAL("general"),
    BANK("bank"),
    DUNGEON("dungeon"),
    TRANSPORTATION("transportation")
}

enum class osrsMapAction(val value: String) {
    NONE("none"),
    RECENTER("recenter"),
    SWITCH_SURFACE("switch_surface"),
    UNKNOWN_PENDING_EVIDENCE("unknown_pending_evidence")
}
