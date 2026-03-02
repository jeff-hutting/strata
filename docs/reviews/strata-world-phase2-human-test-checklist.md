# strata-world Phase 2 — Human Test Checklist

_Check off each item in-game before closing the phase._

---

## World Creation

- [x] `Strata: Biome Designer` appears in the world type selector alongside Superflat and Amplified
- [ ] Selecting it and creating a world loads successfully without crashes

---

## First Spawn

- [ ] Strata Wand is in the player's main hand on first spawn
- [ ] Splash message `"Welcome to the Strata Biome Designer. Your wand is ready."` appears
- [ ] Wand is NOT re-given on subsequent spawns / respawns (die and respawn to verify)

---

## Multiplayer Enforcement

- [ ] Attempting to open the Design World to LAN is blocked or shows an appropriate error
      (`disconnect.strata_world.biome_design_singleplayer` translation key)
- [ ] Attempting to join from a second client while the Design World is running is rejected

---

## Wand — Editor Open

- [ ] Right-clicking in open air opens the Biome Editor screen
- [ ] All five tabs are visible and navigable: **Visual**, **Terrain**, **Features**, **Spawns**, **Export**
- [ ] Display name field in header is editable inline (click pencil icon)
- [ ] Auto-derived biome ID updates when display name changes
      (e.g. typing `"Frost Peaks"` produces `strata_world:frost_peaks`)
- [ ] Header shows current biome ID below the display name

---

## Wand — Biome Sampling

- [ ] Right-clicking terrain samples the biome at the player's current position
- [ ] Editor fields populate with sampled biome values (colors, noise parameters)
- [ ] Header shows `"Loaded template: minecraft:<biome>"` after sampling
- [ ] Sampling a vanilla biome (Badlands, Forest, Flower Forest, etc.) works
- [ ] Unsaved-change prompt appears when a draft is in progress and sampling would replace it

---

## Preview Zone & Regen

- [ ] Changing a Terrain slider (noise parameter) triggers chunk regen after approximately 3 seconds
- [ ] Rapid slider adjustments reset the timer (no mid-drag regen — only fires after the 3 s window)
- [ ] `"Refreshing preview…"` HUD indicator appears during regen
- [ ] Preview zone size visually matches the current render distance setting

---

## Biome Blending Toggle

- [ ] **Disabled:** edited biome generates everywhere across the entire loaded world
- [ ] **Enabled:** vanilla biomes appear beyond the current render distance, allowing transition evaluation

---

## Reset World

- [ ] Reset World button appears in the editor toolbar
- [ ] Clicking it triggers a confirmation prompt before any action
- [ ] Confirming clears terrain and regenerates using the current parameters
- [ ] Cancelling leaves the world unchanged

---

## Draft Persistence

- [ ] Closing and reopening the editor (without quitting) restores the previous values
- [ ] Quitting to main menu and reloading the world restores the draft
- [ ] Unexported-change indicator is visible in the header when the draft differs from last export
      (indicator disappears after a successful export)
- [ ] Draft file is written to `saves/<world>/strata_biomes/<name>.draft.json`

---

## Export

- [ ] `"Save Biome JSON"` writes a valid biome JSON to `saves/<world>/strata_biomes/<name>.json`
- [ ] `"Copy JSON to Clipboard"` places the biome JSON text on the system clipboard
- [ ] `"Export Strata-Pack"` produces a `.stratapack` archive containing the biome JSON,
      `en_us.json`, and a valid `strata-pack.json` manifest with `name`, `author`, and `version`
- [ ] Unexported-change indicator clears after a successful export

---

## Notes

_Space for unexpected findings discovered during in-game testing._

```
Date tested:
Tester:
MC version:
Strata version:

Findings:



```
