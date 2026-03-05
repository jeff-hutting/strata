# Strata — Claude Code Workflow Guide

This guide describes how to use Claude Code effectively for Strata development. Read this before starting any development session.

---

## Overview

Claude Code is an AI coding agent that runs in your terminal or VS Code. For Strata, it does the heavy lifting of writing Java, configuring Gradle, and fixing build errors — while you direct the work, review changes, and make decisions.

**Your role:** Describe *what* to build, review diffs, approve changes, and make design decisions.
**Claude Code's role:** Write the code, run builds, fix errors, and stay consistent with Strata conventions.

---

## Design Session Protocol (Cowork ↔ Claude Code)

Cowork (this environment) is where design and documentation happen. Claude Code is where implementation happens. Keep them separate.

**The rule: discuss in Cowork first, write artifacts only after approval.**

```
1. DISCUSS    — Talk through the idea in Cowork. Tradeoffs, options, implications.
2. DECIDE     — Land on a clear decision. Jeff confirms: "yes, do it."
3. WRITE      — Cowork updates the relevant doc(s). Claude Code is never given
                 ambiguous or mid-debate documentation.
```

This matters because:
- Updating a spec mid-discussion, then changing course, creates doc drift and wasted edits.
- Claude Code reads the docs as ground truth. Docs should only be written when the decision is settled.
- It keeps the chat history clean — decisions are visible and reversible before they're committed.

**The exception:** Additive changes that don't touch existing content (e.g., a new section in a doc that is entirely self-contained) can be written immediately after the idea is proposed, since there's nothing to un-do if the idea changes.

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

Write your findings to docs/reviews/strata-<module>-<phase>-review.md using
this structure for each issue:
  ### [file path]:[line number]
  **Issue:** what's wrong
  **Fix:** what to do
If no issues are found, write a brief "Passed" summary instead.
Do NOT modify any code — report only. I will decide what to fix.
```

When the review file is written, commit it:
```bash
git add docs/reviews/strata-<module>-<phase>-review.md
git commit  # use /commit for format reference
git push
```
Stage the review file only — do not stage implementation code.

#### The Reviewer — strata-world Phase 1 (Module-Specific)
```
Read docs/ARCHITECTURE.md and docs/mods/strata-world/SPEC.md.

Review the Phase 1 implementation in strata-world/ and check for:

1. Naming conventions — mod ID strata_world, package io.strata.world,
   class names per ARCHITECTURE.md Section 5.
2. Biome pipeline integrity — JSON → StrataBiomes → StrataWorldgen →
   StrataWorldEvents → overworld. Each step must be traceable.
3. Noise parameters — temperature, humidity, continentalness, erosion,
   weirdness, and depth values for VerdantHighlands must live in
   WorldConfig, not hardcoded in biome or worldgen classes.
4. Fabric API usage — biome registration must go through Fabric's
   BiomeModifications API. No direct access to vanilla's BiomeSource
   or NoiseBasedChunkGenerator internals.
5. No direct cross-module Java dependencies outside of strata-core.
6. fabric.mod.json declares strata-core as a dependency with the
   correct mod ID.
7. Javadoc on all public methods in StrataBiomes and StrataWorldgen.
8. All Phase 1 acceptance criteria from the spec are met.

Write your findings to docs/reviews/strata-world-phase1-review.md using
this structure for each issue:
  ### [file path]:[line number]
  **Issue:** what's wrong
  **Fix:** what to do
If no issues are found, write a brief "Passed" summary instead.
Do NOT modify any code — report only.
```

When the review file is written, commit it:
```bash
git add docs/reviews/strata-world-phase1-review.md
git commit  # use /commit for format reference
git push
```
Stage the review file only — do not stage implementation code.

#### The Fix Session
```
Read docs/ARCHITECTURE.md, docs/mods/strata-<module>/SPEC.md, and
docs/reviews/strata-<module>-<phase>-review.md.

