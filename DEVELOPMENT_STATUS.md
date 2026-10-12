# Development status - Virtual Planet: Local Skies

> Authoritative progress record. A fresh session must be able to resume from this file alone.

Last updated: all stages implemented; CI green (compile, tests, jar build and artifact upload).

## Environment facts (important)

* The development sandbox has **no `javac`, no Gradle distribution and no access to Maven hosts**
  (`maven.fabricmc.net`, `libraries.minecraft.net`, `services.gradle.org`, `meta.fabricmc.net` are
  TLS-blocked). Local builds are impossible; a JRE-only JDK is used for reference work.
* Therefore **CI is the compiler and the test runner** (`.github/workflows/build.yml`), and it is also the
  only source of build/test evidence. GitHub's raw log endpoint and artifact/blob endpoints are
  unreachable from the sandbox (`EOF`), so CI publishes what a reader needs elsewhere:
  * check-run annotations - read with
    `gh api /repos/benluvzbacon/realistic-minecraft-2/check-runs/<check_run_id>/annotations`
    (the workflow emits the interesting Gradle lines, including a `Gradle summary` notice);
  * the logs and the JUnit XML as artifacts (`gh run download` fails here, but the workflow is standard);
  * on failure, a comment on the pull request containing the same extract.
* Do not claim local compilation, and do not claim runtime testing: **no Minecraft client exists here**.

## Local gates (use these before every push)

```
/tmp/gate/bin/python tools/check_java_syntax.py   src/main/java src/client/java src/test/java
/tmp/gate/bin/python tools/check_java_symbols.py  src/main/java src/client/java src/test/java
```

The first parses every file with javalang (a Java-8 grammar: keep sources free of records/text blocks/
switch expressions). The second resolves every method, constructor and field access on this project's own
classes and every bare identifier - it caught the real CI failure locally after the fact.
Recreate the toolchain with `python3 -m venv /tmp/gate && /tmp/gate/bin/pip install javalang` (PyPI works).

## Stage status

| Stage | Description | Status |
| --- | --- | --- |
| 1 | Project foundation (Gradle wrapper, Loom, metadata, mixin configs, CI, README) | BUILD VERIFIED |
| 2 | Planet mathematics, configuration, unit tests | TEST VERIFIED |
| 3 | Investigation of the 1.21.1 sky rendering | IMPLEMENTED (see the verified facts below) |
| 4 | Local sun in the client (per-player celestial phase) | IMPLEMENTED, BUILD VERIFIED (code compiles in CI); **runtime UNVERIFIED** - no client available |
| 5 | Twilight, moon, seasons, atmosphere refinements | IMPLEMENTED: local moon phase and local light map are wired; twilight/declination/seasons are computed and reported; the *rendered* arc stays vanilla's great circle (documented limitation) |
| 6 | Multiplayer sync, commands, validation | IMPLEMENTED, BUILD VERIFIED: payload registration, join sync, once-per-second border watch, `/virtualplanet info|debug|recalculate|reload` |
| 7 | Full test pass and bug fixing | TEST VERIFIED: the whole suite runs green in CI (four wrong test expectations and one compile error were found and fixed this way) |
| 8 | Final jar, artifact, documentation | BUILD VERIFIED: `build/libs/virtualplanet-1.0.0.jar` is built, existence-checked and uploaded by CI |

## CI history

| Run | Commit | Result |
| --- | --- | --- |
| 38260558063 | `35378b6` | FAILED at `run unit tests`; cause unknown at the time (diagnostics were empty, logs unreachable). |
| 38261363107 | `0ea660c` | `:compileJava` succeeded (Loom 1.6.11, Yarn `1.21.1+build.1`, loader 0.15.11, Fabric API 0.116.17+1.21.1 all resolved); one test-compile error (`halfWayAround` typo). Fixed. |
| 38261735253 | `17baf00` | 62 tests, 4 failed - all four were wrong expectations in the tests. Fixed. |
| 38262122275 | `59b5da4` | 62 tests, 1 failed - one replacement assertion used 4.022 instead of 4.0151 degrees. Fixed. |
| 38262436181 | `895a366` | **SUCCESS**: compile, tests, jar build, jar verification and artifact upload all green. |
| 38262878258 / 38262886433 | `91f14d7` | **SUCCESS**: the client mixin, the networking layer and the new sky-model tests all compile and pass; jar uploaded. |
| 38263905964 / 38263912671 | `e5dafb0` | **SUCCESS** on the final tree (dead config flag removed, workflow and docs finalised): compile, tests, jar build, jar verification and artifact upload all green. Artifacts: `virtualplanet-mod-jar` (53,157 bytes), `test-results`, `gradle-logs`. The workflow publishes a `JUnit totals` annotation with the exact test count. |
| 38264423328 / 38264429439 | `7b7ea60` | **SUCCESS**, and the annotation reports **74 tests, 0 failures** - the whole suite, including the twelve sky-model tests, runs green. This is the verified state of the delivered tree. |

## Verified 1.21.1 facts the implementation relies on

Verified from the Yarn `1.21.1` mappings and from decompiled 1.21.1 sources - not guessed:

