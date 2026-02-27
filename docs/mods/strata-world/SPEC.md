# strata-world — Module Specification

> **Status:** Ready for implementation
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

Every Strata biome is defined primarily as a JSON file under `data/strata_world/worldgen/biome/`. The Java code handles registration and noise placement — the actual biome properties (features, spawns, colors, sounds) live in data files.

This means:
- Biome properties can be changed without recompilation
- Biome JSON files follow vanilla's exact format (no custom parser)
- Biome content updates survive MC version bumps more easily than code-based biomes

### 2.3 Additive, Not Replacing

`strata-world` adds new biomes to the overworld. It does **not** remove or replace vanilla biomes. Players will encounter both vanilla and Strata biomes on the same map.

Biome density and placement are configurable via `WorldConfig` so server admins can tune how frequently Strata biomes appear.

---

## 3. System Architecture

### 3.1 Biome Definition Pipeline

The full pipeline for a Strata biome, from data to world:

```
data/strata_world/worldgen/biome/<name>.json     ← Biome properties (features, spawns, colors)
         ↓
StrataBiomes.java                                ← Registers the BiomeKey in the registry
         ↓
StrataWorldgen.java                              ← Defines multi-noise placement parameters
         ↓
StrataWorldEvents.java                           ← Injects biome into overworld via Fabric API
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
            new Identifier("strata_world", name));
    }

    public static void initialize() {
        // No-op: keys are registered lazily via the data files.
        // This method exists to trigger class loading during mod init.
    }
}
```

### 3.4 Multi-Noise Placement (`StrataWorldgen`)

Minecraft's overworld uses a "multi-noise" biome placement system with six parameters: temperature, humidity, continentalness, erosion, weirdness, and depth. Each biome occupies a region in this 6D parameter space.

`StrataWorldgen` maps each Strata biome to a set of parameter ranges and injects them via `OverworldBiomeSelectionCallback` (Fabric API):

```java
// io.strata.world.worldgen.StrataWorldgen
public final class StrataWorldgen {

    public static void initialize() {
        BiomeSelectionContext context = ...; // provided by Fabric

        // Register VerdantHighlands in the overworld noise space
        // Parameters chosen to place it near vanilla forest/plains regions
        MultiNoiseUtil.NoiseHypercube verdantHighlandsNoise =
            MultiNoiseUtil.createNoiseHypercube(
                /* temperature */    MultiNoiseUtil.toFloat(-0.1f, 0.3f),
                /* humidity */       MultiNoiseUtil.toFloat(0.3f, 0.7f),
                /* continentalness */MultiNoiseUtil.toFloat(0.1f, 0.5f),
                /* erosion */        MultiNoiseUtil.toFloat(-0.3f, 0.1f),
                /* depth */          MultiNoiseUtil.toFloat(0.0f, 0.0f),
                /* weirdness */      MultiNoiseUtil.toFloat(-0.2f, 0.2f),
                /* offset */         0.0f
            );
        // ... register via Fabric API
    }
}
```

**Important:** Multi-noise parameter values require careful tuning to avoid biomes that never spawn or that completely override vanilla biomes. The first implementation should start conservative (narrow parameter ranges) and widen after in-game testing. Document the chosen values and rationale in the biome's spec file.

### 3.5 Fabric API Injection (`StrataWorldEvents`)

Fabric provides `BiomeModifications` for modifying existing biomes and `OverworldBiomeSelectionCallback` (or equivalent in the current Fabric API version) for injecting new biome entries into the overworld's noise parameters.

```java
// io.strata.world.worldgen.StrataWorldEvents
public final class StrataWorldEvents {

    public static void initialize() {
        // Inject Strata biomes into the overworld's noise-based biome source
        // Check current Fabric API for the exact event/callback name —
        // this API has evolved across Minecraft versions.
    }
}
```

> **Note for Claude Code:** The exact Fabric API method for overworld biome injection has changed across MC versions. Always check the current Fabric API javadoc or the fabricmc.net/develop page for the correct approach for the target MC version. Do not copy patterns from tutorials written for older versions.

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
             "Higher values = more Strata biomes. Range: 0.1 – 2.0. Default: 1.0")
    @FloatRange(from = 0.1f, to = 2.0f)
    public float biomeFrequency = 1.0f;

    @Comment("If true, Strata biomes can generate in existing worlds (not just new ones).\n" +
             "May cause seams at chunk boundaries. Recommended: false for existing worlds.")
    public boolean generateInExistingWorlds = false;
}
```

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
│   │   ├── editor/                       ← Biome Editor (Phase 2)
│   │   │   ├── BiomeEditorItem.java      ← The Biome Editor Wand item
│   │   │   ├── BiomeEditorScreen.java    ← Full-screen HUD (Fabric Screen API)
│   │   │   ├── BiomeEditorState.java     ← Holds current editing session state
│   │   │   ├── PreviewZoneManager.java   ← Manages 5x5 chunk preview regen
│   │   │   └── tabs/                    ← One class per HUD tab
│   │   │       ├── VisualTab.java
│   │   │       ├── TerrainTab.java
│   │   │       ├── FeaturesTab.java
│   │   │       ├── SpawnsTab.java
│   │   │       └── ExportTab.java
│   │   └── worldgen/
│   │       ├── StrataWorldgen.java       ← Multi-noise placement params
│   │       ├── StrataWorldEvents.java    ← Fabric API biome injection + asset listener
│   │       └── feature/
│   │           └── CustomAssetFeature.java ← Placed feature type for creator assets
│   └── resources/
│       ├── fabric.mod.json
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

- [ ] `./gradlew :strata-world:build` compiles with zero errors
- [ ] `runClient` launches successfully with both `strata-core` and `strata-world` loaded
- [ ] Log shows "strata-world initialized. 1 biomes registered."
- [ ] Creating a new world and flying around eventually reveals a biome named `strata_world:verdant_highlands` in the F3 overlay
- [ ] VerdantHighlands has the correct sky color, fog color, and water color (visually distinct from vanilla forest)
- [ ] VerdantHighlands has trees, grass, and flowers generating correctly
- [ ] Setting `biomeFrequency = 0.1` in WorldConfig noticeably reduces Strata biome occurrence
- [ ] Setting `enabled = false` in WorldConfig causes no Strata biomes to generate
- [ ] No vanilla biomes are removed or noticeably altered

---

## 11. Phase Breakdown

### Phase 1 (Current Spec)
- Basic biome registration pipeline
- VerdantHighlands proof-of-concept biome
- WorldConfig (enabled toggle, biomeFrequency)

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
