# strata-world — Module Specification

> **Status:** Phase 1 complete — compiles cleanly, 39/40 JUnit tests pass (1 skipped pending GameTest)
> **Build Priority:** #2 — depends on `strata-core`
> **Implements:** Phase 2 of the development roadmap

---

## 1. Purpose

`strata-world` adds custom biomes and terrain to Minecraft's overworld. It is the layer of Strata that shapes the land players explore — distinct regions with their own look, feel, vegetation, mobs, and atmosphere.

This module owns no RPG mechanics, structures, or gameplay systems. Its sole concern is the world itself: what the terrain looks like and what naturally spawns in it.

---

## 2. Core Design Decisions

### 2.1 Native Fabric Worldgen — No TerraBlender

`strata-world` uses Fabric's native worldgen API and Minecraft's data-driven biome system directly. It does **not** depend on TerraBlender or any other third-party worldgen library.

**Rationale:** Strata's core philosophy is owning the stack. TerraBlender is well-maintained today but introduces an external dependency that could lag behind MC releases. Fabric's native API has been stable since 1.18 and is the approach that best serves the "updates as a unit" goal.

The trade-off is more complex biome placement code. This complexity is worth it.

### 2.2 JSON-First Biome Definitions

Every Strata biome is defined primarily as a JSON file under `data/strata_world/worldgen/biome/`. The Java code handles registration, noise placement, and feature injection — the actual biome properties (spawns, colors, sounds, carvers) live in data files.

This means:
- Biome properties can be changed without recompilation
- Biome JSON files follow vanilla's exact format (no custom parser)
- Biome content updates survive MC version bumps more easily than code-based biomes

**Features are the exception.** Inline feature lists in biome JSON impose topological ordering constraints on Minecraft's worldgen graph. When a Strata biome shares vanilla placed features with existing biomes, declaring them inline at different positions produces a `"Feature order cycle"` crash on world load. Features are therefore registered via `BiomeModifications.addFeature()` in `StrataWorldFeatures`, which appends to vanilla's existing ordering graph rather than adding new constraints. Strata biome JSON files keep their `features` arrays empty.

### 2.3 Additive, Not Replacing

`strata-world` adds new biomes to the overworld. It does **not** remove or replace vanilla biomes. Players will encounter both vanilla and Strata biomes on the same map.

Biome density and placement are configurable via `WorldConfig` so server admins can tune how frequently Strata biomes appear.

---

## 3. System Architecture

### 3.1 Biome Definition Pipeline

The full pipeline for a Strata biome, from data to world:

```
data/strata_world/worldgen/biome/<name>.json     ← Biome properties (spawns, colors, carvers)
         ↓
StrataBiomes.java                                ← Registers the BiomeKey in the registry
         ↓
StrataWorldgen.java                              ← Defines multi-noise placement parameters
         ↓
StrataWorldEvents.java                           ← Injects biome into overworld via Fabric API
         ↓                                          + calls StrataWorldFeatures.initialize()
StrataWorldFeatures.java                         ← Registers placed features via BiomeModifications
         ↓
In-game overworld                                ← Players encounter the biome
```

### 3.2 Biome JSON Format

Each biome JSON follows vanilla's format exactly. Claude Code should reference the vanilla biome JSONs (e.g., `minecraft:forest`) as structural templates. Key sections:

```json
{
  "precipitation": "rain",
  "temperature": 0.7,
  "downfall": 0.8,
  "effects": {
    "sky_color": 7972607,
    "fog_color": 12638463,
    "water_color": 4159204,
    "water_fog_color": 329011,
    "grass_color": 5960044,
    "foliage_color": 4764952,
    "mood_sound": {
      "sound": "minecraft:ambient.cave",
      "tick_delay": 6000,
      "block_search_extent": 8,
      "offset": 2.0
    }
  },
  "spawners": { ... },
  "spawn_costs": {},
  "carvers": { ... },
  "features": [ ... ]
}
```

Colors are expressed as decimal integers. Use an RGB-to-decimal converter when speccing new biomes. Features reference vanilla or Strata placed feature keys.

### 3.3 Biome Registration (`StrataBiomes`)

