# Development status - Virtual Planet: Local Skies

> Authoritative progress record. A fresh session must be able to resume from this file alone.

Last updated: first green compile diagnosed - see "CI history" below.

## Environment facts (important)

* The development sandbox has **no JDK with `javac`, no Gradle distribution and no access to Maven hosts**
  (`maven.fabricmc.net`, `libraries.minecraft.net`, `services.gradle.org`, `meta.fabricmc.net` are
  TLS-blocked). Local builds are therefore impossible.
* Consequences:
  * Compilation and the JUnit run happen in **GitHub Actions** (`.github/workflows/build.yml`).
  * Locally the sources are checked with two Python gates (see "Local gates" below).
  * Do not claim local compilation. Do not claim runtime testing unless it happened in CI.
* **Reading CI results from the sandbox:** the raw job log endpoint and the artifact/blob endpoint are
  unreachable (`EOF`) and `gh run download` fails for the same reason. Therefore the workflow, on failure,
  (a) prints the useful lines as check-run **annotations**
  (`gh api /repos/benluvzbacon/realistic-minecraft-2/check-runs/<id>/annotations`) and
  (b) **commits** `.ci-logs/summary.txt`, the raw `gradle-*.log` files and the JUnit XML back to the branch
  with a `[skip ci]` message. Read them with `git fetch && git show <sha>:.ci-logs/summary.txt`.
  Remove `.ci-logs/` once the build is green.

## Local gates

```
/tmp/gate/bin/python tools/check_java_syntax.py   src/main/java src/client/java src/test/java   # parses (javalang)
/tmp/gate/bin/python tools/check_java_symbols.py  src/main/java src/client/java src/test/java   # our own API resolved
```

`check_java_symbols.py` resolves every method/constructor/field access on classes of this project
(inheritance included) and every unqualified identifier, ignoring external types. It caught the real CI
failure locally after the fact (see below). Recreate the toolchain with
`python3 -m venv /tmp/gate && /tmp/gate/bin/pip install javalang` (PyPI is reachable).

## Stage status

| Stage | Description | Status |
| --- | --- | --- |
| 1 | Project foundation (Gradle, Loom, metadata, CI workflow) | BUILD VERIFIED for `:compileJava`; full build pending the test compile fix pushed with this file |
| 2 | Planet mathematics + configuration + unit tests | IMPLEMENTED; tests have never run yet (see CI history) |
| 3 | Client rendering investigation | IMPLEMENTED (research complete, see "Verified 1.21.1 facts") |
| 4 | Local sun implementation (client mixin) | IN PROGRESS (sky model written, mixin not yet) |
| 5 | Twilight, moon, seasons, atmosphere refinements | IN PROGRESS (twilight/moon/season math written in `sky/` + `math/`, not wired to rendering) |
| 6 | Multiplayer sync, commands, config validation | NOT STARTED |
| 7 | Full test pass and bug fixing | NOT STARTED |
| 8 | Final jar, documentation, artifact upload | NOT STARTED |

## CI history

| Run | Commit | Result |
| --- | --- | --- |
| 38260558063 | `35378b6` | FAILED at `run unit tests`. Cause was unknown: logs unreachable, the old workflow published empty diagnostics. |
| 38260878490 | `1fad92e` | FAILED the same way; annotations contained only Gradle stack frames. |
| 38261363107 | `0ea660c` | FAILED, now with useful diagnostics: `:compileJava` **succeeded** (Loom 1.6.11, yarn `1.21.1+build.1`, loader 0.15.11, Fabric API 0.116.17+1.21.1 all resolved); `:compileTestJava` failed with one error - `MultiPlayerScenarioTest.java:116: cannot find symbol: variable halfWayAround` (typo for `halfWayRound`). Fixed. |

## Verified 1.21.1 facts the rendering work relies on

These were verified from the Yarn `1.21.1` mappings tree and from decompiled 1.21.1 sources, not guessed:

* `net.minecraft.world.LunarWorldView#getSkyAngle(float)` is an interface **default** method:
  `return this.getDimension().getSkyAngle(this.getLunarTime());` - it ignores `tickDelta`. There is no
  concrete implementation to inject into, so stage 4 overrides/redirects instead (see below).
