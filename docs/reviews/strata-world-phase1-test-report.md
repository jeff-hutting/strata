# strata-world Phase 1 — Test Report

> **Date:** 2026-02-27
> **Gradle task:** `./gradlew :strata-world:test`
> **Result:** BUILD SUCCESSFUL — 39 passed, 1 skipped, 0 failed

---

## Summary

| Test Class | Tests | Passed | Skipped | Failed |
|---|---|---|---|---|
| `StrataBiomesTest` | 5 | 5 | 0 | 0 |
| `BiomeJsonValidationTest` | 17 | 17 | 0 | 0 |
| `WorldConfigDefaultsTest` | 10 | 10 | 0 | 0 |
| `BiomeFrequencyOffsetTest` | 7 | 7 | 0 | 0 |
| `StrataWorldEventsTest` | 1 | 0 | 1 | 0 |
| **Total** | **40** | **39** | **1** | **0** |

---

## Test Framework Decisions

**JUnit 5** was used for all five test classes. No Fabric GameTest classes were added in Phase 1.

Minecraft data objects (`RegistryKey`, `Identifier`) are plain value types that work on the standard test classpath without a running server. However, any code path that calls `StrataConfigHelper` (which requires AutoConfig to have been registered during mod init) cannot run in a plain JUnit environment. That path is documented below as the single skipped test and flagged for promotion to GameTest in a future phase.

---

## Test Classes

---

### `StrataBiomesTest`
**Location:** `src/test/java/io/strata/world/biome/StrataBiomesTest.java`

**Covers:**
- SPEC §3.3 — `VERDANT_HIGHLANDS` registry key is non-null, uses namespace `strata_world`, path `verdant_highlands`
- SPEC §10 AC — "Log shows strata-world initialized. **1** biomes registered." → `count()` returns `1`
- `initialize()` completes without throwing

**Result:** **5 / 5 passed**

---

### `BiomeJsonValidationTest`
**Location:** `src/test/java/io/strata/world/BiomeJsonValidationTest.java`

**Covers:**
- SPEC §10 AC — "VerdantHighlands has the correct sky color, fog color, and water color (visually distinct from vanilla forest)"
  - `sky_color` = 7255252 (`#6eb4d4` — soft blue-green) ✓
  - `fog_color` = 13166804 (`#c8e8d4` — pale green mist) ✓
  - `water_color` = 4171386 (`#3fa67a` — teal-green) ✓
  - `grass_color` = 5938746 (`#5a9e3a` — rich green) ✓
  - `foliage_color` = 4886058 (`#4a8e2a` — deep green) ✓
- SPEC §10 AC — "VerdantHighlands has trees, grass, and flowers generating correctly"
  - `minecraft:trees_birch_and_oak` present in features ✓
  - `minecraft:patch_grass_forest` present in features ✓
  - `minecraft:flower_default` present in features ✓
  - `minecraft:patch_tall_grass` present in features ✓
- SPEC §6 — `has_precipitation=true`, `temperature=0.65`, `downfall=0.75` ✓
- SPEC §6 — `mood_sound` present (ambient cave audio) ✓
- SPEC §6 — Forest creature spawns include wolves and foxes (vanilla forest spawn inheritance) ✓
- SPEC §2.2 JSON-First — `spawn_costs` is empty object (no custom cost entries in Phase 1) ✓
- Structural: `carvers.air` present, `spawners.monster` and `spawners.creature` present ✓

**Result:** **17 / 17 passed**

---

### `WorldConfigDefaultsTest`
**Location:** `src/test/java/io/strata/world/config/WorldConfigDefaultsTest.java`

**Covers:**
- SPEC §5 / SPEC §10 AC — `enabled` defaults to `true` (biomes generate by default)
- SPEC §5 / SPEC §10 AC — `biomeFrequency` defaults to `1.0f` (neutral scaling)
- Phase 1 review fix (strata-world-phase1-fixes.md) — all six VerdantHighlands multi-noise point fields are present with their specified defaults:
  - `verdantHighlandsTemperature` = `0.0f`
  - `verdantHighlandsHumidity` = `0.3f`
  - `verdantHighlandsContinentalness` = `0.3f`
  - `verdantHighlandsErosion` = `-0.4f`
  - `verdantHighlandsDepth` = `0.0f`
  - `verdantHighlandsWeirdness` = `0.0f`
- Phase 1 review fix — **regression guard**: `generateInExistingWorlds` field was removed (it was declared but never read); test asserts via reflection that it does not re-appear
- All eight expected fields are present on the class

