# strata-world Phase 2 Scaffold Review

> **Date:** 2026-03-01
> **Scope:** Phase 2 Biome Editor MVP scaffold across `strata-core` and `strata-world`
> **Commit:** 5f47d5d (feat(strata-world): scaffold Phase 2 Biome Editor MVP class structure)

---

## 1. Naming Conventions

### Passed

- **StrataWandRegistry** lives in `io.strata.core.wand` — correct placement in strata-core per ARCHITECTURE.md §9.
- **StrataWand** lives in `io.strata.world.editor` — correct per SPEC.md §7.8 ("wand item lives in strata-world for Phase 2").
- **BiomeEditorScreen** lives in `io.strata.world.editor` — correct.
- All five tab classes live in `io.strata.world.editor.tabs` — correct.
- Mod IDs use underscores (`strata_world`, `strata_core`) — correct per ARCHITECTURE.md §5.
- Class naming follows conventions: `StrataWand`, `StrataWandRegistry`, `BiomeEditorState`, `WorldConfig`.

### Issues

### strata-world/src/main/java/io/strata/world/editor/StrataWand.java:41
**Issue:** SPEC.md §8 File Structure lists the wand item class as `BiomeEditorItem.java`, but the scaffold uses `StrataWand.java`. The ARCHITECTURE.md §9 also names it "Strata Wand", so `StrataWand` is arguably the better name — but the SPEC and scaffold are inconsistent.
**Fix:** Update SPEC.md §8 file structure to reflect the rename from `BiomeEditorItem.java` to `StrataWand.java`, since the ARCHITECTURE.md naming is authoritative and `StrataWand` correctly represents its universal purpose.

---

## 2. Hardcoded Values

### Passed

- `UndoManager` default depth (20), range (5–100) are enforced in the constructor via `Math.max(5, Math.min(100, maxDepth))` — correct per SPEC.md §7.7.
- `PreviewZoneManager.getRenderDistance()` reads from `MinecraftClient.getInstance().options.getViewDistance().getValue()` — correct per SPEC.md §7.2.

### Issues

### strata-world/src/main/java/io/strata/world/editor/PreviewZoneManager.java:25-28
**Issue:** The debounce timers (`LAYER2_DEBOUNCE_MS = 3000` and `LAYER1_DEBOUNCE_MS = 500`) are hardcoded as `private static final` constants. SPEC.md §7.1 specifies the 3-second window and §7.7 specifies ~500ms for Layer 1, but the review brief requires these live in `WorldConfig` or an editor preferences class — not as magic numbers.
**Fix:** Move both debounce durations to `WorldConfig` as configurable fields (e.g., `public long editorLayer2DebounceMs = 3000` and `public long editorLayer1DebounceMs = 500`). `PreviewZoneManager` should read from config rather than using constants.

### strata-world/src/main/java/io/strata/world/editor/BiomeEditorState.java:312-325
**Issue:** The `UndoManager` default depth of 20 is passed by callers — but there is no config field backing it. The SPEC says it's "configurable via a slider in the editor's preferences panel" (§7.7), and the depth range (5–100) is validated in `UndoManager`, but no `WorldConfig` field or editor preference class provides the default value. The caller must know to pass 20.
**Fix:** Add `public int editorUndoDepth = 20` to `WorldConfig` (or a new `EditorPreferences` class). `BiomeEditorScreen` should read the default from config when constructing `UndoManager`.

---

## 3. Fabric API Usage

### Passed

- `BiomeDesignWorldPreset` uses `RegistryKey.of(RegistryKeys.WORLD_PRESET, ...)` — correct Fabric registry key pattern.
- `StrataWand.register()` uses `Registry.register(Registries.ITEM, ...)` — correct Fabric item registration.
- `BiomeEditorScreen` extends `net.minecraft.client.gui.screen.Screen` — correct Fabric Screen API usage.
- No direct access to vanilla internal classes (`net.minecraft.server.*` internals).

### Issues

### strata-world/src/main/resources/data/strata_world/worldgen/world_preset/biome_designer.json:1-36
**Issue:** The world preset JSON is data-driven (correct approach), but it relies solely on the data file being picked up by vanilla's world preset registry. Fabric's `WorldPresets` tag system requires the preset to be added to the `minecraft:normal` tag (or a custom tag) to appear in the world creation screen. There is no tag file at `data/minecraft/tags/worldgen/world_preset/normal.json` adding `strata_world:biome_designer`.
**Fix:** Create `strata-world/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json` containing `{"replace": false, "values": ["strata_world:biome_designer"]}` so the preset appears in the world creation screen.

