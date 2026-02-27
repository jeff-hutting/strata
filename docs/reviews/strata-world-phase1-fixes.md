# strata-world Phase 1 — Review Fixes

> **Date:** 2026-02-26
> **Fixes for:** docs/reviews/strata-world-phase1-review.md

---

### strata-world/src/main/java/io/strata/world/worldgen/StrataWorldEvents.java
**Changed:** Added `onOverworldBiomeParameters()` as the entry point for biome injection. The Mixin previously called `StrataWorldgen.addOverworldBiomes()` directly, bypassing this class entirely. The new method delegates to `StrataWorldgen`, restoring the pipeline order documented in SPEC.md Section 3.1: JSON → StrataBiomes → StrataWorldgen → StrataWorldEvents → overworld.

---

### strata-world/src/main/java/io/strata/world/mixin/VanillaBiomeParametersMixin.java
**Changed:** Two fixes applied. (1) The injection call was updated from `StrataWorldgen.addOverworldBiomes()` to `StrataWorldEvents.onOverworldBiomeParameters()` so the Mixin routes through the correct orchestrator. (2) Added a block comment documenting why the Mixin is necessary (Fabric has no native overworld biome injection API), the exact vanilla method signature it targets, and what to verify on MC version bumps — satisfying ARCHITECTURE.md Section 7's requirement that Mixins be "minimal and well-documented."

---

### strata-world/src/main/java/io/strata/world/config/WorldConfig.java
**Changed:** Two fixes applied. (1) Added six config fields (`verdantHighlandsTemperature`, `verdantHighlandsHumidity`, `verdantHighlandsContinentalness`, `verdantHighlandsErosion`, `verdantHighlandsDepth`, `verdantHighlandsWeirdness`) with the previously hardcoded values as defaults, satisfying SPEC.md Section 5's requirement that all world generation tuning values live in `WorldConfig`. (2) Removed the `generateInExistingWorlds` field — it was declared but never read by any code, making it a config toggle with no effect. Removed rather than left as dead config.

---

### strata-world/src/main/java/io/strata/world/worldgen/StrataWorldgen.java
**Changed:** Two fixes applied. (1) `addVerdantHighlands()` now reads the six multi-noise point values from `WorldConfig` via `StrataConfigHelper.get()` instead of using float literals, completing the hardcoded-parameters fix. (2) Added Javadoc to `initialize()` describing its role: triggering class loading while noting that actual biome injection happens via the Mixin at world-generation time.

---

### strata-world/src/main/java/io/strata/world/biome/StrataBiomes.java
**Changed:** Added Javadoc to three previously undocumented public members. `VERDANT_HIGHLANDS` now documents the biome concept ("rolling mid-elevation hills with dense deciduous forest"). `count()` documents its return value. `initialize()` had its inline comment converted to Javadoc describing its purpose (triggering class loading to register registry keys).