* `LunarWorldView#getSkyAngle(float)` is an interface **default** method
  (`getDimension().getSkyAngle(getLunarTime())`, tick delta ignored); `getSkyAngle` has no concrete
  declaration in `World`/`ClientWorld`, which is why stage 4 *adds* the method instead of injecting.
* `LunarWorldView#getMoonSize()` and `#getMoonPhase()` are defaults derived from `getLunarTime()`, so
  overriding the phase moves and resizes the moon.
* `WorldAccess#getLunarTime()` = `getLevelProperties().getTimeOfDay()` = `World#getTimeOfDay()`, so the
  mod's shared clock is exactly vanilla's clock.
* `DimensionType#getSkyAngle(long)`: `d = frac(time/24000 - 0.25); e = 0.5 - cos(d*PI)/2; return (d*2+e)/3`.
* `DimensionType#getMoonPhase(long)` = `(int)(time/24000L % 8L + 8L) % 8`; `MOON_SIZES` as implemented.
* `ClientWorld#getSkyBrightness(float)` (verified body) is used by the light map; it reads
  `getSkyAngle(tickDelta)`, and so do `getSkyColor`, the cloud colour and the fog colour - one override
  makes all of them local.
* `WorldRenderer#renderSky(Matrix4f, Matrix4f, float, Camera, boolean, Runnable)` applies one
  `rotateY(-90)` + `rotateX(skyAngle * 360)` and draws the sun at `+Y`, the moon at `-Y`, stars in the
  same frame (Fabric API's own mixin targets the same descriptor).
* `World#getWorldBorder()` (public method, field `border`), `WorldBorder#getCenterX/getCenterZ/getSize`,
  `Entity#getX/getZ`, `MinecraftClient#getInstance/getCameraEntity/player`,
  `ServerPlayConnectionEvents.JOIN`, `ServerTickEvents.END_SERVER_TICK`, `PayloadTypeRegistry.playS2C()`,
  `ClientPlayNetworking.registerGlobalReceiver`, `CustomPayload.codecOf` (value first),
  `CommandRegistrationCallback.EVENT` (v2), `CommandManager.literal`, `ServerCommandSource.getPlayer`,
  `sendFeedback(Supplier, boolean)`, `hasPermissionLevel(int)` - each checked against the mappings.

## Files

* Build: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradle/wrapper/*`,
  `.github/workflows/build.yml`, `tools/check_java_syntax.py`, `tools/check_java_symbols.py`.
* Pure math (unit tested): `src/main/java/dev/virtualplanet/math/` - `Angles`, `LatitudeAxis`,
  `PlanetGeometry`, `PlanetTime`, `SolarCalculator`, `SolarPosition`.
* Sky model (unit tested, shared by client and commands): `src/main/java/dev/virtualplanet/sky/` -
  `LocalSkyModel`, `LocalSkyView`.
* Configuration (unit tested): `src/main/java/dev/virtualplanet/config/` - `PlanetConfig`, `PlanetConfigJson`,
  `PlanetConfigBinary`, `PlanetConfigManager`, `SimpleJson`.
* Networking: `src/main/java/dev/virtualplanet/net/` - `PlanetConfigPayload`, `PlanetConfigSync`.
* Commands: `src/main/java/dev/virtualplanet/command/VirtualPlanetCommand.java`.
* Client: `src/client/java/dev/virtualplanet/client/LocalSky.java` (viewer position + world border +
  shared time, one cached view per tick) and `.../client/mixin/ClientWorldMixin.java`.
* Entry points: `VirtualPlanet` (main), `VirtualPlanetClient` (client receiver).
* Tests: `src/test/java/dev/virtualplanet/{math,config,integration,sky}/`.

## Known limitations / open items

* **No runtime verification.** The mixin, the payload and the commands have never run inside Minecraft.
  Everything runtime-facing is marked UNVERIFIED in the README. The most valuable next step, if a client
  ever becomes available: launch `./gradlew runClient`, join a world, place two players at longitudes 0
  and half a circumference and compare the sky.
* The rendered sun path stays vanilla's east-west great circle. Latitude and declination shape the
  *reported* astronomy, the moon phase and the light map, but the drawn arc is not tilted: doing that
  would mean injecting into `WorldRenderer#renderSky`'s matrix calls, which cannot be runtime-verified
  here and would risk the whole mod for a cosmetic option. Removed rather than shipped as a dead flag.
* Shader packs that replace sky rendering may ignore the phase; nothing is promised about Iris/Optuna.
* Per-player block lighting/shadows are explicitly **not** claimed.

## Exact next action

1. Confirm the run for the current commit is green (annotations carry a `Gradle summary` notice with the
   test count) and that the `virtualplanet-mod-jar` artifact exists.
2. If a Minecraft client ever becomes available: `./gradlew runClient`, then verify in game:
   `/virtualplanet info` at two longitudes, the sun position at both, and that `/time query daytime`
   never changes. Record the result here and in the README ("runtime verified").
3. Optional, only with a runtime to test against: a `WorldRenderer` mixin for the celestial tilt, and
   Sodium/Iris compatibility checks.