```java
// io.strata.world.biome.StrataBiomes
public final class StrataBiomes {

    public static final RegistryKey<Biome> VERDANT_HIGHLANDS = register("verdant_highlands");
    // Add more biomes here as they are implemented

    private static RegistryKey<Biome> register(String name) {
        return RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of("strata_world", name));  // MC 1.21+: use Identifier.of(), not new Identifier()
    }

    public static void initialize() {
        // No-op: keys are registered lazily via the data files.
        // This method exists to trigger class loading during mod init.
    }
}
```

### 3.4 Multi-Noise Placement (`StrataWorldgen`)

Minecraft's overworld uses a "multi-noise" biome placement system with six parameters: temperature, humidity, continentalness, erosion, weirdness, and depth. Each biome occupies a **point** in this 6D parameter space (not a range — vanilla uses point-based nearest-neighbor selection).

`StrataWorldgen.addOverworldBiomes()` is called by `StrataWorldEvents.onOverworldBiomeParameters()`, which is in turn invoked by the Mixin (see Section 3.6). It reads noise-point values from `WorldConfig` so they can be tuned without recompilation:

```java
// io.strata.world.worldgen.StrataWorldgen
public final class StrataWorldgen {

    /**
     * Called by VanillaBiomeParametersMixin at the tail of writeOverworldBiomeParameters.
     * Adds all Strata biomes to the overworld noise parameter list.
     */
    public static void addOverworldBiomes(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters) {

        WorldConfig config = StrataConfigHelper.get(WorldConfig.class);
        if (!config.enabled) return;

        // Offset controls rarity: higher offset = biome is less likely to win ties.
        // Base offset 0.375 is conservative. biomeFrequency adjusts it inversely.
        float baseOffset = 0.375f;
        float adjustedOffset = baseOffset / Math.max(config.biomeFrequency, 0.1f);

        addVerdantHighlands(parameters, adjustedOffset);
    }

    /** Triggers class loading for the worldgen system. Biome injection happens via Mixin. */
    public static void initialize() {}
}
```

**Important:** Multi-noise parameter values require careful tuning. The implementation uses **point values** (not ranges) passed to `MultiNoiseUtil.createNoiseHypercube()`. Start conservative — a high offset (0.375+) makes the biome rare but present. Widen by lowering the offset. The six noise points are now in `WorldConfig` and can be tuned at runtime.

### 3.5 Worldgen Events (`StrataWorldEvents`)

`StrataWorldEvents` is the single orchestration point for worldgen initialization, matching the pipeline in Section 3.1.

Fabric's `BiomeModifications` API cannot inject *new* biomes into the overworld's multi-noise list — it only supports modifying existing biomes. The actual biome injection entry point is `onOverworldBiomeParameters()`, called by the Mixin (see Section 3.6). Feature registration via `BiomeModifications` is handled separately in `StrataWorldFeatures` (see Section 3.7) and is triggered from `initialize()`:

```java
// io.strata.world.worldgen.StrataWorldEvents
public final class StrataWorldEvents {

    /**
     * Called by VanillaBiomeParametersMixin. Delegates to StrataWorldgen
     * to inject all Strata biomes into the overworld noise parameter list.
     */
    public static void onOverworldBiomeParameters(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters) {
        StrataWorldgen.addOverworldBiomes(parameters);
    }

    /**
     * Registers Fabric event listeners and BiomeModifications feature additions.
     * Called during mod initialization; must run before any world is loaded.
     */
    public static void initialize() {
        StrataLogger.debug("StrataWorldEvents initialized.");
        StrataWorldFeatures.initialize();
    }
}
```

Phase 2 will add the `ASSET_REGISTERED` event hook described in Section 7.4.

### 3.7 Feature Registration (`StrataWorldFeatures`)

`StrataWorldFeatures` registers vanilla placed features for Strata biomes using `BiomeModifications.addFeature()`. It is the single source of truth for which features each Strata biome contains.

**Why a separate class?** Biome noise-parameter injection (Mixin-driven) and feature registration (BiomeModifications-driven) are distinct concerns with different timing and API constraints. Keeping them separate makes each easier to extend and reason about.

**Why `BiomeModifications` and not inline JSON?** See Section 2.2. Short answer: inline feature lists cause "Feature order cycle" crashes; `BiomeModifications` does not.