---

## 4. StrataWandRegistry Decoupling

### Passed

- `strata-world` registers its handler via `StrataWandRegistry.register(new BiomeEditorWandHandler())` in `StrataWorld.onInitialize()` — uses the strata-core interface, not direct coupling.
- `StrataWand.use()` delegates to `StrataWandRegistry.findMatching()` — no direct call from the wand item into `BiomeEditorScreen`.
- `WandHandler` is an interface in `strata-core`; `BiomeEditorWandHandler` is an implementation in `strata-world` — clean separation.
- No direct cross-module Java dependencies outside strata-core interfaces (strata-world imports only `io.strata.core.wand.*` and `io.strata.core.util.*`).

---

## 5. Singleplayer Enforcement

### strata-world/src/main/java/io/strata/world/editor/BiomeDesignWorldPreset.java:21-23
**Issue:** The Javadoc states "Multiplayer join is disabled for Biome Design Worlds — enforced by checking the world's generator type on player connection" but there is no implementation. The class only contains a registry key constant and an `isBiomeDesignWorld()` helper. No Mixin, event listener, or connection handler exists to actually block multiplayer join. The SPEC §7.0 requires "multiplayer join is disabled at the world type level."
**Fix:** Implement multiplayer blocking. Options: (a) a `ServerPlayConnectionEvents.JOIN` listener in `StrataWorld.onInitialize()` that kicks non-host players when the active world preset is `BIOME_DESIGNER`, or (b) a Mixin on the server connection handler. At minimum, add a stub method with a clear TODO that this is a required pre-release blocker, not just a nice-to-have.

---

## 6. Wand Auto-Give and Splash Message

### strata-world/src/main/java/io/strata/world/StrataWorld.java:36-52
**Issue:** There is no implementation of wand auto-give or splash message on first spawn in a Biome Design World. SPEC.md §7.0 requires: "The Strata Wand is automatically placed in the player's main hand at first spawn, accompanied by a splash message: 'Welcome to the Strata Biome Designer. Your wand is ready.'" The `en_us.json` has the translation key `message.strata_world.welcome` but nothing fires it.
**Fix:** Register a `PLAYER_FIRST_JOIN` listener (from `StrataEvents`) that: (1) checks whether the current world is a Biome Design World via `BiomeDesignWorldPreset.isBiomeDesignWorld()`, (2) places `StrataWand.INSTANCE` as an `ItemStack` in the player's main hand inventory slot, (3) sends the splash message via `player.sendMessage(Text.translatable("message.strata_world.welcome"))`. The `PLAYER_FIRST_JOIN` event already fires only on first join (not respawn), so this is the correct hook.

---

## 7. BiomeEditorState Serialization

### Passed

- `BiomeEditorState.toJson()` / `fromJson()` use Gson serialization — correct.
- `saveDraft(Path)` writes to the provided path and creates parent directories — correct.
- `loadDraft(Path)` reads and deserializes — correct.
- Draft path format `saves/<world>/strata_biomes/<name>.draft.json` is documented and consistent with SPEC.md §7.7.

### Issues

### strata-world/src/main/java/io/strata/world/editor/BiomeEditorState.java:218-266
**Issue:** The `UndoManager` is not included in the serialization. SPEC.md §7.7 states "Draft persistence — BiomeEditorState serializes the full editor state (all parameter values, display name, active tab, **undo history**)" — emphasis on undo history being part of the draft. The `UndoManager` is a separate object not referenced by `BiomeEditorState`, and since it's not a field on the state, Gson will not serialize it.
**Fix:** Either (a) make `UndoManager` a field of `BiomeEditorState` so Gson serializes the undo/redo stacks automatically, or (b) add explicit `serializeUndoHistory()` / `deserializeUndoHistory()` methods that write/read the stacks to/from the same draft JSON. Option (a) is simpler but requires making `UndoManager` Gson-friendly (the `Deque<BiomeEditorState>` fields would need to serialize correctly — test for circular references since each snapshot is a `BiomeEditorState`).

