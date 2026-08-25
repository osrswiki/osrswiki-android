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
