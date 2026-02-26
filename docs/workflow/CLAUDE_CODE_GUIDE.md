# Strata — Claude Code Workflow Guide

This guide describes how to use Claude Code effectively for Strata development. Read this before starting any development session.

---

## Overview

Claude Code is an AI coding agent that runs in your terminal or VS Code. For Strata, it does the heavy lifting of writing Java, configuring Gradle, and fixing build errors — while you direct the work, review changes, and make decisions.

**Your role:** Describe *what* to build, review diffs, approve changes, and make design decisions.
**Claude Code's role:** Write the code, run builds, fix errors, and stay consistent with Strata conventions.

---

## Model Selection

Claude Code supports multiple Claude models. For Strata, the right model depends on what you're doing. **Always set the model at the start of a session** — don't switch mid-session.

### Opus — Use for Design and Hard Problems

```bash
claude --model claude-opus-4-6
# or inside a session:
/model claude-opus-4-6
```

Use Opus when:
- **Scaffolding a new module for the first time.** The structural decisions made during scaffolding are hard to undo.
- **Designing a new system** — event bus architecture, RPG data models, worldgen noise strategy, skill tree structures. Anything where you're making long-tail design decisions.
- **Debugging hard problems** — Mixin failures, worldgen artifacts, mysterious runtime crashes that don't have obvious causes.
- **World generation code.** Noise functions, biome placement, and terrain generation involve complex spatial reasoning that Opus handles better.
- **`strata-creator` development.** The in-game GUI system is the most architecturally complex module.

### Sonnet — Use for Implementation and Routine Work

```bash
claude --model claude-sonnet-4-6
# or inside a session:
/model claude-sonnet-4-6
```

Use Sonnet when:
- **Implementing a fully-written spec.** If the spec is detailed and unambiguous, Sonnet executes it reliably.
- **Adding content that follows an established pattern** — a new biome after the first three are built, a new skill following existing skill structure, a new config option.
- **Fixing compilation errors** from a MC version update.
- **Routine Gradle and build work.**

### The Practical Rule

Write your spec (in Cowork) → open Claude Code with **Opus 4.6** (`claude-opus-4-6`) to scaffold and design → switch to **Sonnet 4.6** (`claude-sonnet-4-6`) for content implementation once patterns are established.

The spec-first workflow pairs well with this: Opus does the hard thinking upfront so Sonnet has a clear enough target to execute without surprises.

---

## Recommended Setup

### CLI (Primary Workflow)
```bash
cd ~/dev/strata
claude --model claude-opus-4-6      # for design/scaffold sessions
claude --model claude-sonnet-4-6    # for implementation sessions
```
Fast, low-overhead, and gives you full control. Claude Code shows diffs inline in the terminal before applying changes — review them carefully before accepting.