### strata-world/src/main/java/io/strata/world/editor/BiomeEditorState.java:71-74
**Issue:** The `exported` and `dirty` fields are marked `transient`, which means they are excluded from Gson serialization. This is correct for `dirty` (should reset on load), but `exported` should persist — the SPEC says "The editor tracks whether the current state has been exported, and shows an indicator when there are unexported changes" (§7.7). If `exported` is transient, reopening a draft always shows the "unexported changes" indicator even if the user had already exported.
**Fix:** Remove the `transient` modifier from `exported`.

---

## 8. PreviewZoneManager

### Passed

- `getRenderDistance()` reads from `MinecraftClient.getInstance().options.getViewDistance().getValue()` — dynamically reads render distance, not hardcoded.
- Debounce resets correctly: `onLayer2Changed()` sets `lastLayer2ChangeTime = System.currentTimeMillis()`, and `tick()` checks `now - lastLayer2ChangeTime >= LAYER2_DEBOUNCE_MS` — each call to `onLayer2Changed()` resets the timer.

### Issues

(Debounce constant hardcoding covered in Section 2 above.)

---

## 9. Javadoc

### Passed

- **StrataWandRegistry**: All public methods (`register`, `findMatching`, `count`, `initialize`) have Javadoc.
- **WandHandler**: All interface methods (`getId`, `getDisplayName`, `matches`, `handle`) have Javadoc.
- **StrataWand**: Class-level Javadoc present. `register()` has Javadoc. `use()` is an `@Override` — exempt.
- **BiomeEditorState**: Class-level Javadoc. `toJson`, `fromJson`, `saveDraft`, `loadDraft`, `copy`, `deriveId` have Javadoc. `UndoManager` inner class and its public methods all have Javadoc.
- **PreviewZoneManager**: Class-level Javadoc. All public methods (`onLayer2Changed`, `onLayer1Changed`, `tick`, `forceRegeneration`, `resetWorld`, `getRenderDistance`, `isRegenerating`, `isBiomeBlendingEnabled`, `setBiomeBlendingEnabled`) have Javadoc.
- **BiomeEditorScreen**: Class-level Javadoc. `notifyFeatureListUpdated`, `setActiveTab`, `getState`, `getUndoManager`, `shouldPause` have Javadoc.
- Stub tab classes contain no public logic beyond `getTabName()`, `init()`, and `render()` — exempt per review criteria.

### Issues

### strata-world/src/main/java/io/strata/world/editor/BiomeEditorState.java:116-211
**Issue:** The getter/setter pairs for all Layer 1 and Layer 2 fields (`getSkyColor`, `setSkyColor`, `getTemperature`, etc.) have no Javadoc. These are public methods on a public class. While getters/setters are often self-documenting, the review brief requires all public methods have Javadoc.
**Fix:** Add brief Javadoc to at least the setter methods (e.g., `/** Sets the sky color. Layer 1 — changes apply instantly. */`). Getters can share via `@see` or be exempt if a blanket accessor policy is documented.

### strata-world/src/main/java/io/strata/world/editor/BiomeDesignWorldPreset.java:40-42
**Issue:** `isBiomeDesignWorld()` has Javadoc (good), but `initialize()` has minimal Javadoc. Minor — acceptable for a scaffold.
**Fix:** No action required — acceptable for scaffold phase.

---

## 10. Phase 2 Acceptance Criteria Cross-Check

Cross-referencing SPEC.md §11 Phase 2 checklist against the scaffold:

| Checklist Item | Status | Notes |
|---|---|---|
| Biome Design World custom preset | **Stub** | Registry key + JSON present; missing world preset tag (see §3) |
| Singleplayer-only enforcement | **Missing** | No implementation — documented only (see §5) |
| Wand auto-give at first spawn | **Missing** | No event listener (see §6) |
| Splash message at first spawn | **Missing** | Translation key exists but no code fires it (see §6) |
| Strata Wand item | **Present** | Registered, right-click delegates to registry |
| Wand handler registry in strata-core | **Present** | `StrataWandRegistry` + `WandHandler` interface |
| Biome Editor wand handler | **Present** | `BiomeEditorWandHandler` matches all interactions |
| Full-screen HUD (5 tabs) | **Stub** | Screen + 5 tab classes scaffolded; content is TODO |
| Editable display name + auto-derived ID | **Present** | `BiomeEditorState` has name/ID logic |
| Layer 1 instant preview | **Stub** | State model has Layer 1 fields; no rendering |
| Layer 2 debounced chunk regen | **Stub** | `PreviewZoneManager` has timer logic; regen is TODO |
| Reset World button | **Stub** | `PreviewZoneManager.resetWorld()` exists; body is TODO |
| Biome Blending toggle | **Stub** | Boolean field + setter in `PreviewZoneManager`; no effect |
| Biome naming (display + ID + translation) | **Present** | `deriveId()` + `en_us.json` |
| Draft persistence | **Present** | `saveDraft` / `loadDraft` implemented |
| Undo/Redo | **Partial** | `UndoManager` implemented; not included in draft serialization |
| Load biome from list | **Missing** | No load-from-existing-biome-JSON implementation or stub |
| Feature picker (vanilla + StrataAssetRegistry) | **Stub** | `FeaturesTab` scaffolded; content is TODO |
| Spawn picker (vanilla + StrataAssetRegistry) | **Stub** | `SpawnsTab` scaffolded; content is TODO |
| Export biome JSON to file | **Stub** | `ExportTab` scaffolded; content is TODO |
| Strata-Pack export | **Stub** | `ExportTab` scaffolded; content is TODO |
| `ASSET_REGISTERED` event listener | **Missing** | `notifyFeatureListUpdated()` exists but no event listener is registered in `StrataWorldEvents` or `StrataWorld` |
| Right-click terrain to sample biome | **Stub** | `BiomeEditorWandHandler.handle()` has TODO for biome sampling |

### Items with no stub at all:

### Missing: Load biome from list
**Issue:** SPEC.md §11 Phase 2 includes "Load biome from list: reads existing Strata biome JSON -> populates UI -> unsaved-change prompt -> Reset World regen." There is no stub method, no UI placeholder, and no TODO for this feature in any of the tab classes or `BiomeEditorScreen`.
**Fix:** Add a stub `loadBiome(Path jsonPath)` method to `BiomeEditorScreen` or `BiomeEditorState` with a clear TODO. The Load button in the header bar should also be stubbed in `BiomeEditorScreen.init()`.

### Missing: ASSET_REGISTERED event listener
**Issue:** SPEC.md §7.4 and §11 require `strata-world` to register a listener on `StrataEvents.ASSET_REGISTERED` that calls `BiomeEditorScreen.notifyFeatureListUpdated()`. The static method exists on `BiomeEditorScreen` but no listener is registered anywhere — not in `StrataWorld.onInitialize()`, `StrataWorldEvents.initialize()`, or `StrataWorldClient.onInitializeClient()`.
**Fix:** Add the event registration in `StrataWorldEvents.initialize()` or `StrataWorld.onInitialize()`, matching the pattern shown in SPEC.md §7.4.

---

## Summary

**Total issues found: 9**

| # | Severity | File | Issue |
|---|---|---|---|
| 1 | Low | SPEC.md §8 | File name mismatch: `BiomeEditorItem.java` vs `StrataWand.java` |
| 2 | Medium | PreviewZoneManager:25-28 | Debounce timers hardcoded instead of in config |
| 3 | Medium | BiomeEditorState:312-325 | Undo depth default not backed by config field |
| 4 | Medium | world_preset JSON | Missing world preset tag — preset won't appear in UI |
| 5 | High | BiomeDesignWorldPreset | Singleplayer enforcement not implemented |
| 6 | High | StrataWorld | Wand auto-give + splash message not implemented |
| 7 | Medium | BiomeEditorState:218-266 | Undo stack not included in draft serialization |
| 8 | Low | BiomeEditorState:71 | `exported` field marked transient — won't persist |
| 9 | Low | BiomeEditorState:116-211 | Missing Javadoc on getter/setter methods |
| 10 | Medium | BiomeEditorScreen | No stub for "Load biome from list" feature |
| 11 | Medium | StrataWorldEvents | `ASSET_REGISTERED` listener not registered |

**Verdict:** The scaffold is architecturally sound — naming conventions are correct, the wand registry decoupling is clean, and the two-layer state model is well-designed. However, two high-severity items (singleplayer enforcement, wand auto-give) have no implementation at all, and several medium-severity items need attention before the scaffold can be considered complete.