* `net.minecraft.world.World#getSkyAngleRadians(float)` = `getSkyAngle(tickDelta) * 2*PI`.
* `net.minecraft.world.dimension.DimensionType#getSkyAngle(long)`:
  `d = fractionalPart(time/24000 - 0.25); e = 0.5 - cos(d*PI)/2; return (d*2 + e)/3`.
* `DimensionType#getMoonPhase(long)` = `(int)(time/24000L % 8L + 8L) % 8`, `MOON_SIZES = {1.0, 0.75, 0.5, 0.25, 0.0, 0.25, 0.5, 0.75}`.
* `net.minecraft.client.world.ClientWorld#getSkyBrightness(float)` uses `this.getSkyAngle(tickDelta)`,
  and `LightmapTextureManager` uses `getSkyBrightness`, so overriding the sky angle also affects the
  client light map (this is what `localSkyBrightness` toggles).
* The vanilla renderer (`WorldRenderer#renderSky(Matrix4f, Matrix4f, float, Camera, boolean, Runnable)`,
  verified signature) applies `rotateY(-90)` then `rotateX(skyAngle*360)` and draws the sun at `+Y` of that
  frame: an east-west great circle through the zenith. The moon is at `-Y` and the stars share the matrix,
  and `DimensionEffects#getFogColorOverride(skyAngle, tickDelta)` supplies the sunrise/sunset fog colour -
  all of them therefore follow the sky angle automatically. The single rotation call site is
  `WorldRenderer.java:1561`.
* `World#getWorldBorder()` is callable (`CollisionView#method_8621`, field `World#border`).
* Precedent for the chosen hook: `nanorover59/Starflight-Innovation` adds
  `@Mixin(World.class) + @Override public float getSkyAngle(float)`.

## Files

* Build system: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradle/wrapper/*`,
  `.github/workflows/build.yml`.
* Local gates: `tools/check_java_syntax.py`, `tools/check_java_symbols.py`.
* Pure math (no Minecraft dependency, unit tested): `src/main/java/dev/virtualplanet/math/`
  (`Angles`, `LatitudeAxis`, `PlanetGeometry`, `PlanetTime`, `SolarCalculator`, `SolarPosition`).
* Local sky model (no Minecraft dependency): `src/main/java/dev/virtualplanet/sky/`
  (`LocalSkyModel`, `LocalSkyView`) - the single place where the mod's astronomy is computed, shared by the
  client mixin, the commands and the tests. **No tests written for it yet.**
* Configuration (pure, unit tested): `src/main/java/dev/virtualplanet/config/`
  (`PlanetConfig`, `PlanetConfigJson`, `PlanetConfigBinary`, `PlanetConfigManager`, `SimpleJson`).
* Mod glue: `src/main/java/dev/virtualplanet/VirtualPlanet.java`,
  `src/client/java/dev/virtualplanet/client/VirtualPlanetClient.java`, resources + mixin configs
  (both mixin JSONs still declare **empty** mixin lists).
* Tests: `src/test/java/dev/virtualplanet/{math,config,integration}/` (8 test classes).

## Known limitations / open items

* No client runtime verification: the mixin that makes the sun local (stage 4) is not written.
* `renderLatitudeTilt` is designed but not implemented; it will be config-gated and marked unverified.
* Yarn is pinned to `1.21.1+build.1` - confirmed correct in CI (the dependency resolved).

## Exact next action

1. Read the result of the run for this commit; the expectation is a green test run plus
   `build/libs/virtualplanet-1.0.0.jar`. If it fails, read `.ci-logs/summary.txt` (see above).
2. Stage 4: `src/client/java/dev/virtualplanet/client/mixin/ClientWorldMixin.java` adding
   `@Override public float getSkyAngle(float)` on `World`/`ClientWorld` that returns
   `LocalSkyModel.view(...)` (one cached view per tick), plus the `fabric.mod.json` entry, the client mixin
   JSON entry, the `ClientPlayNetworking` configuration receiver and the payload classes.
3. Stage 6: `PayloadTypeRegistry` registration, server-side sender + world-border watcher, and
   `/virtualplanet info|recalculate|reload|debug` (read the v2 `CommandRegistrationCallback` signature first).
