# strata-creator — Design Intent

> **Status:** Design phase — not yet ready for implementation
> **Build Priority:** #5 — depends on all other modules
> **This document captures architectural intent, not implementation detail.**
> A full SPEC.md will be written when Phase 4 (strata-rpg) is complete.

---

## 1. Vision

`strata-creator` is an in-game creative IDE. A player in Creative mode can open it, design a new kind of tree — choosing its wood type, leaf color, fruit, canopy shape, height range — draw its textures pixel by pixel in a built-in texture editor, and export it. From that moment, the custom tree is a live registered asset available in the Biome Editor's feature picker. No file editing, no mod recompilation, no restarting.

The goal is to make Strata a platform, not just a mod. Anyone can extend the world using only the game itself.

---

## 2. The Asset Pipeline

```
Player designs asset in strata-creator
        ↓
Asset definition saved as JSON (data/strata_creator/assets/<type>/<name>.json)
Textures saved to runtime resource pack (assets/strata_creator/textures/...)
        ↓
StrataAssetRegistry.registerFeature() called (strata-core)
        ↓
ASSET_REGISTERED event fires
        ↓
strata-world's Biome Editor receives the new asset in its feature/spawn picker
        ↓
Designer places asset in a biome definition via the Biome Editor
        ↓
Biome JSON exports with asset reference
        ↓
Worldgen engine resolves asset reference → spawns it naturally in the world
```

No step in this pipeline requires leaving the game.

---

## 3. Asset Types

### 3.1 Custom Block

The simplest asset type. Defines a new block with custom textures and properties.

**Designer inputs:**
- Block name and ID
- Textures: up to 6 faces (top, bottom, north, south, east, west), or a single "all faces" texture
- Texture editor: 16×16 pixel grid (expandable to 32×32)
- Properties: hardness, blast resistance, sound group (stone/wood/grass/etc.), luminance (0–15), transparency, flammability
- Drop behavior: drops itself, drops nothing, drops a custom item

**worldgen role:** Can register as a `WorldgenFeature` in the `ORE` or `GROUND_COVER` category (e.g., a decorative surface block or ore vein). Also used as a building material for Custom Trees and Custom Structures.

**Output files:**
- `data/strata_creator/assets/blocks/<name>.json` — block definition
- `assets/strata_creator/textures/block/<name>.png` — texture(s)
- `assets/strata_creator/models/block/<name>.json` — block model (auto-generated)

---

### 3.2 Custom Item

A standalone item with custom texture and properties. Can also be produced as a drop from Custom Blocks, Custom Trees (fruit), or Custom Mobs.

**Designer inputs:**
- Item name and ID
- Texture: 16×16 or 32×32 pixel grid
- Properties: max stack size (1–64), durability (0 = no durability), food properties (if food: hunger/saturation values), rarity (common/uncommon/rare/epic — affects name color)

**Output files:**
- `data/strata_creator/assets/items/<name>.json`
- `assets/strata_creator/textures/item/<name>.png`

---

### 3.3 Custom Tree

A complex asset composed of multiple blocks and generation rules.

**Designer inputs:**
- Tree name and ID
- **Wood type:** Select or create a Custom Block for the log
- **Leaf type:** Select or create a Custom Block for leaves (can have decay behavior and a color tint)
- **Sapling:** Auto-generated from wood + leaf selection, or custom
- **Fruit:** Optional. Select or create a Custom Item that grows on leaf blocks (with configurable frequency)
- **Canopy shape:** Preset shapes (round, conical, layered, weeping) or custom NBT template
- **Trunk:** Height range (min/max), trunk girth (1×1 or 2×2), branch probability
- **Placement rules:** Min/max Y level, required surface blocks, spacing

**WorldgenFeature contract:**
The Custom Tree implements `WorldgenFeature` in the `TREE` category. Its `toFeatureJson()` produces a vanilla-compatible `ConfiguredFeature` JSON using the `minecraft:tree` or custom Strata feature type. The worldgen engine calls this when placing the tree in the biome.