For each biome, `registerVerdantHighlandsFeatures()` (and future per-biome methods) call the private `addFeature()` helper, which constructs the `RegistryKey<PlacedFeature>` and delegates to `BiomeModifications.addFeature()`. All feature IDs are vanilla `minecraft:` namespace identifiers; no Strata-specific placed features exist yet.

### 3.6 Mixin — Overworld Biome Injection (`VanillaBiomeParametersMixin`)

Fabric has no native API for injecting new biomes into the overworld's multi-noise parameter list (unlike `NetherBiomes` / `TheEndBiomes`). The only supported approach is a Mixin on `VanillaBiomeParameters.writeOverworldBiomeParameters` with `@At("TAIL")`.

The Mixin injects at the **tail** — after all vanilla biome entries have been added — then calls `StrataWorldEvents.onOverworldBiomeParameters()`:

```java
@Mixin(VanillaBiomeParameters.class)
public class VanillaBiomeParametersMixin {

    @Inject(method = "writeOverworldBiomeParameters", at = @At("TAIL"))
    private void strata$addOverworldBiomes(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
            CallbackInfo ci) {
        StrataWorldEvents.onOverworldBiomeParameters(parameters);
    }
}
```

**On MC version bumps, verify:**
1. `VanillaBiomeParameters` still exists in the new Yarn mappings.
2. The method signature (`Consumer<Pair<NoiseHypercube, RegistryKey<Biome>>>`, void return) has not changed.
3. If Fabric gains a native overworld biome injection hook, migrate to it and delete this Mixin.

---

## 4. Module Initializer

**Class:** `io.strata.world.StrataWorld`
**Implements:** `net.fabricmc.api.ModInitializer`

```java
public class StrataWorld implements ModInitializer {
    @Override
    public void onInitialize() {
        StrataLogger.info("Initializing strata-world...");
        StrataConfigHelper.register(WorldConfig.class);
        StrataBiomes.initialize();
        StrataWorldgen.initialize();
        StrataWorldEvents.initialize();
        StrataLogger.info("strata-world initialized. {} biomes registered.",
            StrataBiomes.count());
    }
}
```

---

## 5. Configuration (`WorldConfig`)

All world generation tuning values live in `WorldConfig`. Nothing is hardcoded.

```java
// io.strata.world.config.WorldConfig
@Config(name = "strata_world")
public class WorldConfig implements ConfigData {

    @Comment("Master toggle. Set false to disable all Strata biomes entirely.")
    public boolean enabled = true;

    @Comment("Controls how frequently Strata biomes appear relative to vanilla biomes.\n" +
             "Higher values = more Strata biomes. Range: 0.1 - 2.0. Default: 1.0")
    public float biomeFrequency = 1.0f;

    // --- VerdantHighlands multi-noise placement parameters ---
    // Point values in the overworld's 6D noise space. See StrataWorldgen for usage.

    @Comment("VerdantHighlands: temperature noise point. Mild (0.0 = between cold and warm).")
    public float verdantHighlandsTemperature = 0.0f;

    @Comment("VerdantHighlands: humidity noise point. Moderate-to-lush.")
    public float verdantHighlandsHumidity = 0.3f;

    @Comment("VerdantHighlands: continentalness noise point. Inland.")
    public float verdantHighlandsContinentalness = 0.3f;

    @Comment("VerdantHighlands: erosion noise point. Low erosion = rolling hills.")
    public float verdantHighlandsErosion = -0.4f;

    @Comment("VerdantHighlands: depth noise point. 0.0 = surface.")
    public float verdantHighlandsDepth = 0.0f;

    @Comment("VerdantHighlands: weirdness noise point. 0.0 = normal terrain.")
    public float verdantHighlandsWeirdness = 0.0f;
}
```

> **Note:** The `generateInExistingWorlds` field was removed during Phase 1 review — it was declared but never read by any code. Dead config is worse than no config. This feature can be revisited and implemented properly in a future phase.

> **Note:** The `@FloatRange` annotation on `biomeFrequency` was removed — the range is enforced programmatically via `Math.max(config.biomeFrequency, 0.1f)` in `StrataWorldgen.addOverworldBiomes()`.

---

## 6. Phase 1 Biome: Verdant Highlands

The first implemented biome serves as a proof-of-concept for the entire pipeline. It must be simple enough to build quickly but complete enough to validate every step from JSON to in-game.

