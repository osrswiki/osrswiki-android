# Underground realm release assets

The canonical `underground-realms.json` catalog and nested `**/*.mbtiles`
files required for a full underground-maps build are immutable GitHub Release
assets, not Git source.

The pinned release, each file's destination path, GitHub asset name, SHA-256,
byte size, and download URL are recorded in
`underground-assets-manifest.json`. Nested paths cannot be GitHub Release
asset names (`/` is illegal), so the public files are published with `/`
flattened to `__`. Materialize restores the nested layout Gradle already
consumes (`underground-realms.json` plus `**/*.mbtiles`).

```bash
./scripts/fetch-underground-assets.sh materialize \
  underground-assets-manifest.json \
  ./underground-realms-release
```

Then point a full-map Android assemble at that directory. Relative
`-PosrsUndergroundAssetsDir` values resolve from the Android Gradle root
(the public repo root), not `undergroundmaps/`:

```bash
./gradlew :undergroundmaps:assembleRelease \
  -PosrsUndergroundAssetsDir=underground-realms-release
```

`OSRS_UNDERGROUND_ASSETS_DIR` is the equivalent environment variable
(absolute paths still work). Without
that opt-in, `prepareUndergroundRealmAssets` stages the in-tree fixture stub
from `undergroundmaps/src/fixtureAssets`. There is no implicit home-cache
fallback.

The script downloads only the release named by the manifest and rejects a
missing file, unexpected manifest entry, size mismatch, checksum mismatch, or
URL off the pinned `osrswiki/osrswiki-tooling` tag. Generated rasters and other
map-building intermediates remain host-local and are never published as Git
history.

## Automated weekly updates

Underground assets share the `pixi run map-auto-update` path with the
surface floors. After the live OpenRS2 cache is selected, the job exports
cache-native inventory and alignment, and when provenance rasters, wiki
basemaps, and the locked builder can run, it rebuilds the realm tree,
compares it to the current pin by content (MBTiles tile payloads, not
SQLite wrapper bytes), and publishes a **new**
`underground-assets-v1-<12-hex>` tooling release if the bytes changed.
Wiki `basemaps.json` is fetched from `latest/`, then
`osrs/versions/<live-cache-date>_a` (and `_b`), then the last known
working snapshot (`osrs/versions/2026-03-04_a`) because `latest/` is not
always published. Cache natives the wiki snapshot has not labeled yet
are built cache-only; wiki natives that disappeared from the cache are
skipped instead of failing the rebuild. Native `cache-world-map:*`
realms with no rendered pixels (for example Vampyrium on the current
floors) are excluded the same way as empty `cache-special-region:*`
records.
Old tags stay downloadable. `--dry-run` skips upload and the pin PR.
`scripts/shared/publish-underground-release-assets.sh --create-release`
is the only supported uploader.

The weekly LaunchAgent on mini (`com.omiyawaki.osrswiki.map-auto-update`)
covers both surface and underground. If the locked rebuild fails, the job
keeps the current underground pin so surface can still update, and opens
or comments one GitHub issue (`map-auto-update: underground rebuild
failed`) instead of failing silently. FOSS / F-Droid still opt in to the
real underground catalog on a later app version; this pipeline does not
edit fdroiddata.

The `./scripts/fetch-map-assets.sh materialize` prebuild line downloads one
`map_floors.zip` for the surface floors. Do not expand it into four separate
mbtiles curls.

## F-Droid / FOSS later version

FOSS and F-Droid inclusion of the real underground catalog is opt-in on a
**later** app version. Do **not** add these lines to GitLab fdroiddata MR
!46596 (GitHub `v2.0.1` / versionCode 39). The intended later-version fdroiddata lines (public `osrswiki-android`
tree) are:

```yaml
prebuild:
  - ./scripts/fetch-map-assets.sh materialize
      map-assets-manifest.json app/src/main/assets
  - ./scripts/fetch-underground-assets.sh materialize
      underground-assets-manifest.json underground-realms-release
  - echo 'osrsUndergroundAssetsDir=underground-realms-release' >> gradle.properties
```

Until that later version, a clean public-tree assemble continues to ship the
fixture stub.
