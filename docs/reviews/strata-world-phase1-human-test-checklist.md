# strata-world Phase 1 — Human Test Checklist

_Check off each item in-game before closing the phase. Commit this file when complete._

---

## Biome Registration

- [x] Biome appears in `/locate biome` tab-complete as `strata_world:verdant_highlands`
- [x] Biome generates in a single-biome creative world without crashing
- [x] BetterF3 confirms `strata_world:verdant_highlands` when standing in biome

## Terrain / Generation

- [x] Terrain profile reads as rolling highlands — not flat, not mountainous
- [ ] Transitions to adjacent vanilla biomes look natural (no hard seams)
- [x] No void holes, floating islands, or generation artifacts visible

## Visuals / Atmosphere

- [ ] Sky color is distinctly blue-green (not vanilla blue)
- [ ] Fog color gives a warm highland haze feeling
- [x] Grass color is deep green and noticeably different from vanilla forest/plains
- [x] Foliage color matches grass tone (no jarring mismatch)

## Features

- [x] Oak and birch trees present (`trees_birch_and_oak_leaf_litter`)
- [ ] Flowers visible (`flower_default` — dandelion, poppy, azure bluet)
- [x] Grass patches present (`patch_grass_forest`, `patch_tall_grass`)
- [x] Mushrooms occasionally visible (`brown_mushroom_normal`, `red_mushroom_normal`)
- [x] Sugar cane near water (`patch_sugar_cane`)
- [x] Pumpkins occasionally visible (`patch_pumpkin`)
- [x] Lava lakes present underground and surface (`lake_lava_underground`, `lake_lava_surface`)
- [x] Caves present and correctly shaped (`minecraft:cave`, `minecraft:canyon`)
- [x] Standard ores present underground (coal, iron, gold, redstone, diamond, lapis, copper)

## Spawning

- [x] Passive mobs present in daytime (sheep, pig, cow, chicken visible in screenshot)
- [x] Wolf and fox spawning (confirmed in spawner config)
- [x] Creature count feels right (163 creatures confirmed in-game)
- [x] Hostile mobs spawn at night (spider, zombie, skeleton, creeper, enderman, witch)
- [x] Glow squid present underground (underground water creature count: 5 confirmed)
- [x] No obviously wrong mobs spawning (polar bears, dolphins, striders, etc.)

## Notes

- Did not observe azure bluet, but did observe dandelions and poppies.
- Did not test biome transition (loaded a single biome world for testing).
- Sky and Fog colors are subtle, if different at all. We may need to revisit for Phase 2 tuning.

- Single-biome world was required to reliably locate and test the biome in Phase 1.
  The `/locate biome` command did not find it within default search radius in a normal world.
- Features (trees, ores, etc.) are registered via `StrataWorldFeatures.java` using
  Fabric's `BiomeModifications` API. Inline feature arrays in the biome JSON are
  intentionally empty to avoid feature order cycle crashes with vanilla biomes.
