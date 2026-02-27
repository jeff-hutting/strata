# Strata — Master Ecosystem Architecture

> **Version:** 0.1 (Bootstrap)
> **Target:** Minecraft Java Edition (Fabric)
> **Status:** In development — `strata-core` Phase 1 complete, `strata-world` Phase 1 complete

---

## 1. Vision & Philosophy

Strata is a cohesive Minecraft mod ecosystem built on a single principle: **everything works together, and everything keeps pace with vanilla**.

Most mod ecosystems degrade over time because each mod is independently maintained with no shared foundation. When a new Minecraft version releases, mods break at different rates, get abandoned at different rates, and are replaced by incompatible alternatives. The result is fragmentation.

Strata solves this by:

- **Owning the full stack.** Rather than depending on third-party mods, Strata reimplements (or wraps) every major feature it needs. This means full control over update timing.
- **Building on a shared core.** Every Strata module depends on `strata-core`, which provides shared utilities, configuration, events, and registries. Cohesion is structural, not just aesthetic.
- **Preferring data-driven design.** Wherever Minecraft allows it (biomes, structures, loot tables, advancements), Strata uses JSON/datapack definitions rather than hardcoded logic. This dramatically reduces the code surface that needs to change with each MC version.
- **Updating as a unit.** All Strata modules target the same Minecraft version at the same time. No module ships until all modules compile against the new version. Users always get a complete, compatible set.

---

## 2. Design Principles

### Vanilla-First
Strata enhances Minecraft; it doesn't fight it. New features should feel like they belong in the game. When Mojang introduces new systems (e.g., the data-driven worldgen overhaul in 1.18), Strata migrates to use them rather than maintaining parallel systems.

### Data-Driven Where Possible
New biomes, structures, mob behaviors, RPG stats, and item definitions should be expressible in JSON/YAML/datapack format wherever feasible. This decouples content from code and allows content updates without recompilation.

### Modular but Cohesive
Each Strata module is an independent Fabric mod that can technically be used on its own, but is designed to integrate seamlessly with the others. Modules communicate through `strata-core`'s event bus and shared registries — never through direct cross-module dependencies.

### Documented by Default
Every feature, system, and config option is documented at the time it is built. Documentation lives in `docs/mods/<module-name>/`. Claude Code sessions should always begin by reading the relevant spec before writing any code.

---

## 3. Technical Stack

| Layer | Choice | Reason |
|---|---|---|
| Game | Minecraft Java Edition | Most mature modding ecosystem |
| Mod Loader | Fabric | Fastest vanilla version updates, lightweight, modular |
| Build Tool | Gradle + Fabric Loom | Standard for Fabric mods |
| Language | Java 21 | Current Minecraft JVM target |
| Config | TOML (Cloth Config) | Standard Fabric config UI library |
| Data | JSON / Datapacks | Vanilla-compatible, survives MC updates |
| Version Control | Git | Monorepo — all modules in one repository (see Section 6) |

### Minecraft Version Policy
- Strata always targets the **current latest stable release** of Minecraft Java Edition.
- Snapshot/pre-release support is optional and handled in feature branches.
- When a new MC version releases: update `strata-core` first, then each module in dependency order.

---

## 4. Module Ecosystem

Strata is composed of layered modules. Lower layers must be updated and stable before upper layers can build on them.

```
┌─────────────────────────────────────────────────────┐
│               strata-creator                        │  ← In-game asset design tool
├──────────────┬──────────────┬──────────────┐        │
│ strata-rpg   │strata-world  │strata-structs│        │  ← Feature modules
├──────────────┴──────────────┴──────────────┘        │
│                  strata-core                        │  ← Foundation (required by all)
└─────────────────────────────────────────────────────┘
```

### `strata-core`
**The foundation. All other modules depend on this.**

Responsibilities:
- Shared utility classes (math, NBT helpers, registry wrappers)
- Strata event bus (for cross-module communication without direct dependencies)
- Configuration framework (TOML-based, per-module config files)
- Shared item/block/entity base classes and interfaces
- Version metadata (current target MC version, Strata version)
- Any capability or data attachment system for player/entity data

Build priority: **#1 — must be complete before any other module starts.**

---

### `strata-world`
**Custom biomes, terrain, and world generation.**

Responsibilities:
- Custom biomes with full vanilla biome feature support (vegetation, mobs, sounds, sky colors)
- Custom terrain via Fabric's worldgen API and noise generation
- Custom dimension(s) (optional, later)
- Integration with vanilla's data-driven biome system (Minecraft 1.18+)
- TerraBlender integration for biome injection into the overworld/nether (if appropriate)

Design notes:
- Biome definitions should be expressed as JSON datapacks wherever possible
- Terrain modification should be minimal and surgical to avoid conflicts with vanilla
- Should expose hooks via `strata-core` event bus so other modules can react to biome/world events

