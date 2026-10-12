# Virtual Planet: Local Skies

A Minecraft Java Edition **1.21.1** mod (Fabric) that treats the flat Minecraft world as a
**virtual spherical planet**, so that each player sees their own local sky - sunrise, noon, sunset or
night - at the *same* shared world time.

* Player A sees sunrise while player B, on the other side of the virtual planet, sees sunset.
* Player C sees midday while player D sees the night sky.
* Nothing about the world changes: terrain, chunks, blocks, redstone, mobs, player movement and survival
  gameplay are untouched. There is no second sun, no colour filter and no fake clock.
* The sun, moon, stars, fog colour and twilight are the **real vanilla celestial rendering**, driven by a
  per-player celestial phase computed from the player's coordinates.

## How it works

1. **Geometry** (`dev.virtualplanet.math.PlanetGeometry`) maps Minecraft's horizontal coordinate onto a
   longitude on a sphere of configurable circumference:
   `longitude = referenceLongitude + 2*PI*(x - referenceX)/circumference`.
2. **Time** (`dev.virtualplanet.math.PlanetTime`) keeps Minecraft's shared world time and derives the
   celestial phase from it, then shifts that phase by the longitude offset:
   `localSkyAngle = skyAngle(sharedTime) + (x - referenceX)/circumference`.
   Half a circumference means exactly half a rotation, so opposite players always get opposite skies.
3. **Rendering** (client mixin on `ClientWorld#getSkyAngle(float)`) returns that local phase to the
   vanilla sky renderer. Because vanilla derives the sun, moon, stars and fog colour from the same
   sky angle, all of them become local automatically, with no second sun and no second renderer.
4. **Astronomy** (`dev.virtualplanet.math.SolarCalculator`) additionally computes a real solar model
   (declination, hour angle, elevation, azimuth, sunrise/sunset, civil twilight, polar day/night) that is
   used for the commands, the debug output and the optional celestial tilt.

## Installation

1. Install Minecraft 1.21.1 with Fabric Loader 0.15.11 or newer.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 into `mods/`.
3. Put `virtualplanet-1.0.0.jar` (from the GitHub Actions artifact or the release) into `mods/`.
4. Start the game. On first run `config/virtualplanet.json` is created.

The mod is required on the **client** for the sky rendering; installing it on the server additionally
lets the server own the authoritative configuration and sync it to clients. A vanilla client can still
join a modded server (it simply sees vanilla skies).

## Configuration

`config/virtualplanet.json` (server-authoritative; synced to clients on join and on change):

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch |
| `deriveCircumferenceFromWorldBorder` | `true` | Use the world border to define the planet |
| `circumferenceBlocks` | `0` | Explicit circumference when not derived from the border |
| `borderCircumferenceFactor` | `1.0` | Border size to circumference multiplier |
| `referenceFollowsWorldBorderCenter` | `true` | Reference meridian at the border centre |
| `referenceX` / `referenceZ` | `0` | Reference coordinate (used when not following the border) |
| `referenceLongitudeDegrees` | `0` | Longitude of the reference coordinate |
| `rotationPeriodTicks` | `24000` | Ticks per planetary rotation (24000 = vanilla day length) |
| `smoothCelestialMotion` | `true` | Interpolate the celestial phase between ticks |
| `localMoonPhase` | `true` | Moon phase follows the local sky angle |
| `latitudeAxis` | `Z` | `NONE`, `Z` or `X` |
| `latitudeRangeDegrees` | `66.5` | Latitude at the mapped edge |
| `axialTiltDegrees` | `23.44` | Axial tilt used by the solar model |
| `seasonsEnabled` | `false` | Enable the seasonal declination cycle |
| `yearLengthDays` / `seasonOffsetDays` | `365` / `0` | Season length and phase |
| `renderLatitudeTilt` | `false` | **Experimental:** tilt the rendered arc by `declination - latitude` |
| `localSkyBrightness` | `true` | Skin brightness (light map) follows the local sky |
| `debug` | `false` | Verbose command output and logging |

Every value is validated: a malformed file is repaired with warnings instead of crashing the game.

## Commands

| Command | Permission | Effect |
| --- | --- | --- |
| `/virtualplanet info` | everyone | Shows the local virtual planet state for the caller |
| `/virtualplanet debug` | level 2 | Longitude, latitude, local solar time, elevation, azimuth, sunrise/sunset, twilight |
| `/virtualplanet recalculate` | level 2 | Re-reads the world border and rebuilds the planet geometry |
| `/virtualplanet reload` | level 2 | Reloads `config/virtualplanet.json` and re-syncs clients |

## What is verified, and what is not

Honesty matters more than marketing here.

* **Compiled and unit tested in CI** (GitHub Actions, Java 21): the whole math and configuration layer,
  including longitude normalisation, non-zero border centres, border resizing, huge coordinates,
  opposite-longitude behaviour at an identical world time, solar elevation/azimuth, sunrise/sunset,
  twilight, seasons, polar day/night and malformed configuration handling.
* **Runtime verified**: see `DEVELOPMENT_STATUS.md` for the exact, current state of in-game testing.
* **Not claimed**: physically accurate per-player block lighting or shadows. The mod changes the local
  *sky* (and optionally the local light map brightness); it does not re-light the terrain per player.
* **Shader packs (Iris/Optuna, Sodium)**: the sky hook is a normal mixin on `ClientWorld#getSkyAngle`, so
  Sodium is unaffected. Shader packs that replace the sky rendering entirely may ignore or override the
  mod's phase; see `DEVELOPMENT_STATUS.md` for the compatibility notes.

## Building

```
./gradlew build        # jar in build/libs/virtualplanet-1.0.0.jar
./gradlew test         # unit tests
```

Toolchain: Minecraft 1.21.1, Yarn `1.21.1+build.1`, Fabric Loader `0.15.11`, Fabric API
`0.116.17+1.21.1`, Loom `1.6.11`, Gradle 8.7, Java 21.

## License

MIT.
