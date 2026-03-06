# strata-world Phase 2 — Human Test Checklist

_Check off each item in-game before closing the phase._

Status key:
- `[x]` — confirmed passing in a human test session
- `[ ]` — open: not yet tested, code fix applied and awaiting re-test, or deferred
- `NOTE` — current state or context for that item

---

## World Creation

- [x] `Strata: Biome Designer` appears in the world type selector alongside Superflat and Amplified
- [x] Selecting it and creating a world loads successfully without crashes
      NOTE: World now generates as flat terrain with `strata_world:editor_preview` as the only biome. Previously used `minecraft:multi_noise` (full vanilla gen); that caused players to always spawn in a random vanilla biome (Savannah, Forest, etc.). Needs re-test with flat terrain.

---

## First Spawn

- [x] Strata Wand is in the player's main hand on first spawn
- [x] Splash message `"Welcome to the Strata Biome Designer. Your wand is ready."` appears
- [x] Wand is re-given on respawn if the player no longer has one

---

## Multiplayer Enforcement

- [x] Attempting to open the Design World to LAN is blocked or shows an appropriate error
      NOTE: Shows "unable to host local game" — as intended.
- [x] Attempting to join from a second client while the Design World is running is rejected
      NOTE: Second player receives "Connection Lost. Biome Design Worlds are singleplayer only."

---

## Wand — Editor Open

- [x] Right-clicking in open air opens the Biome Editor screen
      NOTE: Opens with settings from the most-recently sampled terrain click, not necessarily the biome the player is standing in. Documented behavior, not a bug.
- [x] All five tabs are visible and navigable: **Visual**, **Terrain**, **Features**, **Spawns**, **Export**
- [x] Display name field in header is editable inline
      NOTE: Editable inline with "Name:" label. No pencil/save icon (deferred to polish pass).
- [x] Auto-derived biome ID updates when display name changes
      (e.g. typing `"Frost Peaks"` produces `strata_world:frost_peaks`)
- [x] Header shows current biome ID below the display name

---

## Wand — Biome Sampling

- [x] Right-clicking terrain samples the biome at the player's current position
- [x] Editor fields populate with sampled biome values (colors, noise parameters)
- [x] Header shows `"Loaded template: minecraft:<biome>"` after sampling
- [x] Sampling a vanilla biome (Badlands, Forest, Flower Forest, etc.) works
- [ ] Unsaved-change warning appears when a draft is in progress and sampling would replace it
      NOTE: Sampling captures an undo snapshot (Ctrl+Z reversible, depth 50). Undo now works across tab switches. No modal warning dialog — still open item. Needs decision: add confirmation dialog, or accept undo-only approach?

---

## Preview Zone & Regen

- [ ] Visual changes (sky/water/fog/foliage/grass colors) apply within 3 seconds of slider adjustment
      NOTE (code fix applied): The 3-second debounce now calls `worldRenderer.reload()` for visual-only refresh. Sky color, water color, fog, foliage, and grass changes should apply immediately without chunk regeneration. Feature and spawn changes require Reset World. Needs re-test.
- [x] Rapid slider adjustments reset the timer (no mid-drag regen — only fires after the 3 s window)
- [x] `"Refreshing preview..."` HUD indicator appears during regen/refresh
- [x] Preview zone size visually matches the current render distance setting

---

## Biome Blending Toggle

- [x] **Disabled:** edited biome generates everywhere across the entire loaded world
      NOTE: The flat world always generates `editor_preview` everywhere — "Disabled" is the permanent state of the Design World by construction.
- [ ] **Enabled:** vanilla biomes appear beyond the current render distance, allowing transition evaluation
      NOTE (deferred): The flat world uses `FlatBiomeSource`, which hardcodes a single biome. Showing vanilla biomes beyond a radius requires a custom `BiomeSource` that conditionally returns `editor_preview` vs. MultiNoise based on player distance. Deferred to a future phase.

---

## Reset World

- [x] Reset World button appears in the editor toolbar
- [x] Clicking it triggers a confirmation prompt before any action
- [ ] Confirming clears terrain and regenerates using the current biome parameters
      NOTE (code fix applied): `resetWorld()` now uses the SERVER_STOPPED pattern — disconnects (triggering the server's final `saveAll()`), then `StrataWorld.SERVER_STOPPED` deletes all `.mca` files *after* the final save completes. Fresh chunks regenerate via `BiomeGenerationMixin` when the player reopens the world. The player lands on the world-select screen after confirm. Needs re-test.
- [x] Cancelling leaves the world unchanged

---

## Draft Persistence

- [x] Closing and reopening the editor (without quitting) restores the previous values
- [x] Quitting to main menu and reloading the world restores the draft
- [x] Unexported-change indicator is visible in the header when the draft differs from last export
      (indicator disappears after a successful export)
- [x] Draft file is written to `saves/<world>/strata_biomes/_session.draft.json`

---

## Export

- [x] `"Save Biome JSON"` writes a valid biome JSON to `saves/<world>/strata_biomes/<name>.json`
- [x] `"Copy JSON to Clipboard"` places the biome JSON text on the system clipboard
- [x] `"Export Strata-Pack"` produces a `.stratapack` archive containing the biome JSON,
      `en_us.json`, and a valid `strata-pack.json` manifest with `name`, `author`, and `version`
      NOTE: Two-click overwrite confirmation: first click warns "File exists! Click again to overwrite."
- [x] Unexported-change indicator clears after a successful export

---

## Open Items — Features & Spawns Tabs

- [ ] Entity search dropdown shows all available entities (scrollable)
      NOTE (code fix applied): Dropdown fetches up to 500 results from `ENTITY_TYPE` registry filtered by query. Shows 8 at a time with keyboard navigation (↑/↓ to scroll, Tab/Enter to autocomplete, Escape to dismiss, PageUp/PageDown to jump 8 at a time, scroll wheel). Blue border at bottom indicates more entries below. Needs re-test.
- [ ] Feature search dropdown shows all available placed features (scrollable)
      NOTE (code fix applied): Same as Entity field — 500-result pool, 8 visible, keyboard-scrollable window with PageUp/PageDown and scroll wheel. Needs re-test.

---

## Open Items — Other

- [ ] Undo/redo: unsaved-change warning when sampling replaces a draft
      NOTE: Undo (Ctrl+Z, depth 50) is the current mechanism. Undo history now persists across tab switches (tab-switch no longer consumes an undo slot). No modal warning yet — open design question.
- [ ] Biome Blending "Enabled" mode
      NOTE (deferred): See Biome Blending Toggle section above.
- [ ] Spawn chunks do not regenerate on Reset World
      NOTE (resolved by SERVER_STOPPED pattern): Because Reset World now disconnects before deleting files, the server fully stops and completes its final save before any files are removed. All chunks — including spawn chunks — are deleted and regenerated fresh when the player reopens the world from the world-select screen. Needs re-test.

---

## Test Sessions

```
Date tested: 2026/03/05
Tester: Jeff Hutting
MC version: 1.21.11
Strata version: 0.1.0

Findings from last test:
- Visual changes work (sky, water, fog color via BiomeMixin/worldRenderer.reload)
- Terrain/feature/spawn changes were NOT reflected after regen
- World always spawned in a random vanilla biome (Savannah)
- Entity/feature suggestion dropdowns could not scroll beyond 6 initial results
- Undo was consumed by tab switches

Code fixes applied: 2026/03/05
Items marked "Needs re-test" require in-game verification.
```
