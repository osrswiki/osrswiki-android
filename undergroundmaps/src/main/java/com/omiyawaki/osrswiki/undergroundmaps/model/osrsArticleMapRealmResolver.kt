package com.omiyawaki.osrswiki.undergroundmaps.model

/**
 * Chooses the realm an in-article Kartographer embed should load.
 *
 * Wiki maps encode two independent coordinates: OSRS plane (`data-plane`) and
 * the wiki map id (`data-mapid`, also visible as `/rendered/{id}/` tiles).
 * Surface is only the default when no map id matches and no other realm layout
 * contains the game coordinate.
 */
data class osrsArticleMapRealmResolution(
    val realm: osrsRealmRecord,
    val destination: osrsRealmEndpointDestination?,
    val plane: Int
)

object osrsArticleMapRealmResolver {
    fun resolve(
        catalog: osrsRealmCatalog,
        mapper: osrsRealmEndpointMapper,
        mapId: Int?,
        plane: Int,
        gameX: Int,
        gameY: Int
    ): osrsArticleMapRealmResolution {
        val requested = osrsRealmLinkPosition(plane, gameX, gameY)
        val mapIdMatches = if (mapId == null) {
            emptyList()
        } else {
            catalog.manifest.realms.filter { it.mapId == mapId }
        }
        val searchOrder = when {
            mapIdMatches.isNotEmpty() -> mapIdMatches + catalog.manifest.realms.filter { realm ->
                mapIdMatches.none { it.id == realm.id }
            }
            else -> catalog.manifest.realms
        }

        fun mapped(realm: osrsRealmRecord, position: osrsRealmLinkPosition) =
            mapper.map(realm, position)

        for (realm in searchOrder) {
            mapped(realm, requested)?.let { destination ->
                return osrsArticleMapRealmResolution(realm, destination, destination.plane)
            }
        }
        for (realm in searchOrder) {
            for (candidatePlane in realm.planes) {
                if (candidatePlane == plane) continue
                mapped(realm, osrsRealmLinkPosition(candidatePlane, gameX, gameY))?.let { destination ->
                    return osrsArticleMapRealmResolution(realm, destination, destination.plane)
                }
            }
        }

        val fallbackRealm = mapIdMatches.firstOrNull() ?: catalog.surface
        val fallbackPlane = fallbackRealm.assetForPlane(plane)?.plane
            ?: fallbackRealm.defaultPlane
        return osrsArticleMapRealmResolution(
            realm = fallbackRealm,
            destination = mapped(fallbackRealm, osrsRealmLinkPosition(fallbackPlane, gameX, gameY)),
            plane = fallbackPlane
        )
    }
}
