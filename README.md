# Strata

A cohesive Minecraft mod ecosystem for Java Edition (Fabric) — built to stay current with every vanilla release.

## Philosophy

Most mod ecosystems fragment over time. Mods fall behind new Minecraft versions at different rates, get abandoned, and get replaced by incompatible alternatives. Strata solves this by owning the full stack: a single, layered ecosystem where every module shares a common foundation and updates together.

## Modules

| Module | Purpose | Status |
|---|---|---|
| `strata-core` | Shared foundation: events, config, registries, data | 🔲 Planned |
| `strata-world` | Custom biomes, terrain, and world generation | 🔲 Planned |
| `strata-structures` | Custom and enhanced structures | 🔲 Planned |
| `strata-rpg` | Customizable RPG systems: stats, skills, progression | 🔲 Planned |
| `strata-creator` | In-game asset design tool for blocks, mobs, and items | 🔲 Planned |

## Documentation

- **[Architecture Overview](docs/ARCHITECTURE.md)** — Ecosystem design, conventions, and update strategy
- **[Claude Code Workflow](docs/workflow/CLAUDE_CODE_GUIDE.md)** — How to use Claude Code to develop Strata
- **Module Specs** — See `docs/mods/` (populated as each module is designed)

## Tech Stack

- Minecraft Java Edition (latest stable)
- Fabric mod loader
- Java 21
- Gradle + Fabric Loom

## Development Status

Pre-development. Architecture and documentation phase.
