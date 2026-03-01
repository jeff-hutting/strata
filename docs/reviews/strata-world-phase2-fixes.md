# strata-world Phase 2 Review — Fix Summary

Fixes applied from `docs/reviews/strata-world-phase2-review.md`.
All 10 items resolved. Both `strata-core` and `strata-world` build cleanly.

---

## HIGH

### Issue 1 — Singleplayer enforcement
**Files:** `StrataWorld.java`, `en_us.json`

Registered a `ServerPlayConnectionEvents.JOIN` listener in `StrataWorld.onInitialize()`.
If the current world is a Biome Design World and `getCurrentPlayerCount() > 1`, the connecting
player is immediately disconnected with the translatable key
`disconnect.strata_world.biome_design_singleplayer`.

The `disconnect.strata_world.biome_design_singleplayer` entry was added to `en_us.json`.

World detection uses `BiomeDesignWorldPreset.isCurrentWorldBiomeDesignWorld(server)`, which reads
`level.dat` NBT (`Data → WorldGenSettings → preset`) and compares to
`strata_world:biome_designer`. This avoids a missing Yarn API (`LevelProperties.getWorldPreset()`
does not exist in Yarn 1.21.11+build.4).

### Issue 2 — Wand auto-give + splash message on first join
**File:** `StrataWorld.java`

Registered a `StrataEvents.PLAYER_FIRST_JOIN` listener. When the world is a Biome Design World,
the wand (`StrataWand.INSTANCE`) is placed in the player's selected hotbar slot
(`getSelectedSlot()`) and a welcome message is shown. `ServerPlayerEntity.getEntityWorld()`
(returning `ServerWorld`) is used to reach `MinecraftServer` since `Entity.getServer()` is not
mapped in Yarn 1.21.11+build.4.

---

## MEDIUM

### Issue 3 — Layer 1/2 debounce delays into config
**Files:** `WorldConfig.java`, `PreviewZoneManager.java`

Added `editorLayer2DebounceMs` (default 3000 ms) and `editorLayer1DebounceMs` (default 500 ms)
to `WorldConfig`. Removed the corresponding `static final` constants from `PreviewZoneManager`.
`PreviewZoneManager.tick()` now reads the config values on each tick via
`StrataConfigHelper.get(WorldConfig.class)`.

### Issue 4 — Undo depth into config; BiomeEditorScreen reads it
**Files:** `WorldConfig.java`, `BiomeEditorScreen.java`

Added `editorUndoDepth` (default 20, range 5–100) to `WorldConfig`.
`BiomeEditorScreen` constructor no longer accepts a separate `UndoManager` parameter; it reads
the configured depth from `WorldConfig` via `StrataConfigHelper` and applies it to
`state.getUndoManager().setMaxDepth()`.

### Issue 5 — Add world_preset normal.json tag
**File:** `data/minecraft/tags/worldgen/world_preset/normal.json`

Created the tag file so that `strata_world:biome_designer` is included in the vanilla world
creation screen alongside Superflat and Amplified.

### Issue 6 — Include undo stack in BiomeEditorState serialization
**File:** `BiomeEditorState.java`

`undoManager` is now a non-transient instance field of `BiomeEditorState`, initialized lazily
via `getUndoManager()`. `UndoManager`'s stack fields use `ArrayDeque` (Gson-compatible concrete
type) instead of the `Deque` interface. Snapshots pushed to the undo stack have `undoManager ==
null` (intentional: prevents circular serialization while keeping the draft JSON compact).

### Issue 7 — loadBiome() stub + ASSET_REGISTERED listener
**Files:** `BiomeEditorScreen.java`, `StrataWorldEvents.java`, `StrataEvents.java`,
`AssetRegistered.java`

Added `loadBiome(Path jsonPath)` stub to `BiomeEditorScreen` with TODO steps for the full
implementation (SPEC §11 Phase 2).

Added `ASSET_REGISTERED` event to `StrataEvents` (strata-core) backed by a new
`AssetRegistered` functional interface. Registered a client-side listener in
`StrataWorldEvents.initialize()` (guarded with `EnvType.CLIENT`) that calls
`BiomeEditorScreen.notifyFeatureListUpdated()` and logs the registered asset ID.

---

## LOW

### Issue 8 — SPEC §8 file structure: BiomeEditorItem.java → StrataWand.java
**File:** `docs/mods/strata-world/SPEC.md`

Corrected the file tree in §8 to reference `StrataWand.java` (the actual file) instead of the
stale `BiomeEditorItem.java` name.

### Issue 9 — Remove transient from exported field
**File:** `BiomeEditorState.java`

Removed the `transient` modifier from the `exported` field so that the exported flag is
included in the draft JSON serialization as specified in SPEC §7.7.

### Issue 10 — Javadoc on all public getters/setters in BiomeEditorState
**File:** `BiomeEditorState.java`

Added full Javadoc to all public getter and setter methods, documenting the Layer 1/2
distinction, units, and behaviour of the undo manager accessor.

---

## Compilation notes

- `LevelProperties.getWorldPreset()` does not exist in Yarn 1.21.11+build.4. Replaced with
  level.dat NBT read using `NbtIo.readCompressed(Path, NbtSizeTracker)` and
  `NbtCompound.getCompoundOrEmpty()`.
- `Entity.getServer()` is not mapped in Yarn 1.21.11+build.4. Replaced with
  `ServerPlayerEntity.getEntityWorld().getServer()`.
- `PlayerInventory.selectedSlot` is private in Yarn 1.21.11+build.4. Replaced with
  `getSelectedSlot()`.
- Fabric Loom caches remapped project-dependency jars by content hash. After strata-core was
  rebuilt with the new `ASSET_REGISTERED` event, the stale Loom cache entry for the old jar
  was deleted so that strata-world picked up the updated JAR on the next build.
