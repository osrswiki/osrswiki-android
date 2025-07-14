package com.omiyawaki.osrswiki.map

import kotlinx.serialization.Serializable

@Serializable
data class MapDiagnosticData(
    val y: Float,
    val x: Float,
    val width: Float,
    val height: Float,
    val lat: String?,
    val lon: String?,
    val zoom: String?,
    val plane: String?
)