### Concept
Rolling mid-elevation hills with dense deciduous forest and rich green color palette. Sits between plains and forest in the noise space. Visually distinct from vanilla forest via custom sky and fog tints and unique feature mix.

### Properties

| Property | Value |
|---|---|
| Precipitation | Rain |
| Temperature | 0.65 |
| Downfall | 0.75 |
| Sky color | `#6eb4d4` (soft blue-green) |
| Fog color | `#c8e8d4` (pale green mist) |
| Water color | `#3fa67a` (teal-green) |
| Grass color | `#5a9e3a` (rich green) |
| Foliage color | `#4a8e2a` (deep green) |

### Features (Phase 1 — Minimal)
- Vanilla oak trees (slightly denser than plains, less dense than forest)
- Vanilla birch trees (occasional)
- Vanilla tall grass and ferns (moderate density)
- Vanilla flowers (dandelion, poppy, azure bluet)

*Custom features (unique flora, unique ground cover) are deferred to Phase 2.*

### Mob Spawns
Inherit vanilla forest spawns (wolves, foxes, rabbits, pigs, cows, sheep). No custom mobs in Phase 1.

### Noise Placement
Target region: mid-temperature, mid-humidity, inland (not coastal), low erosion (rolling hills).
Start conservative — narrow ranges that guarantee the biome appears but doesn't dominate.

---

## 7. In-Game Biome Editor

The Biome Editor is a Creative-mode-only tool for designing and refining Strata biomes without leaving the game. It is opened via the **Biome Editor Wand** — a special item obtainable from the creative inventory.

This is a planned system, designed now so the module architecture accommodates it. Implementation follows basic biome registration (Phase 2 of `strata-world`).

### 7.1 Two-Layer Real-Time Editing

Biome properties fall into two categories with different update behavior:

**Layer 1 — Visual Properties (instant, no chunk regen)**
These are client-side rendering properties. Changes are reflected immediately on screen as sliders or color pickers move.
- Sky color, fog color, water color, water fog color
- Grass tint, foliage tint
- Ambient sounds, mood sounds
- Weather type (rain/snow/none)
- Particle effects

**Layer 2 — Structural Properties (queued, triggers preview refresh)**
These affect what generates in the world. Changes queue a 3-second debounced chunk regeneration of a 5×5 chunk preview zone around the player.
- Multi-noise parameters (temperature, humidity, continentalness, erosion, weirdness)
- Feature list and density (which trees, plants, rocks appear)
- Mob spawn entries
- Surface rules (what blocks form the surface and subsurface layers)
- Carver configuration

### 7.2 Preview Zone

When the player opens the Biome Editor Wand UI in a Creative world, a **Preview Zone** is established — a 5×5 chunk area centered on the player. A visual overlay (configurable in `WorldConfig`) marks the boundary of the preview zone.

When structural parameters change:
1. A 3-second debounce timer starts (resets if parameters change again)
2. On expiry, the preview zone chunks are regenerated using the current biome parameters
3. The player sees the terrain reshape in real time within the preview area
4. A HUD indicator shows "Refreshing preview..." during regeneration

The preview zone exists in the normal world — it is not a separate dimension. Server admins should only allow the Biome Editor in creative-mode servers or single-player worlds.

### 7.3 HUD Layout