Build priority: **#2 — early module, foundational for the world feel.**

---

### `strata-structures`
**Custom and enhanced structures.**

Responsibilities:
- New structure templates (built in-game using structure blocks, saved as NBT)
- Structure variants per biome
- Enhancement/modification of vanilla structures (adding loot, mobs, decorations)
- Structure loot table definitions (JSON)
- Integration with vanilla's structure placement system

Design notes:
- Vanilla's structure system is largely datapack-driven since 1.19; lean into this
- Structure NBT files live in `data/strata/structures/`
- Avoid overwriting vanilla structure NBT; use processors and post-processors instead

Build priority: **#3 — depends on `strata-world` for biome-aware placement.**

---

### `strata-rpg`
**Customizable RPG systems: stats, skills, progression, classes.**

Responsibilities:
- Custom player attributes (strength, dexterity, intelligence, etc.)
- Skill trees with unlock conditions
- Player classes/archetypes (optional at start)
- Experience and leveling systems separate from vanilla XP
- Status effects extension (custom buffs/debuffs)
- Configurable via TOML — server admins can tune all values
- Mod API so other mods can register new skills/stats

Design notes:
- All balance values (damage multipliers, XP curves, etc.) must be config-file-driven, never hardcoded
- The RPG system should feel optional / progressive — players who ignore it shouldn't be penalized
- Hook into `strata-core` for player data persistence (NBT/data attachments)

Build priority: **#4 — depends on `strata-core` for data persistence.**

---

### `strata-creator`
**In-game asset design tool for blocks, mobs, items, and entities.**

Responsibilities:
- Creative-mode GUI for designing new content without leaving the game
- Block designer: choose textures, properties, sounds, behaviors
- Mob/entity designer: configure model, AI behaviors, drops, attributes
- Item designer: stats, durability, enchantability, custom abilities
- Export designs as Strata-compatible JSON definitions
- Import/share designs between worlds or players

Design notes:
- This is the most ambitious module and should be built last
- The GUI will use Fabric's Screen API
- Exported definitions feed back into `strata-world`, `strata-structures`, and `strata-rpg`
- Think of this as a creative IDE inside the game

Build priority: **#5 — depends on all other modules.**

---

## 5. Naming & ID Conventions

### Mod IDs
All Strata mods use the prefix `strata_`:
- `strata_core`
- `strata_world`
- `strata_structures`
- `strata_rpg`
- `strata_creator`

*Note: Fabric mod IDs use underscores, not hyphens.*

### Java Package
All Strata code lives under:
```
io.strata.<module>
```
Examples:
- `io.strata.core.registry.StrataRegistry`
- `io.strata.world.biome.StrataBiomes`
- `io.strata.rpg.skill.SkillTree`

### Resource Namespaces
All Strata assets and data use the namespace matching the mod ID:
- `strata_core:example_item`
- `strata_world:verdant_highlands`
- `strata_rpg:skill/swordmastery`

### Class Naming
- Registries: `Strata<Type>s` (e.g., `StrataBiomes`, `StrataItems`)
- Init classes: `Strata<Module>` (e.g., `StrataWorld`, `StrataRpg`)
- Config classes: `<Module>Config` (e.g., `RpgConfig`, `WorldConfig`)

---

## 6. Repository Structure

Strata uses a **monorepo** — all modules live in a single Git repository with a Gradle multi-project build. This is a firm architectural decision (not TBD) for the following reasons:

- The update strategy requires all modules to compile against the same MC version simultaneously. A monorepo makes this a single `gradle.properties` change rather than five separate repo edits.
- Claude Code can see the entire codebase in one session, enabling correct cross-module changes without manual coordination.
- A single developer (or small team) has no need for isolated repo access per module.
- Splitting into separate repos later is straightforward; consolidating separate repos later is painful.

### Top-Level Layout

```
strata/
├── settings.gradle             ← Declares all subprojects
├── build.gradle                ← Shared config applied to all modules
├── gradle.properties           ← ALL version numbers live here (MC, Fabric, Strata)
├── docs/
│   ├── ARCHITECTURE.md         ← This file
│   ├── mods/                   ← Per-module spec documents
│   │   ├── strata-core/
│   │   ├── strata-world/
│   │   ├── strata-structures/
│   │   ├── strata-rpg/
│   │   └── strata-creator/
│   ├── workflow/               ← Claude Code and development workflow guides
│   └── conventions/            ← Coding and naming conventions
├── strata-core/                ← Module source (created when development begins)
├── strata-world/
├── strata-structures/
├── strata-rpg/
└── strata-creator/
```

### Gradle Multi-Project Structure