Fix all issues listed in the review. After fixing, run
./gradlew :strata-<module>:build and confirm it passes.
Do not change any code unrelated to the review findings.

When done, write a summary of changes to
docs/reviews/strata-<module>-<phase>-fixes.md using this structure:

  ### [file path]
  **Changed:** what was done and why
```

When `./gradlew :strata-<module>:build` passes and the fixes file is written, commit everything:
```bash
git add <all changed implementation files>
git add docs/reviews/strata-<module>-<phase>-fixes.md
git commit  # use /commit for format reference
git push
```

#### The Tester
```
Read docs/ARCHITECTURE.md, docs/mods/strata-<module>/SPEC.md, and
docs/reviews/strata-<module>-<phase>-fixes.md.

Write tests for the recently implemented [feature] in strata-<module>/.
- Use Fabric's GameTest framework for anything that requires a running game world
- Use JUnit 5 for pure logic/utility tests that don't need Minecraft running
- Cover every acceptance criterion listed at the bottom of the spec
- Test edge cases: null inputs, empty collections, boundary values

Do NOT modify any implementation code — only add test files under src/test/.
Run ./gradlew :strata-<module>:test after writing and fix any test compilation errors.

Write a test report to docs/reviews/strata-<module>-<phase>-test-report.md:
  ### [test class]
  **Covers:** which acceptance criteria or edge cases
  **Result:** passed / failed / skipped (with reason)

Also write a human test checklist to docs/reviews/strata-<module>-<phase>-human-test-checklist.md.
This covers anything that requires in-game verification or has a subjective outcome that automated
tests cannot assess. Use GitHub-flavored markdown checkboxes. Structure it as:

  # <Module> <Phase> — Human Test Checklist
  _Check off each item in-game before closing the phase. Commit this file when complete._

  ## Biome / Feature Registration
  - [ ] [specific thing visible in F3 or tab-complete]

  ## Terrain / Generation
  - [ ] [subjective terrain shape, transition quality, etc.]

  ## Visuals / Atmosphere
  - [ ] [colors, fog, sky — things only a human can judge]

  ## Spawning
  - [ ] [correct mobs present, no wrong mobs, counts feel right]

  ## Notes
  _Space for unexpected findings._

Only include sections relevant to the module. Omit sections that don't apply.
```

When the test run is clean and both files are written, commit:
```bash
git add src/test/
git add docs/reviews/strata-<module>-<phase>-test-report.md
git add docs/reviews/strata-<module>-<phase>-human-test-checklist.md
git commit  # use /commit for format reference
git push
```

#### The Scribe
```
Read docs/ARCHITECTURE.md, docs/mods/strata-<module>/SPEC.md, and
docs/reviews/strata-<module>-<phase>-fixes.md.

The implementation of [feature] is now complete. Update the following to reflect
what was actually built (implementation may have deviated from the spec):
1. Add or update Javadoc on all public classes and methods in strata-<module>/
2. Update docs/mods/strata-<module>/SPEC.md if the implementation differs from the spec
3. Update the roadmap checklist in docs/ARCHITECTURE.md to check off completed items