**Output files:**
- `data/strata_creator/assets/trees/<name>.json` — full tree definition
- Block and item definitions for each component (log, leaves, sapling, fruit if present)

---

### 3.4 Custom Plant / Flora

A ground-level or tall plant for biome decoration.

**Designer inputs:**
- Plant name and ID
- Height: single-block (flower/mushroom style) or two-block (tall grass style)
- Texture(s): per block segment
- Placement rules: supported surfaces, light level requirement, water tolerance
- Growth behavior: static (decorative only) or farmable (can be planted by player)

**WorldgenFeature contract:** Category `PLANT`, `FLOWER`, or `GRASS` depending on type.

---

### 3.5 Custom Mob

An entity with custom model, AI behaviors, drops, and attributes.

**Designer inputs:**
- Mob name and ID
- **Model:** A simplified box-model editor (head, body, 4 limbs — Minecraft's standard skeleton). Size adjustable per segment.
- **Texture:** Texture editor on an unwrapped UV layout of the model
- **AI behaviors:** Selected from a preset list (wander, follow player, flee on damage, attack player, attack animals, passive, swim, etc.). Multiple behaviors can be combined.
- **Attributes:** Max health, movement speed, armor, attack damage, knockback resistance. All numeric, sliders with sane defaults.
- **Drops:** Loot table builder — add drop entries (vanilla items or custom items), set chance and quantity
- **Spawn conditions:** Biome types, light level, height range, surface block requirements
- **Sound set:** Choose from vanilla sound groups (animal, hostile, neutral) — custom sounds deferred to later

**SpawnableAsset contract:** Implements `SpawnableAsset`. Its `toSpawnJson()` produces a spawn entry for the biome JSON spawners array.

**Output files:**
- `data/strata_creator/assets/mobs/<name>.json`
- `assets/strata_creator/textures/entity/<name>.png`
- `assets/strata_creator/geo/<name>.json` (model geometry — Bedrock-style JSON or custom format)

> **Note:** Custom mob rendering requires a client-side rendering pipeline. The model geometry format and renderer need their own design session before implementation. This is the most technically complex asset type.

---

## 4. Texture Workflow — External Editor Approach

**Decision:** `strata-creator` does not include a built-in pixel editor. Instead, it manages texture files and delegates all editing to the player's preferred external tool.

**Rationale:** Building a usable pixel art editor inside a Minecraft screen would be weeks of complex GUI work — and the result would always be worse than dedicated tools like Aseprite, Paint.NET, or Photoshop. Every serious Minecraft texture artist already has a preferred tool. Deferring to that tool produces higher-quality assets with far less code.

---

### The Workflow

1. **"Edit Texture" button** in any asset designer (Block, Item, Tree, etc.)
2. strata-creator writes the current texture PNG to its runtime resource pack folder at a deterministic path:
   ```
   resourcepacks/strata_creator_<world-name>/assets/strata_creator/textures/<type>/<asset-name>.png
   ```
   If no texture exists yet, it writes a blank 16×16 template (transparent or magenta checkerboard).
3. strata-creator calls Fabric's `Util.getOperatingSystem().open(file)` to open the PNG in the OS default application — the same mechanism Minecraft uses for "Open Resource Pack Folder". The player's default image editor opens automatically.
4. The player edits the texture and saves it from their external tool.
5. Back in the game, the player clicks **"Reload Texture"** (a button in the designer UI). strata-creator calls `MinecraftClient.getInstance().reloadResources()` to pick up the saved PNG. The reload takes 1–3 seconds.

> **Why a manual reload button instead of file-watching?**
> Java's `WatchService` adds implementation complexity and can behave inconsistently across OS/filesystem combinations. A single button is simpler, more predictable, and gives the player explicit control over when the reload happens (useful when iterating on a texture with multiple saves).

---

### "Copy from Vanilla" Feature

strata-creator provides a texture browser that lets players start from an existing vanilla texture rather than a blank canvas:

- Player clicks "Copy from Vanilla" in the asset designer
- A searchable list of vanilla block/item texture paths is shown (extracted from the game's assets at runtime)
- Player selects one → it is copied to the asset's output path
- Player clicks "Edit Texture" to open it in their external editor

This replaces the "Copy from vanilla" feature of the original built-in editor concept.

---

### What strata-creator Still Owns

- File path management (naming, placement, ensuring the folder exists)
- Template generation (blank 16×16 PNG for new assets)
- The vanilla texture browser
- The "Reload Texture" button and `reloadResources()` call
- Displaying the current texture as a preview within the asset designer screen (renders the PNG onto a screen widget — display only, not editable)

---

### Platform Note

`Util.getOperatingSystem().open(file)` is the vanilla Minecraft call for "Open in system default app." It works on Windows, macOS, and Linux. On systems where no default PNG application is configured, it will silently fail — the designer UI should handle this gracefully by also displaying the file path in a copyable text field so the player can navigate to it manually.

---

## 5. Data Persistence

All custom assets are saved as JSON files in the world save directory:
```
saves/<world-name>/strata_creator/
├── blocks/
├── items/
├── trees/
├── plants/
└── mobs/
```

Textures are saved to the game's resource pack directory:
```
resourcepacks/strata_creator_<world-name>/
└── assets/strata_creator/textures/
    ├── block/
    ├── item/
    └── entity/
```

On world load, `strata-creator` scans the save directory, deserializes all asset JSONs, and re-registers them in `StrataAssetRegistry`. This means custom assets survive world restarts.

**Sharing assets:** A player can copy their `strata_creator/` save folder and `strata_creator_<world>/` resource pack to share custom assets with others. A future "asset pack" export feature will package these into a single zip.

---

## 6. Architecture Notes

### No Direct Dependency on strata-world
`strata-creator` registers assets into `StrataAssetRegistry` (strata-core) and fires events. It never directly calls `strata-world` code. The biome editor in `strata-world` listens for `ASSET_REGISTERED` and updates itself. This keeps the modules decoupled.

### Client-Side vs. Server-Side
The creator UI is entirely client-side (Fabric `ClientModInitializer`). Asset definitions and textures are client-side resources. However, for assets to appear in multiplayer worlds, they need to be distributed to all clients — this is a future consideration (asset pack syncing via server resource pack).

Phase 1 of `strata-creator` targets **single-player Creative mode only**.

### strata-core Dependencies Added by This Module
`strata-creator` depends on `strata-core` for:
- `StrataAssetRegistry` — to register completed assets
- `WorldgenFeature` / `SpawnableAsset` — interfaces to implement
- `StrataEvents` — to fire `ASSET_REGISTERED`
- `StrataLogger` — logging
- `StrataConfigHelper` — for the `CreatorConfig` class

---

## 7. Implementation Sequence (When Phase 5 Begins)

1. Write full `SPEC.md` (replace this document)
2. Scaffold `strata-creator` module
3. Implement Custom Block designer (simplest asset type — good proof of concept)
4. Implement texture workflow: file path management, blank template generation, `open()` call, Reload button, preview widget, vanilla texture browser
5. Implement Custom Item designer
6. Implement Custom Tree designer (most complex non-mob asset)
7. Implement Custom Plant/Flora designer
8. Implement Custom Mob designer (save for last — most complex)
9. Implement asset persistence (save/load on world open/close)
10. End-to-end test: design a tree → appears in Biome Editor → placed in biome → generates in world

> **Note:** Step 4 is now the texture *workflow* (file management + OS open + reload), not a built-in pixel editor. This is significantly less complex than the original plan. The mob renderer in Step 8 remains the most technically challenging component and still requires a dedicated design session.

---

*This document should be revisited and converted to a full SPEC.md after strata-rpg (Phase 4) is complete and before Phase 5 begins. The mob model format and renderer will need a dedicated design session with Claude Opus before implementation begins.*
