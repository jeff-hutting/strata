# strata-world Phase 2 — Human Test Checklist

_Check off each item in-game before closing the phase._

Current findings are noted under each task or section header, indicated by `NOTE:` header.
---

## World Creation

- [x] `Strata: Biome Designer` appears in the world type selector alongside Superflat and Amplified
- [x] Selecting it and creating a world loads successfully without crashes

---

## First Spawn

- [x] Strata Wand is in the player's main hand on first spawn
- [x] Splash message `"Welcome to the Strata Biome Designer. Your wand is ready."` appears
- [x] Wand is re-given on respawn if the player no longer has one
      NOTE: Works as intended.

---

## Multiplayer Enforcement

- [ ] Attempting to open the Design World to LAN is blocked or shows an appropriate error
      (`disconnect.strata_world.biome_design_singleplayer` translation key)
      NOTE (code fix applied): Added IntegratedServerMixin to cancel openToLan() in Biome Design Worlds. The LAN button should now silently do nothing. Needs re-test.
- [x] Attempting to join from a second client while the Design World is running is rejected
      NOTE: Second player is rejected. Receives "Connection Lost. Biome Design Worlds are singleplayer only" message.


---

## Wand — Editor Open

- [x] Right-clicking in open air opens the Biome Editor screen
- [x] All five tabs are visible and navigable: **Visual**, **Terrain**, **Features**, **Spawns**, **Export**
- [ ] Display name field in header is editable inline (click pencil icon)
      NOTE (code fix applied): Field is editable inline with "Name:" label. No pencil/save icon (deferred to polish pass). Biome ID now auto-derives on keystroke. Needs re-test.
- [ ] Auto-derived biome ID updates when display name changes
      (e.g. typing `"Frost Peaks"` produces `strata_world:frost_peaks`)
      NOTE (code fix applied): Fixed fromSampleJson() — no longer sets biomeIdOverridden=true with vanilla ID. Sampled biome stored as templateSource, ID auto-derives from display name. ExportTab name field syncs from state. Needs re-test.
- [x] Header shows current biome ID below the display name

---

## Wand — Biome Sampling

- [x] Right-clicking terrain samples the biome at the player's current position
- [x] Editor fields populate with sampled biome values (colors, noise parameters)
- [ ] Header shows `"Loaded template: minecraft:<biome>"` after sampling
      NOTE (code fix applied): Added templateSource field to BiomeEditorState and "Loaded template:" display in header. Needs re-test.
- [x] Sampling a vanilla biome (Badlands, Forest, Flower Forest, etc.) works
- [ ] Unsaved-change prompt appears when a draft is in progress and sampling would replace it
      NOTE (code fix applied): Sampling now captures undo snapshot before replacing draft, making it reversible via Ctrl+Z. No modal dialog — uses undo instead. Needs re-test.

---

## Preview Zone & Regen

- [ ] Changing a Terrain slider (noise parameter) triggers chunk regen after approximately 3 seconds
      NOTE (code fix applied): triggerRegeneration() now writes biome JSON to datapack, calls server.reloadResources(), and triggers chunk reload. Features/spawns/colors should now take effect. Terrain SHAPE (noise placement) is export-only — it affects where the biome generates when installed in a real world. Needs re-test.
- [x] Rapid slider adjustments reset the timer (no mid-drag regen -- only fires after the 3 s window)
- [ ] `"Refreshing preview..."` HUD indicator appears during regen
      NOTE: TerrainTab renders this when pzm.isRegenerating() is true. Needs re-test.
- [ ] Preview zone size visually matches the current render distance setting

---

## Biome Blending Toggle

- [ ] **Disabled:** edited biome generates everywhere across the entire loaded world
      NOTE (code fix applied): Toggle now triggers worldRenderer.reload() on change. Needs re-test.
- [ ] **Enabled:** vanilla biomes appear beyond the current render distance, allowing transition evaluation
      NOTE: Blending affects client-side mixin overrides; toggle now triggers chunk repaint. Needs re-test.

---

## Reset World

- [x] Reset World button appears in the editor toolbar
- [x] Clicking it triggers a confirmation prompt before any action
- [ ] Confirming clears terrain and regenerates using the current parameters
      NOTE (code fix applied): resetWorld() now writes biome JSON to datapack, saves all chunks, deletes .mca files, reloads datapacks, and triggers full chunk regeneration. Needs re-test.
- [x] Cancelling leaves the world unchanged

---

## Draft Persistence

- [x] Closing and reopening the editor (without quitting) restores the previous values
- [x] Quitting to main menu and reloading the world restores the draft
- [x] Unexported-change indicator is visible in the header when the draft differs from last export
      (indicator disappears after a successful export)
- [x] Draft file is written to `saves/<world>/strata_biomes/<name>.draft.json`

---

## Export

- [x] `"Save Biome JSON"` writes a valid biome JSON to `saves/<world>/strata_biomes/<name>.json`
- [x] `"Copy JSON to Clipboard"` places the biome JSON text on the system clipboard
- [x] `"Export Strata-Pack"` produces a `.stratapack` archive containing the biome JSON,
      `en_us.json`, and a valid `strata-pack.json` manifest with `name`, `author`, and `version`
      NOTE (code fix applied): Added two-click overwrite confirmation. First click warns "File exists! Click again to overwrite." Needs re-test.
- [x] Unexported-change indicator clears after a successful export
      NOTE: "Exported" message displays and "Unsaved" clears — working as intended.

---

## Notes/Bugs

- [ ] Biome does not regenerate with updated settings.
      NOTE (code fix applied): triggerRegeneration() and resetWorld() now write biome JSON to datapack and reload server resources. Needs re-test.
- [ ] Biome ID no longer updating - only displays vanilla biomes
      NOTE (code fix applied): Fixed fromSampleJson() to not set biomeIdOverridden. ID auto-derives from display name. Needs re-test.
- [ ] 'Entities' field shows an example entity, but I would like to be able to select from a list of available entities
      NOTE (code fix applied): Added searchable suggestion dropdown filtered from ENTITY_TYPE registry. Shows up to 6 matches as user types. Needs re-test.
- [ ] In the Spawns tab, it shows a table with the headings "Entity, Wt, Min, Max". It is currently displaying to close to the Entity: <field> directly above it, so there is some minor overlap.
      NOTE (code fix applied): Increased vertical spacing between entity field and table headers. Needs re-test.
- [ ] 'Features' field shows example feature, but I would like to be able to select from a list of availabe features.
      NOTE (code fix applied): Added searchable suggestion dropdown filtered from PLACED_FEATURE dynamic registry. Shows up to 6 matches as user types. Needs re-test.
- [ ] In Terrain tab, the bottom text for "Current Biome: " displays to close to the buttons, so the top of the text is covered by the buttons.
      NOTE (code fix applied): Moved status text below all buttons with proper spacing. Needs re-test.


```
Date tested: 2025/03/05 11:55
Tester: Jeff Hutting
MC version: 1.21.11
Strata version: 0.1.0

Findings:

See Notes section above.

Code fixes applied: 2026/03/05
Items marked "code fix applied" need in-game re-testing to confirm.

```
