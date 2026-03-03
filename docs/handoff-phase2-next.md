# Handoff Prompt — strata-world Phase 2 continuation

Use this as the opening message for the next chat session.

---

## Context

We are building **strata-world**, a Fabric mod for Minecraft 1.21.11 (Yarn mappings 1.21.11+build.4, Fabric Loader 0.16.x). The project is a multi-module Gradle workspace. The module we are working in is `strata-world`.

The current feature under development is the **Phase 2 Biome Editor MVP** — an in-game GUI that lets players design custom biomes in real-time without leaving the world.

All Phase 2 work lives on branch **`feat/phase2-biome-editor-mvp`** (commit `b108acd`). Do not commit directly to `main`.

---

## What is already working (confirmed in-game)

- Right-clicking the Biome Design Wand opens a tabbed `BiomeEditorScreen`
- Tab navigation works via mouse click, Tab key, arrow keys, and number keys 1–5
- **Visual tab** is fully functional:
  - Six clickable color rows: Sky, Fog, Water, Water Fog, Grass, Foliage
  - R/G/B sliders update the world in real-time as you drag
  - Sky and Fog colors update instantly (per-frame mixin)
  - Water, Water Fog, Grass, Foliage colors update after a 750 ms debounce triggers `worldRenderer.reload()`
  - Session state persists when you press ESC and reopen the editor (same world session)
  - Session state is saved to `<world>/strata_biomes/_session.draft.json` on world disconnect and restored on next editor open — colors survive world close/reopen
- Terrain, Features, Spawns, Export tabs are **stubs** (show placeholder text, not yet functional)

---

## Key architecture

### Session lifetime
`BiomeEditorSession` is a static singleton holding the active `BiomeEditorState` and `PreviewZoneManager`. It is opened on wand right-click (via S2C packet) and cleared on world disconnect (`ClientPlayConnectionEvents.DISCONNECT`). It is **not** cleared on screen close.

### Live color overrides
Two client-only Fabric mixins intercept MC's color resolution per-frame:
- `EnvironmentAttributeMapMixin` — overrides `EnvironmentAttributeMap.apply()` for sky / fog / waterFog (instant, no chunk rebuild needed)
- `BiomeMixin` — overrides `Biome.getWaterColor()`, `getGrassColorAt()`, `getFoliageColor()`, `hasPrecipitation()` (mesh-baked; requires `worldRenderer.reload()` to take effect)

Both are registered under `"client"` in `strata_world.mixins.json` so they never load on a dedicated server.

### State persistence
- `BiomeEditorState.saveDraft(Path)` / `loadDraft(Path)` — Gson serialization of all fields
- Draft path (singleplayer): `<world_save_dir>/strata_biomes/_session.draft.json`
- Draft path (multiplayer): `<game_dir>/strata-world-sessions/<server_key>/strata_biomes/_session.draft.json`
- Save triggered in `StrataWorldClient` → `ClientPlayConnectionEvents.DISCONNECT`
- Load triggered in `StrataWorldClient` → S2C handler (first open in a new session)

### MC 1.21.11 API quirks to remember
- `drawText()` color is **ARGB 32-bit** — `0xFFFFFF` = alpha 0 (invisible). Always use `0xFFFFFFFF` / `0xFF888888` etc.
- `mouseClicked` signature is `(Click click, boolean doubleClick)` — **not** `(double, double, int)`
- `SliderWidget` is in `net.minecraft.client.gui.widget.SliderWidget`; `value` is `protected double`; subclass must implement `updateMessage()` and `applyValue()`
- `WorldSavePath` is in `net.minecraft.util.WorldSavePath` (not `world.storage`)
- `Screen.addDrawableChild()` is `protected` — tab classes call the `addTabWidget()` public wrapper on `BiomeEditorScreen`

---

## Important files

```
strata-world/src/main/java/io/strata/world/
  StrataWorldClient.java              — client init; S2C handler; disconnect save/load
  editor/
    BiomeEditorScreen.java            — main Screen subclass; tab lifecycle
    BiomeEditorSession.java           — static singleton; session lifetime
    BiomeEditorState.java             — all biome properties; Gson serialization; UndoManager
    BiomeEditorWandHandler.java       — server-side wand right-click → S2C packet
    tabs/
      EditorTab.java                  — abstract base for all tabs
      VisualTab.java                  — FULLY IMPLEMENTED (RGB sliders, color preview)
      TerrainTab.java                 — STUB
      FeaturesTab.java                — STUB
      SpawnsTab.java                  — STUB
      ExportTab.java                  — STUB
  mixin/
    BiomeMixin.java                   — water/grass/foliage/precipitation overrides
    EnvironmentAttributeMapMixin.java — sky/fog/waterFog overrides
  network/
    OpenBiomeEditorPayload.java       — S2C packet
strata-world/src/main/resources/
  strata_world.mixins.json            — includes "client" section for both mixins
```

---

## Suggested next steps

The Phase 2 spec defines these remaining features (all in stub tabs):

### Terrain tab (`TerrainTab.java`)
Six multi-noise sliders: Temperature, Humidity, Continentalness, Erosion, Weirdness, Depth. Each is a float in [-1, 1] or [0, 1] range. Changes are **Layer 2** — they require the `PreviewZoneManager` to regenerate chunks in the preview zone (debounced). `BiomeEditorState` already has all six fields with getters/setters.

### Features tab (`FeaturesTab.java`)
A scrollable checklist of placed-feature identifiers (`BiomeEditorState.getFeatures()`). Players toggle features on/off; the preview zone regenerates. Feature IDs come from the strata-creator registry.

### Spawns tab (`SpawnsTab.java`)
A list editor for `BiomeEditorState.getSpawnEntries()`. Each `SpawnEntry` has `entityId`, `weight`, `minGroupSize`, `maxGroupSize`. Needs add/remove/edit rows.

### Export tab (`ExportTab.java`)
A "Finalize & Export" button that writes the biome as a proper datapack JSON to `saves/<world>/datapacks/strata-custom-biomes/data/strata_world/worldgen/biome/<id>.json`. The export format mirrors `verdant_highlands.json`.

### Undo/Redo keybindings
`BiomeEditorState.UndoManager` is already implemented. Wire up Ctrl+Z / Ctrl+Y keybindings in `BiomeEditorScreen` (the TODO is marked in `StrataWorldClient.java`).

### Commit discipline
- Work on branch `feat/phase2-biome-editor-mvp`
- Commit after each self-contained feature or bugfix using conventional commits (`feat:`, `fix:`, `refactor:`, etc.)
- Do not accumulate more than ~5 changed files between commits