The root `gradle.properties` is the single source of truth for all version numbers:

```properties
# Minecraft / Fabric — update ONLY these when a new MC version releases
# ⚠️ Do NOT hardcode version numbers here in the docs — they go stale immediately.
# Always look up current values from https://fabricmc.net/develop/ before scaffolding.
minecraft_version=<current — check fabricmc.net/develop>
yarn_mappings=<current — check fabricmc.net/develop>
loader_version=<current — check fabricmc.net/develop>
fabric_version=<current — check fabricmc.net/develop>

# Strata
strata_version=0.1.0
java_version=21
```

**Important:** The actual `gradle.properties` file in the repo will have real version numbers. The placeholders above are a reminder that these values must be fetched fresh at scaffold time — never copied from documentation.

The root `settings.gradle` registers all modules as subprojects:

```groovy
pluginManagement {
    repositories {
        maven { url 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}

rootProject.name = 'strata'

include 'strata-core'
include 'strata-world'
include 'strata-structures'
include 'strata-rpg'
include 'strata-creator'
```

The root `build.gradle` applies shared configuration to every module (Minecraft dependency, Fabric API, Java version, etc.) so individual module `build.gradle` files only contain what is unique to that module — primarily their inter-module dependencies:

```groovy
// strata-world/build.gradle — only declares what's unique to this module
dependencies {
    modImplementation project(':strata-core')
}
```

This means a MC version bump requires editing **one file** (`gradle.properties`), then running one build from the repo root:

```bash
./gradlew build   # builds all modules from repo root
```

### Per-Module Source Layout

Each module follows standard Fabric project structure:

```
strata-<module>/
├── build.gradle                ← Module-specific deps only (e.g. project(':strata-core'))
├── src/
│   └── main/
│       ├── java/io/strata/<module>/
│       └── resources/
│           ├── fabric.mod.json
│           ├── assets/strata_<module>/
│           └── data/strata_<module>/
```

Note: there is no per-module `gradle.properties`. All versions are declared at the root.

---

## 7. Update Strategy (Vanilla Version Bumps)

When a new Minecraft version releases:

1. **Check Fabric Loader compatibility.** Fabric loader usually updates within hours. Wait for loader update before doing anything else.
2. **Update `strata-core` first.** Run `./gradlew build` and fix all compilation errors. The core is the hardest part and gates everything else.
3. **Update modules in dependency order.** `strata-world` → `strata-structures` → `strata-rpg` → `strata-creator`.
4. **Audit data-driven content.** Check if any vanilla biome/structure/loot JSON formats changed. Update Strata JSON files accordingly.
5. **Test in a clean world.** Verify worldgen, structures, and RPG systems before releasing.
6. **Tag the release** with both the Strata version and the target MC version: `v0.3.0-mc1.22`.

### Minimizing Update Pain
- Never use internal Minecraft classes (net.minecraft.server.* internals that aren't exposed via Fabric API). Always go through Fabric API or Mixins.
- Keep Mixin targets minimal and well-documented. Every Mixin is a potential breakage point.
- Prefer data-driven content over hardcoded content at every opportunity.

---

## 8. Development Roadmap

### Phase 1 — Foundation ✓
- [x] Set up `strata-core` Gradle project
- [x] Implement shared configuration system
- [x] Implement Strata event bus
- [x] Implement player data attachment system
- [x] Write `strata-core` spec document

### Phase 2 — World (Phase 1 complete)
- [x] Set up `strata-world` Gradle project
- [x] Research and spec biome/terrain approach for current MC version
- [x] Implement first custom biome (VerdantHighlands — proof of concept via Mixin + JSON)
- [ ] ~~Implement TerraBlender integration~~ — decided against; using native Fabric Mixin on `VanillaBiomeParameters` instead (see `strata-world` SPEC §2.1 and §3.6)
- [ ] Write full biome library (Phase 3 of `strata-world`)

### Phase 3 — Structures
- [ ] Set up `strata-structures` Gradle project
- [ ] Create first custom structure in-game (structure blocks)
- [ ] Implement structure registration pipeline
- [ ] Implement vanilla structure enhancement system

### Phase 4 — RPG
- [ ] Set up `strata-rpg` Gradle project
- [ ] Spec the attribute and skill system
- [ ] Implement custom player attributes
- [ ] Implement skill tree (basic)
- [ ] Implement leveling system
- [ ] Make all values config-driven

### Phase 5 — Creator
- [ ] Research Fabric Screen API capabilities
- [ ] Spec in-game designer UI
- [ ] Implement block designer (first)
- [ ] Implement item designer
- [ ] Implement mob/entity designer
- [ ] Implement export/import pipeline

---

*This document is a living reference. Update it whenever the architecture evolves. Claude Code sessions should read this file at the start of every development session.*
