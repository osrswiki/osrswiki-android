package com.omiyawaki.osrswiki.undergroundmaps

import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmManifestParser

internal fun osrsTestCatalog(): osrsRealmCatalog = osrsRealmManifestParser().parse(OSRS_TEST_MANIFEST)

internal val OSRS_TEST_MANIFEST = """
    {
      "schema_version": 1,
      "candidate": "test-001",
      "product": {
        "label": "OSRS Underground Maps",
        "application_id": "com.omiyawaki.osrswiki.undergroundmaps"
      },
      "inputs": {
        "source_snapshots": {
          "raster": {
            "game_bounds": {"min_x": 960, "min_y": 1216, "max_x": 4224, "max_y": 12608},
            "game_coord_scale": 4,
            "width": 13056,
            "height": 45568
          }
        }
      },
      "accounting": {"unresolved_content_pixels": 0},
      "realms": [
        {
          "id": "cache-world-map:lms-desert-island",
          "canonical_name": "Last Man Standing Desert Island",
          "aliases": ["LMS Island"],
          "group": "realms",
          "is_surface": false,
          "native_file_id": 36,
          "map_id": 36,
          "article": "Last Man Standing",
          "center": [3400.0, 5800.0],
          "default_plane": 0,
          "planes": [0, 1],
          "cache_declared_planes": [0, 1],
          "assets": [
            ${osrsTestAsset(0, "realms/lms-0.mbtiles", -20.0, -10.0, 20.0, 10.0, 9506, 26974)},
            ${osrsTestAsset(1, "realms/lms-1.mbtiles", -20.0, -10.0, 20.0, 10.0, 9506, 26974)}
          ]
        },
        {
          "id": "cache-world-map:main",
          "canonical_name": "Gielinor Surface",
          "aliases": ["Surface"],
          "group": "surface",
          "is_surface": true,
          "native_file_id": 0,
          "map_id": 0,
          "article": "Map:Gielinor",
          "center": [3222.0, 3218.0],
          "default_plane": 0,
          "planes": [0],
          "cache_declared_planes": [0],
          "assets": [
            ${osrsTestAsset(0, "realms/surface.mbtiles", -40.0, -30.0, 40.0, 30.0, 8794, 37302)}
          ]
        },
        {
          "id": "other-map-10042",
          "canonical_name": "Player-owned House",
          "aliases": ["POH"],
          "group": "other_maps",
          "is_surface": false,
          "map_id": 10042,
          "article": "Player-owned house",
          "center": [2000.0, 9000.0],
          "default_plane": 0,
          "planes": [0],
          "assets": [
            ${osrsTestAsset(0, "realms/poh.mbtiles", -15.0, -15.0, 15.0, 15.0, 4000, 14000)}
          ]
        }
      ],
      "selector": {}
    }
""".trimIndent()

private fun osrsTestAsset(
    plane: Int,
    path: String,
    west: Double,
    south: Double,
    east: Double,
    north: Double,
    sourcePixelMinX: Int,
    sourcePixelMinY: Int
): String = """
    {
      "plane": $plane,
      "mbtiles_path": "$path",
      "mbtiles_sha256": "${"a".repeat(64)}",
      "mbtiles_bytes": 4096,
      "mask_path": "masks/mask-$plane.png",
      "mask_sha256": "${"b".repeat(64)}",
      "width": 512,
      "height": 512,
      "nonblank": true,
      "tile_size": 512,
      "min_zoom": 0,
      "max_zoom": 6,
      "tile_count": 7,
      "canvas_size": 512,
      "content_pixel_bounds": [0, 0, 512, 512],
      "content_latlon_bounds": [$west, $south, $east, $north],
      "source_bounds": [0, 0, 512, 512],
      "display_bounds": [0, 0, 512, 512],
      "layout_components": [{
        "source_pixel_bounds": {
          "min_x": $sourcePixelMinX,
          "min_y": $sourcePixelMinY,
          "max_x": ${sourcePixelMinX + 512},
          "max_y": ${sourcePixelMinY + 512}
        },
        "asset_pixel_bounds": {"min_x": 0, "min_y": 0, "max_x": 512, "max_y": 512},
        "source_to_display_dx_pixels": 0,
        "source_to_display_dy_pixels": 0,
        "provenance_codes": [1],
        "assigned_source_pixel_count": 262144
      }]
    }
""".trimIndent()