Do NOT change any Java implementation code.
Output is the updated files — no separate report needed.
```

When all documentation is updated, commit:
```bash
git add docs/
git commit  # use /commit for format reference
git push
```
Stage documentation files only — do not stage implementation code.

---

### File Output Convention

**Every agent that produces output another agent will read must write to a file.**
Terminal output disappears into scroll. Files persist, can be committed, and
can be passed directly to the next session.

| Session | Output file |
|---|---|
| Reviewer | `docs/reviews/<module>-<phase>-review.md` |
| Fix | `docs/reviews/<module>-<phase>-fixes.md` |
| Tester | `docs/reviews/<module>-<phase>-test-report.md` |
| Tester | `docs/reviews/<module>-<phase>-human-test-checklist.md` |
| Scribe | Updates spec and ARCHITECTURE.md directly — no separate file |

---

### When to Run the Full Gate vs. a Lighter Process

Not every change needs all five roles. Use judgment:

| Change type | Roles needed |
|---|---|
| New phase implementation from spec | Reviewer → Fix → Tester → Scribe |
| New architectural pattern (first of its kind) | Reviewer → Fix → Tester → Scribe |
| Bug fix on a `fix/*` branch | Build must pass; Scribe if the fix changes how something works |
| Feature migration (same behaviour, different mechanism) | Build must pass; Scribe to document the new pattern |
| Doc-only change | None — just commit |
| Content addition following an established pattern (e.g. 2nd biome after 1st is solid) | Tester + Scribe; skip Reviewer if pattern is well-established |

**The Scribe is almost always worth running** — doc drift compounds quickly. When in doubt, run it.

**The human test checklist is always required** before merging to main, regardless of which other roles ran.

### Sequential Quality Gate (Recommended Pattern)

Run these focused sessions after any significant feature implementation:

```bash
# Session 1: Implement (Sonnet or Opus)
claude --model claude-sonnet-4-6
> [developer prompt]

# Session 2: Review (Opus) — writes docs/reviews/<module>-<phase>-review.md
claude --model claude-opus-4-6
> [reviewer prompt]

# Session 3: Fix (Sonnet) — reads review.md, writes fixes.md
claude --model claude-sonnet-4-6
> [fix session prompt]

# Session 4: Test (Sonnet) — reads fixes.md, writes test-report.md
claude --model claude-sonnet-4-6
> [tester prompt]

# Session 5: Scribe (Sonnet) — reads fixes.md, updates spec + ARCHITECTURE.md
claude --model claude-sonnet-4-6
> [scribe prompt]
```

---

### Before Merging to Main

After the quality gate is clean (Scribe committed, branch pushed), do one final validation before merging the feature branch to `main`:

```bash
# 1. Build the production jars — always rebuild from the current branch tip.
#    Jars in build/libs/ from a previous session are stale and will not reflect
#    any fixes committed since they were built.
./gradlew clean build

# 2. Copy the built jars to your Minecraft mods folder
#    Remove any older strata-* jars first — Minecraft will error if both old
#    and new versions are present in the mods folder simultaneously.
cp strata-core/build/libs/strata-core-*.jar  ~/Library/Application\ Support/minecraft/mods/
cp strata-world/build/libs/strata-world-*.jar ~/Library/Application\ Support/minecraft/mods/
# (include any other strata-* modules)

# 3. Launch Minecraft normally (not via runClient)
#    — Create a new singleplayer world
#    — Walk around and confirm the module's features work
#    — Watch the log (logs/latest.log) for errors on world load
```

**Why this matters:** `runClient` loads resources through Fabric's dev classpath, which is more forgiving than the production resource pipeline. Format errors in biome JSON (`carvers` array format, placed feature names), structure NBT, and data-pack files can pass `runClient` silently and only surface in a real install. A production jar test is the only reliable gate.

**Minimum check per module:**

| Module | What to verify |
|---|---|
| `strata-world` | New biome appears in-game; no registry errors on world load |
| `strata-core` | No startup crash; config file generated at first launch |
| `strata-rpg` | XP/skill UI visible; no null pointer on first player join |

If this step reveals bugs, open a `fix/<short-description>` branch, fix, re-run the gate cycle, and repeat the jar test before merging.

**Also work through the human test checklist** (`docs/reviews/<module>-<phase>-human-test-checklist.md`) during this step — check off each item, add notes for anything unexpected, and commit the completed checklist before merging.

### Tools for In-Game Validation (strata-world)

**BetterF3** — Replaces the vanilla F3 overlay with a readable version that shows the current biome name (e.g. `strata_world:verdant_highlands`) clearly. Confirmed working with Strata mods.

**Single-biome world** — The most reliable way to confirm a new biome generates without crashing and to evaluate its visuals in isolation. In the world creation screen, set world type to "Single Biome" and select your custom biome. If the world loads, the biome is valid.

**`/locate biome <biome-id>`** — Vanilla command. Use tab-complete to confirm the biome is registered (`strata_world:verdant_highlands` should appear). If it finds coordinates, teleport there with `/tp <x> ~ <z>`. May not find rare biomes near spawn — try from multiple locations or use the single-biome technique instead.

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

## Agent Teams (Phase 3 Kickoff and Beyond)

Agent Teams is an experimental Claude Code feature that goes further than subagents. Subagents report results back to the main agent and cannot talk to each other. Teammates are fully independent Claude Code sessions that share a task list, can message each other directly, and self-claim work without the lead dispatching every step. This makes them better suited for work that requires cross-teammate discussion — parallel reviewers comparing notes, or developers who need to negotiate a shared interface before writing code on each side of it.

**Requirement:** iTerm2 with the `it2` CLI on your PATH (already the case for this project). Agent Teams is already enabled in `.claude/settings.json` via `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`.

### When to Use Agent Teams vs. Subagents

| Situation | Use |
|---|---|
| Parallel independent content (3 new biomes, no shared files) | Subagents — lower overhead |
| Sequential bug fixes and polish on existing code | Neither — single session |
| Finishing a nearly-complete phase | Neither — single session |
| Launching a new phase with multiple independent modules | **Agent Teams** |
| Parallel code review from different specialist lenses | **Agent Teams** (reviewers compare and challenge each other) |
| Debugging with competing hypotheses | **Agent Teams** (teammates argue against each other's theories) |

### The Right Moment for Phase 3

Use Agent Teams **at the start of Phase 3**, not to finish Phase 2. The right trigger is: you have a written Phase 3 spec, Phase 2 is merged to main, and you have 3+ independent new modules or systems to build simultaneously.

Do **not** use Agent Teams for:
- Polishing or fixing items in a nearly-complete phase (file conflicts, no real parallelism benefit)
- Any task where teammates would need to edit the same file
- Sequential workflows where each step depends on the last

### File Ownership is Mandatory

Two teammates editing the same file will overwrite each other. Before spawning a team, explicitly assign file ownership in the prompt. For strata-world, natural ownership boundaries are:

| Owner | Files |
|---|---|
| Teammate A | A new biome: its JSON file + its registration in `StrataBiomes.java` + its noise params in `StrataWorldgen.java` |
| Teammate B | A different biome — same file types, zero overlap |
| Teammate C | A new editor system: its tab class under `editor/tabs/` + any new state fields in `BiomeEditorState.java` |
| Teammate D | Tests only: `src/test/` — never touches implementation files |
| Teammate E | Docs only: `docs/` — Scribe role, never touches implementation files |

### Phase 3 Kickoff — Lead Prompt Template

Paste this into a fresh Claude Code session (Opus recommended for the lead) at the start of Phase 3:

```
You are the team lead for a Phase 3 strata-world implementation sprint.

Before doing anything else, read:
- docs/ARCHITECTURE.md
- docs/mods/strata-world/SPEC.md
- docs/workflow/CLAUDE_CODE_GUIDE.md (the Agent Teams section)

Your job is to:
1. Plan the parallel work for this sprint based on the Phase 3 spec items listed below.
2. Spawn 3–4 teammates (Sonnet model), assigning each a clearly bounded set of files.
3. Give each teammate an explicit context prompt that includes:
   - Which files they own (list them)
   - Which files they must NOT touch
   - The "Starting a Session" golden rule from CLAUDE_CODE_GUIDE.md
   - Their specific deliverable
4. Synthesize results when teammates complete their work. Report any conflicts or blockers.

Phase 3 items for this sprint: [describe what to build, e.g., "two new biomes: CrimsonBadlands
and FrostPeaks; full PreviewZoneManager server-side regen; biome browser panel in the editor"]

File ownership rules for this project:
- Each biome is one JSON file + one registration block in StrataBiomes.java — assign one biome per teammate.
- Editor tab files (editor/tabs/*.java) are one per teammate — never shared.
- BiomeEditorState.java is high-conflict; only one teammate may touch it.
- Test files (src/test/) and doc files (docs/) are safe for a dedicated teammate each.

Do not start writing code yet — confirm the team plan with me first.
```

### Parallel Review — Specialist Team Template

Use this after a major implementation, instead of (or in addition to) the sequential Reviewer role:

```
Spawn three reviewer teammates, each with a different lens. Use Sonnet for each.
Give each teammate this context first:
  Read docs/ARCHITECTURE.md and docs/mods/strata-world/SPEC.md.
  Your only job is to review — do NOT modify any code.

- Reviewer 1 — Fabric correctness: Fabric API usage, Mixin registrations in strata_world.mixins.json,
  no direct access to vanilla internals, MC 1.21.11 API quirks documented in the handoff prompt.
- Reviewer 2 — State and thread safety: BiomeEditorState mutation patterns, UndoManager snapshot
  timing, client-only code paths, no server-side calls from client mixins.
- Reviewer 3 — Build system and conventions: naming (mod ID strata_world, package io.strata.world),
  no hardcoded config values (all in WorldConfig), Javadoc on public methods,
  conventional commit hygiene.

Have each reviewer write findings to a separate temp file:
  docs/reviews/phase3-review-fabric.md
  docs/reviews/phase3-review-state.md
  docs/reviews/phase3-review-conventions.md

After all three are done, have them discuss any overlapping findings and consolidate into
docs/reviews/strata-world-phase3-review.md. Then report the consolidated list to me.
```

### Competing Hypotheses — Debug Team Template

Use when a bug has multiple plausible root causes and you want to investigate them in parallel:

```
There is a bug: [describe symptom exactly].

Spawn 3 teammates to investigate competing hypotheses. Each teammate should:
1. Read docs/ARCHITECTURE.md and the relevant files for their hypothesis.
2. Argue for their hypothesis with specific evidence from the code.
3. Then actively try to find evidence that *disproves* their own hypothesis.
4. Report their conclusion (confirmed / disproved / inconclusive) with code references.

- Teammate 1 hypothesis: The bug is in [area A — e.g., the mixin not applying correctly]
- Teammate 2 hypothesis: The bug is in [area B — e.g., the debounce timer firing at the wrong time]
- Teammate 3 hypothesis: The bug is in [area C — e.g., state not being read on the correct thread]

After each teammate reports, discuss findings together. Write the consensus root cause
and proposed fix to docs/reviews/debug-[short-name].md, then report to me.
```

### Known Limitations

- **`/resume` and `/rewind` do not restore in-process teammates.** After resuming a session, the lead may try to message teammates that no longer exist. Spawn fresh teammates after any resume.
- **Task status can lag.** If a teammate finishes but doesn't mark its task done, the lead may stall. Nudge the lead: *"Check if Teammate 2's work is actually complete and update the task list."*
- **One team per session.** Clean up a team before starting a new one.
- **Token cost scales with team size.** 3–4 teammates is the right ceiling for this codebase. Five or more rarely pays off.

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

   > **runClient is not a substitute for jar validation.** The dev environment loads
   > resources differently than a standard Minecraft install and can mask format errors
   > in biome JSON, structure templates, and data-pack files. See "Before Merging to Main"
   > below for the required jar validation step.

5. ITERATE
   Bring bugs back to Claude Code with specific descriptions.
   Update the spec if the design changed during implementation.
```

---

## Committing Changes

Strata uses [Conventional Commits](https://www.conventionalcommits.org/) for a clean, searchable git history.

### Format

```
<type>(<scope>): <short description>

# Examples:
feat(strata-world): add VerdantHighlands biome
fix(strata-core): fix asset registry NPE
docs(workflow): add commit workflow guidance
refactor(strata-core): extract BiomeUtils
test(strata-world): add biome registration tests
```

**Types:** `feat` · `fix` · `docs` · `refactor` · `test` · `chore`
**Scopes:** match the module name (`strata-core`, `strata-world`, etc.) or use `workflow` / `docs` for documentation-only changes.

### When to Commit

- **After a successful `./gradlew build`** — never commit broken code. If the build fails, fix it first.
- **After a Reviewer session** — commit with a note that review passed.
- **After Cowork doc updates** — documentation changes get their own commit, separate from code changes.
- **One logical unit per commit** — scaffold, single feature, single fix. Not one giant commit per session.

### Who Writes the Message

Each role prompt now includes a commit step at the end — commit happens within the session, not after. Use the `/commit` slash command for the message format:

```
/commit
```

Claude Code will review staged changes, propose a conventional commit message using Strata's scopes and types, run `git commit`, and push.

> **Cowork applies this too.** Doc changes made here (in Cowork) should be committed before starting a Claude Code session that will read those docs. Don't send Claude Code to read a spec that hasn't been committed yet — it should always be working from a clean, committed state.

---

## Common Mistakes to Avoid

**Don't start without context.** Always make Claude Code read the architecture doc first. Without it, you'll get code that doesn't follow Strata conventions.

**Don't let Claude Code work on multiple modules at once** (unless using intentional parallel subagents). Cross-module changes are easy to get wrong.

**Don't skip spec writing.** The clearer your spec, the better the code. Vague prompts produce vague code that needs heavy revision.

**Don't accept builds that don't compile.** Always run `./gradlew build` before wrapping up a session. Never leave broken code in the repo.

**Don't hardcode values.** If Claude Code hardcodes a damage multiplier, level cap, or any balance value, push back and ask it to move the value to the module's config class.

---

## Starting a New Cowork Session

When this chat gets long or you're starting fresh, open a new Cowork chat and paste this as your first message:

```
We are continuing development on the Strata Minecraft mod ecosystem.

Please read the following files before we discuss anything:
- docs/ARCHITECTURE.md
- docs/workflow/CLAUDE_CODE_GUIDE.md

Then read whichever module specs are relevant to what we're working on:
- docs/mods/strata-core/SPEC.md
- docs/mods/strata-world/SPEC.md
- docs/mods/strata-creator/DESIGN_INTENT.md

Once you've read them, confirm your understanding of the current project state —
what has been built vs. what is still spec-only.
```

The confirmation step is important — it surfaces any gaps in understanding before you start giving instructions.

**When to split chats:** At natural stopping points — when a Claude Code session finishes and you're back in Cowork to plan the next phase. Each chat should map roughly to one design phase, with docs committed and up to date before the new chat begins.

---

## File Reference

| File | Purpose |
|---|---|
| `docs/ARCHITECTURE.md` | Master ecosystem document — read at every session start |
| `docs/mods/<module>/SPEC.md` | Per-feature specification — read before implementing |
| `docs/workflow/CLAUDE_CODE_GUIDE.md` | This file |
| `docs/reviews/<module>-<phase>-review.md` | Reviewer output |
| `docs/reviews/<module>-<phase>-fixes.md` | Fix session output |
| `docs/reviews/<module>-<phase>-test-report.md` | Tester output — automated test results |
| `docs/reviews/<module>-<phase>-human-test-checklist.md` | Tester output — in-game / subjective checks for Jeff to complete |
| `docs/conventions/` | Detailed coding conventions (naming, patterns, etc.) |
| `.claude/commands/commit.md` | `/commit` slash command — conventional commit format for Strata |
| `.claude/settings.json` | Committed project permissions — pre-approves common dev commands; enables `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS` |

---

*Keep this guide updated as you develop new workflow patterns. The best prompts you discover should be added here for future sessions.*