**Result:** **10 / 10 passed**

---

### `BiomeFrequencyOffsetTest`
**Location:** `src/test/java/io/strata/world/worldgen/BiomeFrequencyOffsetTest.java`

**Covers:**
- SPEC §10 AC — "Setting `biomeFrequency = 0.1` noticeably reduces Strata biome occurrence"
  - Formula: `adjustedOffset = 0.375f / Math.max(biomeFrequency, 0.1f)`
  - Lower offset → biome wins more ties → appears more often; higher offset → rarer
- Default `biomeFrequency=1.0` produces base offset `0.375`
- `biomeFrequency=2.0` (maximum) halves the offset to `0.1875` (biome appears twice as often)
- `biomeFrequency=0.1` (minimum) produces offset `3.75` (10× rarer than default)
- `biomeFrequency=0.0` is clamped to `0.1` by `Math.max(freq, 0.1f)` — no division-by-zero
- Negative values are also clamped to the `0.1` floor
- Offset is strictly monotonically decreasing with increasing frequency (verified at three points)
- Offset is always positive for any valid or invalid input

**Result:** **7 / 7 passed**

---

### `StrataWorldEventsTest`
**Location:** `src/test/java/io/strata/world/worldgen/StrataWorldEventsTest.java`

**Covers (intended):**
- SPEC §3.1 pipeline — `StrataWorldEvents.initialize()` is the orchestration entry point;
  it must complete without throwing

**Why skipped:**
`StrataWorldEvents.initialize()` calls `StrataLogger.debug()`. `StrataLogger` routes through `StrataConfigHelper.get(StrataCoreConfig.class)` to resolve the log level, and that call requires `StrataCoreConfig` to have been registered with AutoConfig during mod initialisation. That registration only occurs in a running Minecraft environment. Outside that environment the call throws `RuntimeException: Config 'class io.strata.core.config.StrataCoreConfig' has not been registered`.

**Action required:** Promote this test to a Fabric GameTest (or a dedicated integration test that calls `AutoConfig.register(...)` as fixture setup) before it can be executed. The test code is already written and annotated `@Disabled` so it runs automatically once the fixture issue is resolved.

**Result:** **0 / 1 run — 1 skipped** (by `@Disabled`)

---

## Acceptance Criteria Coverage

| Acceptance Criterion (SPEC §10) | Covered by | Status |
|---|---|---|
| `./gradlew :strata-world:build` compiles with zero errors | Build task (pre-existing) | ✓ Build passes |
| `runClient` launches successfully with both mods loaded | Requires GameTest / manual | — Deferred |
| Log shows "strata-world initialized. 1 biomes registered." | `StrataBiomesTest.countReturnsOne()` | ✓ Tested |
| Biome named `strata_world:verdant_highlands` appears in F3 overlay | Requires GameTest | — Deferred |
| VerdantHighlands has correct sky, fog, water colors | `BiomeJsonValidationTest` (5 color tests) | ✓ Tested |
| VerdantHighlands has trees, grass, and flowers | `BiomeJsonValidationTest` (4 feature tests) | ✓ Tested |
| `biomeFrequency = 0.1` noticeably reduces occurrence | `BiomeFrequencyOffsetTest` (7 tests) | ✓ Tested |
| `enabled = false` causes no Strata biomes to generate | Config default tested; runtime behavior requires GameTest | Partial |
| No vanilla biomes removed or noticeably altered | Requires GameTest / in-game verification | — Deferred |

---

## Deferred to GameTest

The following acceptance criteria require a running Minecraft server and are explicitly out of scope for JUnit 5:

1. **Biome appears in F3 overlay** — requires world generation to complete, which needs a running dimension.
2. **`enabled = false` blocks all biome generation** — the config default is tested, but verifying the `if (!config.enabled) return;` branch actually suppresses injection requires calling `addOverworldBiomes()` with AutoConfig registered.
3. **No vanilla biomes altered** — requires navigating a live overworld and sampling all vanilla biome regions.
4. **`runClient` smoke test** — requires a full game launch.

When GameTest infrastructure is added to `strata-world`, these criteria map naturally to:
- `@GameTest` that creates a world, teleports to a known noise coordinate, and asserts `World.getBiome()` returns `strata_world:verdant_highlands`
- `@GameTest` that sets `WorldConfig.enabled = false` before world generation and asserts the biome never appears
