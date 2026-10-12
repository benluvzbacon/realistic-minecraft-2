# Virtual Planet: Local Skies

A Minecraft Java Edition **1.21.1** mod (Fabric) that treats the flat Minecraft world as a
**virtual spherical planet**, so that each player sees their own local sky - sunrise, noon, sunset or
night - at the *same* shared world time.

* Player A sees sunrise while player B, on the other side of the virtual planet, sees sunset.
* Player C sees midday while player D sees the night sky.
* Nothing about the world changes: terrain, chunks, blocks, redstone, mobs, player movement and survival
  gameplay are untouched. There is no second sun, no colour filter and no fake clock.
* The sun, moon, stars and the sky/cloud/fog colours are the **real vanilla celestial rendering**, driven
  by a per-player celestial phase computed from the player's coordinates.

## How it works

1. **Geometry** (`dev.virtualplanet.math.PlanetGeometry`) maps Minecraft's horizontal coordinate onto a
   longitude on a sphere of configurable circumference:
   `longitude = referenceLongitude + 2*PI*(x - referenceX)/circumference`, normalised into `[-PI, PI)`.
   The radius is `circumference / (2*PI)`, and by default the circumference comes from the **world
   border** - including a border centre that is not the origin, and border resizes.
2. **Time** (`dev.virtualplanet.math.PlanetTime`) keeps Minecraft's shared world time and derives the
   celestial phase from it, then shifts that phase by the longitude offset:
   `localSkyAngle = skyAngle(sharedTime) + (x - referenceX)/circumference`.
   Half a circumference means exactly half a rotation, so opposite players always get opposite skies.
   `rotationPeriodTicks` (default 24000, i.e. vanilla) says how many ticks one planetary rotation takes;
   the phase shift itself never depends on it.
   The shared world time itself is never touched: `/time query daytime` and the F3 screen still report
   the server's own clock, because the mod only changes the *phase derived from* it. Three different
   quantities are kept apart: the shared world time in ticks, the celestial phase in turns
   (`0.0` = the meridian's own noon), and the local solar time as a clock string (`12:00` = local noon).
3. **Rendering** (`dev.virtualplanet.client.mixin.ClientWorldMixin`) adds
   `getSkyAngle(float)` to `ClientWorld`. In 1.21.1 the sky angle is an *interface default method*
   (`LunarWorldView#getSkyAngle`), so there is nothing concrete to inject into - adding the method
   overrides it for every call site at once: the four calls inside `ClientWorld` (sky brightness for the
   light map, sky colour, cloud colour, fog colour) and the single call in `WorldRenderer#renderSky` that
   rotates the sun, moon and stars. One sun, drawn where the viewer's longitude puts it; the vanilla sun
   can never disagree with the mod, because it *is* the vanilla sun.
4. **Astronomy** (`dev.virtualplanet.math.SolarCalculator`) computes a real solar model (declination,
   hour angle, elevation, azimuth, sunrise/sunset, civil twilight, polar day/night). It is what the
   commands and the debug output report, and what `seasonsEnabled`/`axialTiltDegrees` drive.
5. **Moon** (`dev.virtualplanet.sky.LocalSkyModel`) gives each player the moon phase of *their* local day
   boundary, and `LunarWorldView#getMoonSize()` follows it automatically because vanilla derives the moon
   size from the phase.

## Installation

1. Install Minecraft 1.21.1 with Fabric Loader 0.15.11 or newer.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 into `mods/`.
3. Put `virtualplanet-1.0.0.jar` (GitHub Actions artifact or release) into `mods/`.
4. Start the game. On first run `config/virtualplanet.json` is created.

The mod is required on the **client** for the sky rendering; installing it on the server additionally
lets the server own the authoritative configuration and sync it to clients. A vanilla client can join a
modded server: it simply sees vanilla skies, because the server only sends the configuration to clients
that declared the channel.

## Configuration

`config/virtualplanet.json` (server-authoritative; synced to clients on join and on change):

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch (off = exactly vanilla skies) |
| `deriveCircumferenceFromWorldBorder` | `true` | Use the world border to define the planet |
| `circumferenceBlocks` | `0` | Explicit circumference when not derived from the border |
| `borderCircumferenceFactor` | `1.0` | Border size to circumference multiplier |
| `referenceFollowsWorldBorderCenter` | `true` | Reference meridian at the border centre |
| `referenceX` / `referenceZ` | `0` | Reference coordinate (used when not following the border) |
| `referenceLongitudeDegrees` | `0` | Longitude of the reference coordinate |
| `rotationPeriodTicks` | `24000` | Ticks per planetary rotation (24000 = vanilla day length) |
| `smoothCelestialMotion` | `true` | Interpolate the celestial phase between ticks |
| `localMoonPhase` | `true` | Moon phase follows the viewer's local day |
| `latitudeAxis` | `Z` | `NONE`, `Z` or `X` |
| `latitudeRangeDegrees` | `66.5` | Latitude reached at half the mapped span |
| `axialTiltDegrees` | `23.44` | Axial tilt used by the solar model |
| `seasonsEnabled` | `false` | Enable the seasonal declination cycle |
| `yearLengthDays` / `seasonOffsetDays` | `365` / `0` | Season length and phase |
| `localSkyBrightness` | `true` | The client light map follows the local sky |
| `debug` | `false` | Verbose logging and command output |

Every value is validated: a malformed file is repaired with warnings instead of crashing the game, and an
undecodable network payload falls back to the defaults.

## Commands

| Command | Permission | Effect |
| --- | --- | --- |
| `/virtualplanet info` | everyone | The caller's virtual longitude and latitude, local solar time, sun elevation and azimuth, sunrise/sunset, twilight, moon phase - computed with the same code the client renders |
| `/virtualplanet debug` | level 2 | Toggles verbose logging and command output |
| `/virtualplanet recalculate` | level 2 | Re-sends the configuration (which drops every client's cached sky view) and prints the caller's local solar time |
| `/virtualplanet reload` | level 2 | Reloads `config/virtualplanet.json`, repairs it, re-syncs clients and reports the repairs |

## What is verified, and what is not

Honesty matters more than marketing here.

* **Compiled and unit tested in CI** (GitHub Actions, Java 21, `./gradlew test` then `./gradlew build`,
  jar uploaded as an artifact): the whole math, sky and configuration layer - longitude normalisation,
  non-zero border centres, border resizing, huge coordinates, opposite longitudes at an identical world
  time, four players at quarter longitudes seeing midday/sunset/midnight/sunrise, rotation-period
  independence, latitude mapping and clamping, polar day/night, seasons, the vanilla brightness and moon
  phase formulas, and malformed configuration handling.
* **Runtime verified**: nothing yet. The development environment has no Minecraft client, so the mixin
  has never been loaded in a running game. `DEVELOPMENT_STATUS.md` records exactly which claims are
  verified in CI and which are design-level.
* **Not claimed**: physically accurate per-player block lighting or shadows. The mod changes the local
  *sky* and, optionally, the local light map brightness; it does not re-light terrain per player.
* **Limitations**: the latitude function is a documented linear mapping (clamped at
  `latitudeRangeDegrees`), not a distortion-free projection. The drawn sun path is vanilla's east-west
  great circle: latitude and declination affect the reported astronomy, the moon phase and the light
  map, but the mod does not rotate the rendered arc, because that would mean replacing a second part of
  the vanilla sky renderer for an optional cosmetic effect.
* **Shader packs (Iris/Optuna) and Sodium**: the sky hook is a normal mixin on `ClientWorld#getSkyAngle`,
  which Sodium does not replace. Nothing about shader packs is promised: a pack that renders its own sky
  may ignore or override the phase.

## Building

```
./gradlew build        # jar in build/libs/virtualplanet-1.0.0.jar
./gradlew test         # unit tests
```

Toolchain: Minecraft 1.21.1, Yarn `1.21.1+build.1`, Fabric Loader `0.15.11`, Fabric API
`0.116.17+1.21.1`, Loom `1.6.11`, Gradle 8.7, Java 21.

## License

MIT.
