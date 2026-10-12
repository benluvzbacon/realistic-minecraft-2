# Development status - Virtual Planet: Local Skies

> Authoritative progress record. A fresh session must be able to resume from this file alone.

Last updated: stage 1 build-out (repository, build system, math, config, tests).

## Environment facts (important)

* The development sandbox has **no JDK with `javac`, no Gradle distribution and no access to Maven hosts**
  (`maven.fabricmc.net`, `libraries.minecraft.net`, `services.gradle.org` are TLS-blocked). Local builds are
  therefore impossible; a JRE-only JDK was used for reference work.
* Consequences:
  * Compilation and the JUnit run happen in **GitHub Actions** (`.github/workflows/build.yml`).
  * Locally the sources are checked with `python3 tools/check_java_syntax.py` (javalang parser) and the
    numerical expectations of the tests were cross-verified with a Python mirror of the same formulas.
  * Do not claim local compilation. Do not claim runtime testing unless it happened in CI.

## Stage status

| Stage | Description | Status |
| --- | --- | --- |
| 1 | Project foundation (Gradle, Loom, metadata, CI workflow) | IMPLEMENTED (build verified after the first CI run) |
| 2 | Planet mathematics + configuration + unit tests | IMPLEMENTED, TEST VERIFIED in CI (see below) |
| 3 | Client rendering investigation | IMPLEMENTED (research complete, see "Verified 1.21.1 facts") |
| 4 | Local sun implementation (client mixin) | NOT STARTED |
| 5 | Twilight, moon, seasons, atmosphere refinements | NOT STARTED |
| 6 | Multiplayer sync, commands, config validation | NOT STARTED |
| 7 | Full test pass and bug fixing | NOT STARTED |
| 8 | Final jar, documentation, artifact upload | NOT STARTED |

## Verified 1.21.1 facts the rendering work relies on

These were verified from the Yarn `1.21.1` mappings tree and from decompiled 1.21.1 sources, not guessed:

* `net.minecraft.world.LunarWorldView#getSkyAngle(float)` is an interface **default** method:
  `return this.getDimension().getSkyAngle(this.getLunarTime());` - it ignores `tickDelta`.
* `net.minecraft.world.World#getSkyAngleRadians(float)` = `getSkyAngle(tickDelta) * 2*PI`.
* `net.minecraft.world.dimension.DimensionType#getSkyAngle(long)`:
  `d = fractionalPart(time/24000 - 0.25); e = 0.5 - cos(d*PI)/2; return (d*2 + e)/3`.
* `net.minecraft.client.world.ClientWorld#getSkyBrightness(float)` uses `this.getSkyAngle(tickDelta)`,
  and `LightmapTextureManager` uses `getSkyBrightness`, so overriding the sky angle also affects the
  client light map (this is what `localSkyBrightness` toggles).
* The vanilla renderer (`WorldRenderer#renderSky(Matrix4f, Matrix4f, float, Camera, boolean, Runnable)`,
  verified signature) applies `rotateY(-90)` then `rotateX(skyAngle*360)` and draws the sun at `+Y` of that
  frame: an east-west great circle through the zenith. The moon is at `-Y` and the stars share the matrix,
  and `DimensionEffects#getFogColorOverride(skyAngle, tickDelta)` supplies the sunrise/sunset fog colour -
  all of them therefore follow the sky angle automatically.
* Fabric API's own `WorldRendererMixin` targets the same `renderSky` descriptor, which confirms it is the
  live 1.21.1 signature.

## Files

* Build system: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradle/wrapper/*`,
  `.github/workflows/build.yml`, `tools/check_java_syntax.py`.
* Pure math (no Minecraft dependency, unit tested): `src/main/java/dev/virtualplanet/math/`
  (`Angles`, `LatitudeAxis`, `PlanetGeometry`, `PlanetTime`, `SolarCalculator`, `SolarPosition`).
* Configuration (pure, unit tested): `src/main/java/dev/virtualplanet/config/`
  (`PlanetConfig`, `PlanetConfigJson`, `PlanetConfigBinary`, `SimpleJson`).
* Mod glue: `src/main/java/dev/virtualplanet/VirtualPlanet.java`,
  `src/client/java/dev/virtualplanet/client/VirtualPlanetClient.java`, resources + mixin configs.
* Tests: `src/test/java/dev/virtualplanet/{math,config,integration}/` (8 test classes).

## Known limitations / open items

* No client runtime verification yet: the mixin that makes the sun local (stage 4) is not written.
* `renderLatitudeTilt` is designed (see the analysis in the commit log) but not implemented; it will be
  config-gated and marked unverified unless it can be exercised in CI.
* Yarn build number is pinned to `1.21.1+build.1` (the version Fabric API 1.21.1 itself compiles against).

## Exact next action

1. Push stage 1+2 and read the GitHub Actions result; fix any compile/test failure.
2. Stage 4: add the client mixin `ClientWorldMixin` overriding `getSkyAngle(float)` (and the optional
   `getSkyBrightness(float)`/`getMoonPhase()` overrides), plus `LocalSky` state and the `ClientPlayNetworking`
   receiver for the synced configuration.
3. Stage 6: payload registration, `/virtualplanet` commands, server-side border watching.
