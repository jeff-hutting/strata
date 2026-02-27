# strata-world Phase 1 Review

> **Date:** 2026-02-26
> **Reviewed against:** docs/ARCHITECTURE.md, docs/mods/strata-world/SPEC.md
> **Scope:** Naming conventions, biome pipeline, noise parameters, Fabric API
> usage, cross-module deps, fabric.mod.json, Javadoc, acceptance criteria

---

## 1. Naming Conventions

Passed. Mod ID `strata_world`, package `io.strata.world`, and class names
(`StrataWorld`, `StrataBiomes`, `WorldConfig`) all conform to ARCHITECTURE.md
Section 5.

---

## 2. Biome Pipeline Integrity

### StrataWorldEvents.java:14-16

**Issue:** The spec (Section 3.1) defines the pipeline as
JSON -> StrataBiomes -> StrataWorldgen -> StrataWorldEvents -> overworld.
In the implementation `StrataWorldEvents.initialize()` is a no-op.
`VanillaBiomeParametersMixin` calls `StrataWorldgen.addOverworldBiomes()`
directly, bypassing `StrataWorldEvents` entirely.

**Fix:** Make `StrataWorldEvents` the orchestrator of biome injection. Move the
`addOverworldBiomes` call site so that `StrataWorldEvents` wires the Mixin
callback to `StrataWorldgen`, preserving the spec's documented pipeline order.
At minimum, the Mixin should delegate to `StrataWorldEvents`, which then
delegates to `StrataWorldgen`.

---

## 3. Noise Parameters Hardcoded

### StrataWorldgen.java:55-60

**Issue:** The six multi-noise placement values for VerdantHighlands
(temperature, humidity, continentalness, erosion, depth, weirdness) are
hardcoded as float literals. The spec (Section 5) states: "All world generation
tuning values live in `WorldConfig`. Nothing is hardcoded."

**Fix:** Add fields to `WorldConfig` for each noise parameter with the current
values as defaults. Read them in `StrataWorldgen.addVerdantHighlands()` instead
of using literals.

---

## 4. Fabric API Usage

### VanillaBiomeParametersMixin.java:16-24

**Issue:** Biome injection uses a Mixin on vanilla's `VanillaBiomeParameters`
class. The review criteria require injection through Fabric's
`BiomeModifications` API with no direct access to vanilla internals. Fabric has
no built-in overworld biome injection API (unlike Nether/End), which is why the
Mixin was chosen, but this makes it a breakage point on MC version bumps.

**Fix:** If no Fabric API alternative exists, keep the Mixin but add a block
comment at the top of `VanillaBiomeParametersMixin.java` documenting: (1) why
the Mixin is necessary, (2) the vanilla method signature it targets, and
(3) what to check on MC version bumps. This satisfies ARCHITECTURE.md Section 7
which permits Mixins as a fallback when they are "minimal and well-documented."

---

## 5. Cross-Module Dependencies

Passed. All cross-module imports target `io.strata.core` only.

---

## 6. fabric.mod.json Dependency

Passed. `fabric.mod.json:22` declares `"strata_core": "*"` in the `depends`
block.

---

## 7. Javadoc Coverage

### StrataBiomes.java:10

**Issue:** Public field `VERDANT_HIGHLANDS` has no Javadoc.

**Fix:** Add Javadoc describing the biome (e.g. "Registry key for the Verdant
Highlands biome — rolling mid-elevation hills with dense deciduous forest.").

### StrataBiomes.java:18

**Issue:** Public method `count()` has no Javadoc.

**Fix:** Add Javadoc: "Returns the number of registered Strata biomes."

### StrataBiomes.java:22

**Issue:** Public method `initialize()` has no Javadoc. An inline comment
exists but is not Javadoc format.

**Fix:** Convert to Javadoc: "Triggers class loading to register all Strata
biome registry keys. Called during mod initialization."

### StrataWorldgen.java:67

**Issue:** Public method `initialize()` has no Javadoc.

**Fix:** Add Javadoc: "Triggers class loading for the worldgen system. Biome
injection is performed by the Mixin at world-generation time."

---

## 8. Acceptance Criteria

### WorldConfig.java:19

**Issue:** The `generateInExistingWorlds` field is declared but never read by
any code. Users see a config toggle that has no effect.

**Fix:** Either implement the gating logic (skip biome injection when the config
is false and the world already has generated chunks) or remove the field until
it can be implemented. Dead config is worse than no config.

---

## Summary

| # | Severity | Location | Short description |
|---|---|---|---|
| 1 | Medium | `StrataWorldEvents.java:14-16` | Pipeline bypasses StrataWorldEvents |
| 2 | High | `StrataWorldgen.java:55-60` | Noise parameters hardcoded, not in WorldConfig |
| 3 | Medium | `VanillaBiomeParametersMixin.java:16-24` | Mixin on vanilla internals, needs documented rationale |
| 4 | Low | `StrataBiomes.java:10` | Missing Javadoc on VERDANT_HIGHLANDS |
| 5 | Low | `StrataBiomes.java:18` | Missing Javadoc on count() |
| 6 | Low | `StrataBiomes.java:22` | Missing Javadoc on initialize() |
| 7 | Low | `StrataWorldgen.java:67` | Missing Javadoc on initialize() |
| 8 | Low | `WorldConfig.java:19` | generateInExistingWorlds declared but unused |