The Biome Editor UI is a full-screen panel (using Fabric's Screen API) with five tabs:

```
┌─────────────────────────────────────────────────────────────────┐
│  STRATA BIOME EDITOR          [New Biome]  [Load]  [Save]  [X]  │
├──────────┬──────────────────────────────────────────────────────┤
│ VISUAL   │                                                       │
│ TERRAIN  │   [Tab content — see below]                          │
│ FEATURES │                                                       │
│ SPAWNS   │                                                       │
│ EXPORT   │                                                       │
└──────────┴──────────────────────────────────────────────────────┘
```

**Visual Tab** — Color pickers and sliders for all Layer 1 properties. Live preview updates as values change.

**Terrain Tab** — Six sliders for multi-noise parameters, each with a range display. A "Refresh Preview" button and an auto-refresh toggle (on/off). Shows the current biome name detected at player's feet for reference.

**Features Tab** — Two-column layout: left panel lists features currently in the biome; right panel is a searchable picker of all available features. Available features include vanilla placed features AND everything registered in `StrataAssetRegistry`. Filterable by `FeatureCategory`. Each feature shows its display name, category, and source (vanilla/strata-built-in/custom).

**Spawns Tab** — Similar two-column layout for mob spawn entries. Right panel pulls from both vanilla entity types and `StrataAssetRegistry.getAllSpawns()`. Each entry shows weight, min/max group size (editable inline).

**Export Tab** — Shows a preview of the generated biome JSON. Buttons: "Copy to Clipboard", "Save to File" (writes to `saves/<world>/strata_biomes/<name>.json`), "Apply to World" (registers the biome for the current session).

### 7.4 Asset Registry Integration

The Features tab and Spawns tab query `StrataAssetRegistry` (from `strata-core`) for all available custom assets. This is the conduit — the biome editor does not know or care how the assets were made. It only sees what's in the registry.

When `strata-creator` registers a new custom tree via `StrataAssetRegistry.registerFeature()`, it fires the `ASSET_REGISTERED` event. `strata-world` listens to this event and refreshes the Features tab's available list automatically — no restart required.

```java
// In StrataWorldEvents (strata-world):
StrataEvents.ASSET_REGISTERED.register((id, asset) -> {
    if (asset instanceof WorldgenFeature feature) {
        BiomeEditorScreen.notifyFeatureListUpdated();
        StrataLogger.debug("Biome editor: new feature available — {}", id);
    }
});
```

### 7.5 Biome JSON Export Format

The export produces a standard vanilla-compatible biome JSON. Custom asset features are referenced by their `Identifier` in the features array — the worldgen engine knows how to resolve them via `StrataAssetRegistry.getFeature(id)`.

Custom assets in the features array are wrapped in a Strata-specific placed feature type:
```json
{
  "type": "strata_world:custom_asset_feature",
  "asset_id": "strata_creator:my_oak_variant",
  "placement": { ... }
}
```

This is the only non-vanilla JSON field. Everything else in the exported biome JSON is standard vanilla format.

---

## 8. File Structure

```
strata-world/
├── build.gradle
├── src/main/
│   ├── java/io/strata/world/
│   │   ├── StrataWorld.java              ← ModInitializer
│   │   ├── biome/
│   │   │   └── StrataBiomes.java         ← BiomeKey registry
│   │   ├── config/
│   │   │   └── WorldConfig.java          ← World generation config
│   │   ├── editor/                       ← Biome Editor (Phase 2 — not yet implemented)
│   │   │   ├── BiomeEditorItem.java
│   │   │   ├── BiomeEditorScreen.java
│   │   │   ├── BiomeEditorState.java
│   │   │   ├── PreviewZoneManager.java
│   │   │   └── tabs/
│   │   │       ├── VisualTab.java
│   │   │       ├── TerrainTab.java
│   │   │       ├── FeaturesTab.java
│   │   │       ├── SpawnsTab.java
│   │   │       └── ExportTab.java
│   │   ├── mixin/
│   │   │   └── VanillaBiomeParametersMixin.java  ← Injects biomes at TAIL of writeOverworldBiomeParameters
│   │   └── worldgen/
│   │       ├── StrataWorldgen.java       ← Multi-noise placement params + biome injection logic
│   │       ├── StrataWorldEvents.java    ← Orchestration point; calls StrataWorldFeatures + future asset listeners
│   │       ├── StrataWorldFeatures.java  ← BiomeModifications feature registration for all Strata biomes
│   │       └── feature/
│   │           └── CustomAssetFeature.java ← Placed feature type for creator assets (Phase 2)
│   └── resources/
│       ├── fabric.mod.json
│       ├── strata_world.mixins.json      ← Mixin config declaring VanillaBiomeParametersMixin
│       └── data/strata_world/
│           └── worldgen/
│               └── biome/
│                   └── verdant_highlands.json  ← Phase 1 biome
```

---

## 8. Gradle Configuration

### `build.gradle` (strata-world)

```groovy
dependencies {
    // Depends on strata-core for logging, config, and event system
    modImplementation project(':strata-core')
}
```

### `fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "strata_world",
  "version": "${version}",
  "name": "Strata World",
  "description": "Custom biomes and world generation for the Strata ecosystem.",
  "entrypoints": {
    "main": ["io.strata.world.StrataWorld"]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-api": "*",
    "minecraft": "~1.21",
    "strata_core": "*"
  }
}
```

---

## 9. Implementation Notes for Claude Code

- **Check the Fabric API version.** The biome injection API has changed between MC versions. Before writing `StrataWorldEvents`, look up the current recommended approach in the Fabric API source or wiki for the target version. The class/method names may differ from examples in these docs.
- **Multi-noise parameters are sensitive.** Start with narrow ranges for VerdantHighlands. If the biome doesn't appear in a test world, widen the ranges. If it's everywhere, narrow them. Expect to iterate.
- **Decimal color integers.** All color values in biome JSON are decimal integers, not hex. Convert: `#6eb4d4` → R=110, G=180, B=212 → `(110 << 16) | (180 << 8) | 212` = `7255252`.
- **Feature references.** Biome JSON feature arrays reference placed feature keys, not feature JSONs directly. Reference vanilla biome JSON files to understand the nesting structure before writing Strata biome JSON.
- **Run `runClient` and fly around** after implementing VerdantHighlands. Use the F3 screen to confirm the biome name appears. Don't declare Phase 1 complete from a build alone.

