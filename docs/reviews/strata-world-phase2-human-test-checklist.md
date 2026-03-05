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
      NOTE (resolved): Added PLAYER_RESPAWN listener in StrataWorld that re-gives the wand in Biome Design Worlds. Shows actionbar message "Strata Wand restored." Needs re-test.

---

## Multiplayer Enforcement

- [ ] Attempting to open the Design World to LAN is blocked or shows an appropriate error
      (`disconnect.strata_world.biome_design_singleplayer` translation key)
      NOTE (code fix applied): Fixed enforcement logic — now compares joining player UUID against server host profile UUID. Non-host players are disconnected with the translation key message. Needs re-test.
- [ ] Attempting to join from a second client while the Design World is running is rejected
      NOTE (code fix applied): Same fix as above — second player should now be rejected. Needs re-test.


---

## Wand — Editor Open

- [x] Right-clicking in open air opens the Biome Editor screen
- [x] All five tabs are visible and navigable: **Visual**, **Terrain**, **Features**, **Spawns**, **Export**
- [ ] Display name field in header is editable inline (click pencil icon)
      NOTE (code fix applied): Made displayNameField visible with setDrawsBackground(true) and repositioned with "Name:" label. Needs re-test.
- [x] Auto-derived biome ID updates when display name changes
      (e.g. typing `"Frost Peaks"` produces `strata_world:frost_peaks`)
      NOTE: This works in the Export/Save tab, but is not reflected in the F3 debug screen.
- [ ] Header shows current biome ID below the display name
      NOTE: Only shows biome ID.

---

## Wand — Biome Sampling

- [ ] Right-clicking terrain samples the biome at the player's current position
      NOTE (code fix applied): Implemented BiomeSamplePayload network packet and BiomeEditorWandHandler sampling logic. Block hits send sampled biome data; air hits open editor. Needs re-test.
- [ ] Editor fields populate with sampled biome values (colors, noise parameters)
      NOTE (code fix applied): Added BiomeEditorState.fromSampleJson() to populate state from sampled JSON. Needs re-test.
- [ ] Header shows `"Loaded template: minecraft:<biome>"` after sampling
- [ ] Sampling a vanilla biome (Badlands, Forest, Flower Forest, etc.) works
- [ ] Unsaved-change prompt appears when a draft is in progress and sampling would replace it
      NOTE (partially resolved): Sampling implementation complete. Unsaved-change prompt not yet implemented — sampling currently replaces draft without warning.

---

## Preview Zone & Regen

- [ ] Changing a Terrain slider (noise parameter) triggers chunk regen after approximately 3 seconds
      NOTE (code fix applied): Fixed PreviewZoneManager debounce timing and regenerating flag. Added regenStartTime with 1-second minimum indicator display. Needs re-test.
- [ ] Rapid slider adjustments reset the timer (no mid-drag regen -- only fires after the 3 s window)
- [ ] `"Refreshing preview..."` HUD indicator appears during regen
- [ ] Preview zone size visually matches the current render distance setting

---

## Biome Blending Toggle

- [ ] **Disabled:** edited biome generates everywhere across the entire loaded world
      NOTE (code fix applied): Added "Blending: ON/OFF" toggle button in TerrainTab. Needs re-test.
- [ ] **Enabled:** vanilla biomes appear beyond the current render distance, allowing transition evaluation

---

## Reset World

- [ ] Reset World button appears in the editor toolbar
      NOTE (code fix applied): Added "Reset World" button in TerrainTab with two-click confirmation. Needs re-test.
- [ ] Clicking it triggers a confirmation prompt before any action
      NOTE (code fix applied): Button changes to "Confirm Reset?" on first click, executes on second click.
- [ ] Confirming clears terrain and regenerates using the current parameters
      NOTE (code fix applied): Implemented resetWorld() in PreviewZoneManager — deletes .mca files and triggers worldRenderer.reload(). Needs re-test.
- [ ] Cancelling leaves the world unchanged

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
- [ ] `"Export Strata-Pack"` produces a `.stratapack` archive containing the biome JSON,
      `en_us.json`, and a valid `strata-pack.json` manifest with `name`, `author`, and `version`
      NOTE (code fix applied): Added exportStrataPack() in ExportTab producing .stratapack ZIP with manifest, biome JSON, and lang entry. Also fixed "Set biome name first" validation to auto-derive biome ID before checking. Needs re-test.
- [ ] Unexported-change indicator clears after a successful export

---

## Notes

- [x] RGB Sliders and buttons in Visual tab are very dark and hard to read.
      NOTE (resolved): Fixed render order — moved super.render() after background fills so widgets are drawn on top of backgrounds instead of underneath.
- [x] Sliders and buttons in Terrain tab are dark and hard to read
      NOTE (resolved): Same render order fix as above.
- [x] Text box and button in Features tab are dark and hard to read
      NOTE (resolved): Same render order fix. Also added "Feature:" label and offset text field to avoid overlap.
- [ ] Clicking in text box in Features tab doesn't display a list like we had originally spec'd.
      NOTE: Two-column searchable picker from spec not yet implemented.
- [x] Mob Spawns Tab: same button and text field darkness issues. 'Entity' text displays partially over the text box.
      NOTE (resolved): Same render order fix. Added "Entity:" label and offset text field from x+10 to x+60 to fix text overlap.
- [x] Export/Save Tab: Same button and text field darkness issues. .JSON preview is readable but still too dark.
      NOTE (resolved): Same render order fix. Added content area background fill.

```
Date tested: 2025/03/05 11:55
Tester: Jeff Hutting
MC version: 1.21.11
Strata version: 0.1.0

Findings:

See Notes section above.

Code fixes applied: 2026/03/05
Items marked "code fix applied" or "resolved" have implementation changes
but need in-game re-testing to confirm and check off.

```