### VS Code (Optional)
1. Install [VS Code](https://code.visualstudio.com/)
2. Install the **Claude Code extension** from the VS Code marketplace
3. Open the `strata/` workspace root in VS Code

Useful if you want side-by-side file browsing while Claude Code works, or if you prefer a graphical diff view. Identical capabilities to the CLI — personal preference only.

---

## Starting a Session — The Golden Rule

**Always start every Claude Code session with this prompt (adapt as needed):**

```
Read docs/ARCHITECTURE.md and the relevant spec in docs/mods/<module-name>/ before doing anything else. We are working on the strata-<module> module today. Here is what I want to accomplish: [your task description]
```

This ensures Claude Code understands:
- The full ecosystem context
- Naming conventions and package structure
- The design philosophy (vanilla-first, data-driven, etc.)
- What already exists before writing new code

---

## Session Types

### 1. Scaffold a New Module
Use when starting a brand new Strata module from scratch.

**Prompt template:**
```
Read docs/ARCHITECTURE.md. Scaffold a new Fabric mod project for strata-<module>.
Use the conventions in the architecture doc (mod ID: strata_<module>, package: io.strata.<module>,
Java 21, current Fabric Loom).

Before writing any version numbers, fetch the current Minecraft version, Yarn mappings,
Fabric Loader version, and Fabric API version from https://fabricmc.net/develop/ — do not
use any version numbers from the documentation, as they may be out of date.

Create the standard Fabric project structure with:
- build.gradle
- fabric.mod.json
- Main mod initializer class
- A placeholder README
Do not implement any features yet — just the scaffolding.
```

> **Why no version numbers in the docs?** MC and Fabric versions change frequently. The docs intentionally omit specific version strings — always treat fabricmc.net/develop/ as the source of truth.

### 2. Implement a Spec
Use when you have a written spec and want Claude Code to build it.

**Prompt template:**
```
Read docs/ARCHITECTURE.md and docs/mods/strata-<module>/SPEC.md.
Implement [specific feature from the spec]. Follow all naming conventions and design principles
in the architecture doc. After implementing, run ./gradlew build and fix any errors.
```

### 3. Fix a Build or Runtime Error
Use when something is broken.

**Prompt template:**
```
Read docs/ARCHITECTURE.md. The strata-<module> module has an error: [paste error message or describe symptom].
Diagnose and fix the issue. Do not change any unrelated code.
```

### 4. Update for a New Minecraft Version
Use when a new MC version releases and you need to update.

**Prompt template:**
```
Read docs/ARCHITECTURE.md, paying special attention to the Update Strategy section.
A new Minecraft version has released and Fabric Loader is now compatible.

First, fetch the current Minecraft version, Yarn mappings, Fabric Loader version, and
Fabric API version from https://fabricmc.net/develop/ — use those exact values.

Update the root gradle.properties with the new versions, then run ./gradlew build from
the repo root. Fix all compilation errors in dependency order (strata-core first).
List every breaking change you encounter as you go.
```

---

## The Strata Dev Team

Claude Code can take on different roles depending on what a session needs. Think of each role as a specialist you bring in for a specific job. You can run roles sequentially (implement → review → test) or use subagents to parallelize independent work.

### The Five Roles

**The Architect** (`claude-opus-4-6`)
Scaffolds new modules, makes long-term design decisions, debugs hard problems. Always Opus — these decisions have consequences.

**The Developer** (`claude-sonnet-4-6`)
Implements features from a written spec. Sonnet is fast and reliable here when the spec is clear.

**The Reviewer** (`claude-opus-4-6`)
Reviews completed code against Strata conventions. Opus catches subtle issues Sonnet might miss.

**The Tester** (`claude-sonnet-4-6`)
Writes unit and integration tests after implementation. Sonnet is sufficient for well-defined test cases.

**The Scribe** (`claude-sonnet-4-6`)
Updates documentation, JavaDoc, and spec files to reflect what was actually built. Keeps docs from drifting.

---

### Role Prompt Templates

#### The Reviewer
```
Read docs/ARCHITECTURE.md and docs/mods/strata-<module>/SPEC.md.

Review the recently implemented code in strata-<module>/ and check for:
1. Naming conventions (Section 5 of ARCHITECTURE.md — mod IDs, packages, class names)
2. No hardcoded config values — all tunable values must live in <Module>Config
3. Fabric API used correctly — no direct access to vanilla internal classes
4. Cross-module events routed through strata-core's StrataEvents (not direct calls)
5. No direct cross-module Java dependencies (only via strata-core interfaces)
6. Public API classes have Javadoc on every public method
7. Acceptance criteria in the spec are fully met

Report every issue found with the file path and line number.
Do NOT modify any code — report only. I will decide what to fix.
```

#### The Tester
```
Read docs/ARCHITECTURE.md and docs/mods/strata-<module>/SPEC.md.

Write tests for the recently implemented [feature] in strata-<module>/.
- Use Fabric's GameTest framework for anything that requires a running game world
- Use JUnit 5 for pure logic/utility tests that don't need Minecraft running
- Cover every acceptance criterion listed at the bottom of the spec
- Test edge cases: null inputs, empty collections, boundary values

Do NOT modify any implementation code — only add test files under src/test/.
Run ./gradlew :strata-<module>:test after writing and fix any test compilation errors.
```

#### The Scribe
```
Read docs/ARCHITECTURE.md and docs/mods/strata-<module>/SPEC.md.

The implementation of [feature] is now complete. Update the following to reflect
what was actually built (implementation may have deviated from the spec):
1. Add or update Javadoc on all public classes and methods in strata-<module>/
2. Update docs/mods/strata-<module>/SPEC.md if the implementation differs from the spec
3. Update the roadmap checklist in docs/ARCHITECTURE.md to check off completed items

Do NOT change any Java implementation code.
```

---

### Sequential Quality Gate (Recommended Pattern)

Run three focused sessions after any significant feature implementation:

```bash
# Session 1: Implement (Sonnet)
claude --model claude-sonnet-4-6
> [developer prompt]

# Session 2: Review (Opus)
claude --model claude-opus-4-6
> [reviewer prompt]

# Session 3: Fix issues found in review (Sonnet)
claude --model claude-sonnet-4-6
> Fix the issues identified in the review: [paste review output]

# Session 4: Test (Sonnet)
claude --model claude-sonnet-4-6
> [tester prompt]
```

---

## Using Subagents (Parallel Work)

Claude Code can spawn parallel subagents to tackle multiple tasks simultaneously. This is powerful for Strata because different parts of a module are often independent.

### When to Use Subagents
- Setting up boilerplate + writing first feature at the same time
- Implementing multiple independent registries in parallel
- Writing tests while writing implementation

### How to Request Subagents
```
Using parallel subagents, do the following simultaneously:
- Agent 1: Implement the biome registration system in strata-world
- Agent 2: Set up the Gradle project structure and fabric.mod.json for strata-world
- Agent 3: Write the WorldConfig TOML configuration class in strata-core
Tell me when all three are complete before proceeding.
```

### Parallel Content Pattern (Most Common Use)
Once the biome/skill/structure *system* is built, adding new *content* is highly parallelizable. Each piece of content is independent:

```
Read docs/ARCHITECTURE.md and docs/mods/strata-world/SPEC.md.

Using parallel subagents, implement three new biomes simultaneously.
Each agent should follow the biome pipeline defined in the spec exactly.

- Agent 1: Implement VerdantHighlands (rolling green hills, dense oak/birch canopy,
  custom tall grass features, #5a7a2e fog color)
- Agent 2: Implement CrimsonBadlands (arid red mesas, sparse dead bushes,
  terracotta layers, #c0392b sky tint)
- Agent 3: Implement FrostPeaks (high-altitude snow biome, spruce/pine trees,
  powder snow patches, #a8d8ea sky color)

Each agent: create the JSON biome file, register it in StrataBiomes, add noise
parameters, and verify it compiles. Report completion status individually.
```

### Subagent Caution
Subagents are most useful when tasks are truly independent. If Agent 2's output is needed by Agent 1, run them sequentially instead. Always review all subagent output before asking Claude Code to continue.

---

## Reviewing Claude Code's Work

Claude Code will show you diffs of every file it changes. **Always review these before accepting.**

Key things to check:
- Are naming conventions followed? (mod IDs, package names, class names — see ARCHITECTURE.md)
- Are any vanilla internals being accessed directly? (Should always go through Fabric API)
- Are config values hardcoded? (They should always be in a Config class or JSON)
- Does the new code wire up through `strata-core`'s event bus where appropriate?

If something looks wrong, tell Claude Code specifically: *"The class name should follow StrataXxx convention"* or *"This value should be in RpgConfig, not hardcoded."*

---

## Build Commands Reference

Strata uses a monorepo with a Gradle multi-project build. Most commands can be run from either the **repo root** (affects all modules) or from **inside a module directory** (affects only that module).

```bash
# --- From repo root (strata/) ---

# Build ALL modules
./gradlew build

# Build a specific module only
./gradlew :strata-core:build
./gradlew :strata-world:build

# Clean all modules
./gradlew clean

# --- From inside a module directory (e.g., strata-core/) ---

# Build this module only
./gradlew build

# Launch dev Minecraft client with this module loaded
./gradlew runClient

# Launch dev Minecraft server with this module loaded
./gradlew runServer

# Clean this module's build artifacts
./gradlew clean

# Regenerate IDE sources (if VS Code/IntelliJ gets confused)
./gradlew genSources
```

**Important:** When doing a MC version update, always run `./gradlew build` from the **repo root** to catch all breakages across all modules in one pass.

---

## Spec-First Development Workflow

The most effective way to work with Claude Code on Strata:

```
1. SPEC (in Cowork or a text editor)
   Write docs/mods/<module>/SPEC.md describing the feature in plain English.
   Include: what it does, what data it stores, how it integrates with other modules,
   what config options exist, and any edge cases.

2. REVIEW
   Read your spec. Ask: is anything ambiguous? Would Claude Code know exactly what to build?
   If not, add more detail.

3. BUILD (in Claude Code)
   Hand Claude Code the spec. Let it implement.
   Watch the diffs. Ask questions if something looks wrong.

4. TEST
   Run ./gradlew runClient. Test in-game.
   Log any bugs or unexpected behavior.

5. ITERATE
   Bring bugs back to Claude Code with specific descriptions.
   Update the spec if the design changed during implementation.
```

---

## Common Mistakes to Avoid

**Don't start without context.** Always make Claude Code read the architecture doc first. Without it, you'll get code that doesn't follow Strata conventions.

**Don't let Claude Code work on multiple modules at once** (unless using intentional parallel subagents). Cross-module changes are easy to get wrong.

**Don't skip spec writing.** The clearer your spec, the better the code. Vague prompts produce vague code that needs heavy revision.

**Don't accept builds that don't compile.** Always run `./gradlew build` before wrapping up a session. Never leave broken code in the repo.

**Don't hardcode values.** If Claude Code hardcodes a damage multiplier, level cap, or any balance value, push back and ask it to move the value to the module's config class.

---

## File Reference

| File | Purpose |
|---|---|
| `docs/ARCHITECTURE.md` | Master ecosystem document — read at every session start |
| `docs/mods/<module>/SPEC.md` | Per-feature specification — read before implementing |
| `docs/workflow/CLAUDE_CODE_GUIDE.md` | This file |
| `docs/conventions/` | Detailed coding conventions (naming, patterns, etc.) |

---

*Keep this guide updated as you develop new workflow patterns. The best prompts you discover should be added here for future sessions.*