---

## 10. Acceptance Criteria

`strata-world` Phase 1 is complete when:

- [x] `./gradlew :strata-world:build` compiles with zero errors
- [ ] `runClient` launches successfully with both `strata-core` and `strata-world` loaded — *deferred to GameTest*
- [x] Log shows "strata-world initialized. 1 biomes registered." — verified by `StrataBiomesTest`
- [ ] Creating a new world and flying around eventually reveals a biome named `strata_world:verdant_highlands` in the F3 overlay — *deferred to GameTest*
- [x] VerdantHighlands has the correct sky color, fog color, and water color (visually distinct from vanilla forest) — verified by `BiomeJsonValidationTest`
- [x] VerdantHighlands has trees, grass, and flowers generating correctly — verified by `BiomeJsonValidationTest`
- [x] Setting `biomeFrequency = 0.1` in WorldConfig noticeably reduces Strata biome occurrence — verified by `BiomeFrequencyOffsetTest`
- [ ] Setting `enabled = false` in WorldConfig causes no Strata biomes to generate — config default tested; runtime injection branch requires GameTest
- [ ] No vanilla biomes are removed or noticeably altered — *deferred to GameTest / in-game verification*

**Test summary (Phase 1):** 39 passed, 1 skipped (`StrataWorldEventsTest` — requires AutoConfig fixture), 0 failed. See `docs/reviews/strata-world-phase1-test-report.md` for full details.

---

## 11. Phase Breakdown

### Phase 1 — Complete
- [x] Basic biome registration pipeline (JSON → StrataBiomes → StrataWorldgen → StrataWorldEvents → Mixin → overworld)
- [x] VerdantHighlands proof-of-concept biome (JSON + multi-noise placement)
- [x] WorldConfig (enabled toggle, biomeFrequency, per-biome noise-point overrides)
- [x] JUnit 5 test suite — 39 passed, 1 skipped pending GameTest infrastructure
- [x] VerdantHighlands feature registration via BiomeModifications (vegetation, ores, decoration, springs, lava lakes, monster rooms, freeze layer)

### Phase 2 — Biome Editor MVP
- Biome Editor Wand item
- Full-screen HUD (all five tabs)
- Visual properties: real-time preview
- Structural properties: preview zone with debounced chunk regen
- Feature picker pulling from vanilla + `StrataAssetRegistry`
- Spawn picker pulling from vanilla + `StrataAssetRegistry`
- Export biome JSON to file
- `ASSET_REGISTERED` event listener to refresh editor lists

### Phase 3 — Biome Content Expansion
- Additional biomes (CrimsonBadlands, FrostPeaks, and more)
- Custom surface rules per biome
- Custom placed features unique to Strata biomes
- Biome-specific ambient sounds and music

### Phase 4 — Advanced Worldgen
- Custom terrain noise settings
- Custom dimension(s)
- Integration with `strata-structures` for biome-aware structure placement

### Out of Scope (All Phases)
- Any RPG mechanics (belongs in `strata-rpg`)
- Asset creation (belongs in `strata-creator`)
- Structure building (belongs in `strata-structures`)
