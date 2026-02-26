# strata-core — Module Specification

> **Status:** Ready for implementation
> **Build Priority:** #1 — all other modules depend on this
> **Implements:** Phase 1 of the development roadmap

---

## 1. Purpose

`strata-core` is the shared foundation every other Strata module depends on. It provides no gameplay content of its own. Its job is to make the rest of the ecosystem possible: a shared event system for cross-module communication, a configuration framework, a player data persistence layer, registry helpers, and shared utilities.

Nothing in `strata-world`, `strata-structures`, `strata-rpg`, or `strata-creator` should re-implement any of these systems. If a pattern needs to exist in multiple modules, it belongs in `strata-core`.

---

## 2. Responsibilities

- **Module initializer** — Entry point, version logging, wiring up internal systems
- **Strata Event System** — Typed, Fabric-native events for cross-module communication
- **Configuration Framework** — Per-module TOML config using Cloth Config, with `strata-core`'s own config as the reference implementation
- **Player Data Attachments** — A framework for modules to persist custom data on players and other entities using Fabric's Data Attachments API
- **Registry Helpers** — Utility wrappers that simplify block/item/entity registration and enforce Strata naming conventions
- **Shared Utilities** — Math helpers, NBT utilities, logging wrapper

---

## 3. System Designs

### 3.1 Module Initializer

**Class:** `io.strata.core.StrataCore`
**Implements:** `net.fabricmc.api.ModInitializer`
**Registered in:** `fabric.mod.json` as the `main` entrypoint

Responsibilities on `onInitialize()`:
1. Log Strata version and MC version to confirm the mod loaded correctly
2. Initialize the `StrataEvents` registry (pre-registers all core events)
3. Initialize `StrataCoreConfig` via Cloth Config's `AutoConfig`
4. Initialize `StrataAttachments` (register all core data attachment types)
5. Log completion

There should also be a `StrataCoreClient` class implementing `ClientModInitializer` registered under the `client` entrypoint, for any future client-only initialization (currently a no-op placeholder).

**Version constants** should live in a dedicated `StrataVersion` class:

```java
// io.strata.core.StrataVersion
public final class StrataVersion {
    public static final String STRATA_VERSION = "0.1.0";
    public static final String MOD_ID = "strata_core";
    // Populated at runtime from FabricLoader:
    public static String getMinecraftVersion() { ... }
}
```

---

### 3.2 Strata Event System

**Package:** `io.strata.core.event`

Strata does **not** implement a custom event bus. Instead, it defines a centralized collection of typed Fabric `Event<T>` instances. This uses Fabric's battle-tested event system while giving Strata a single place to discover and manage all cross-module events.

#### Design Pattern

Each event is a `public static final Event<T>` field on a class, where `T` is a functional interface. Fabric's `EventFactory.createArrayBacked()` is used to construct events. Other modules listen by calling `StrataEvents.SOME_EVENT.register(listener)`.

#### Core Events to Define (Phase 1)

All events live in `io.strata.core.event.StrataEvents`:

```java
public final class StrataEvents {

    // Fired after all Strata modules have finished initializing.
    // Useful for cross-module setup that requires all mods to be loaded.
    public static final Event<StrataInitialized> STRATA_INITIALIZED = ...;

    // Fired when a player first joins a world (new game, not respawn).
    // Used by strata-rpg to initialize player stats.
    public static final Event<PlayerFirstJoin> PLAYER_FIRST_JOIN = ...;

    // Fired when a player respawns (death or dimension change).
    public static final Event<PlayerRespawn> PLAYER_RESPAWN = ...;

    // Fired when a player's Strata data is loaded from disk.
    // Signals that it is safe to read player attachment data.
    public static final Event<PlayerDataLoaded> PLAYER_DATA_LOADED = ...;

    // Fired when a player's Strata data is about to be saved to disk.
    // Gives modules a chance to flush any pending state.
    public static final Event<PlayerDataSaving> PLAYER_DATA_SAVING = ...;
}
```

#### Functional Interfaces

Each event type gets its own `@FunctionalInterface` in `io.strata.core.event.callback`:

```java
@FunctionalInterface
public interface StrataInitialized {
    void onStrataInitialized();
}

@FunctionalInterface
public interface PlayerFirstJoin {
    void onPlayerFirstJoin(ServerPlayerEntity player);
}

// etc.
```

#### How Other Modules Use This

```java
// In strata-rpg's initializer:
StrataEvents.PLAYER_FIRST_JOIN.register(player -> {
    RpgPlayerData data = StrataAttachments.RPG_DATA.get(player); // see section 3.4
    data.initializeDefaults();
});
```

#### Adding New Events

When a new module needs a cross-module event, it adds the event definition to `StrataEvents` in `strata-core` and the corresponding callback interface to the `callback` package. Never define cross-module events in a feature module.

Module-internal events (listened to only within one module) can be defined locally within that module.

---

### 3.3 Configuration Framework

**Package:** `io.strata.core.config`
**Library:** Cloth Config (`me.shedaniel.autoconfig`)

#### Pattern

Each Strata module has exactly one config class annotated with `@Config`. `strata-core` provides:
1. A reference implementation (`StrataCoreConfig`) that other modules mirror
2. A helper class `StrataConfigHelper` that standardizes config registration and access

#### `StrataCoreConfig`

```java
// io.strata.core.config.StrataCoreConfig
@Config(name = "strata_core")
public class StrataCoreConfig implements ConfigData {

    @Comment("Set to true to enable verbose Strata logging. Useful for debugging.")
    public boolean verboseLogging = false;

    @Comment("Set to false to disable Strata's startup banner in the log.")
    public boolean showStartupBanner = true;
}
```

Config files are saved by Cloth Config to `.minecraft/config/strata_core.json` (or `.toml` depending on serializer — use JSON for simplicity in Phase 1; TOML can be added later).

#### Registration

In `StrataCore.onInitialize()`:
```java
AutoConfig.register(StrataCoreConfig.class, GsonConfigSerializer::new);
```

#### `StrataConfigHelper`

```java
// io.strata.core.config.StrataConfigHelper
public final class StrataConfigHelper {

    // Registers a config class. Call this from each module's initializer.
    public static <T extends ConfigData> void register(Class<T> configClass) {
        AutoConfig.register(configClass, GsonConfigSerializer::new);
    }

    // Retrieves the current config instance. Call this anywhere to read config values.
    public static <T extends ConfigData> T get(Class<T> configClass) {
        return AutoConfig.getConfigHolder(configClass).getConfig();
    }
}
```

Usage in other modules:
```java
// In strata-rpg's initializer:
StrataConfigHelper.register(RpgConfig.class);

// Anywhere in strata-rpg:
RpgConfig config = StrataConfigHelper.get(RpgConfig.class);
int maxLevel = config.maxPlayerLevel;
```

---

### 3.4 Player Data Attachments

**Package:** `io.strata.core.attachment`
**Library:** Fabric Data Attachments API (`net.fabricmc.fabric.api.attachment.v1`)

The Data Attachments API is the modern Fabric mechanism for storing custom data on game objects (players, worlds, chunks, etc.) without modifying vanilla classes. It replaces older capability-style patterns.

#### Design

`strata-core` defines a centralized class `StrataAttachments` that holds all attachment type definitions used across the ecosystem. Each module registers its attachment type here (or in a module-local attachments class — TBD based on size; start centralized).

#### Phase 1 Attachment Types

```java
// io.strata.core.attachment.StrataAttachments
public final class StrataAttachments {

    // Tracks whether a player has received first-join initialization.
    // Stored on ServerPlayerEntity.
    public static final AttachmentType<Boolean> PLAYER_INITIALIZED =
        AttachmentRegistry.createPersistent(
            new Identifier("strata_core", "player_initialized"),
            builder -> builder.initializer(() -> false)
        );

    // Generic string→string tag map for lightweight cross-module flags.
    // Stored on ServerPlayerEntity.
    public static final AttachmentType<Map<String, String>> PLAYER_TAGS =
        AttachmentRegistry.createPersistent(
            new Identifier("strata_core", "player_tags"),
            builder -> builder
                .initializer(HashMap::new)
                .copyOnDeath(false)
        );
}
```

Phase 1 keeps attachments minimal — just enough to support the event system and player initialization detection. `strata-rpg` will add RPG-specific attachment types when it is built, following this same pattern.

#### Usage Pattern

```java
// Writing
player.setAttached(StrataAttachments.PLAYER_INITIALIZED, true);

// Reading
boolean initialized = player.getAttachedOrElse(StrataAttachments.PLAYER_INITIALIZED, false);
```

#### First-Join Detection

`StrataCore` registers a listener on Fabric's `ServerPlayConnectionEvents.JOIN` to detect first joins using the `PLAYER_INITIALIZED` attachment, then fires `StrataEvents.PLAYER_FIRST_JOIN`:

```java
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
    ServerPlayerEntity player = handler.getPlayer();
    boolean initialized = player.getAttachedOrElse(StrataAttachments.PLAYER_INITIALIZED, false);
    if (!initialized) {
        player.setAttached(StrataAttachments.PLAYER_INITIALIZED, true);
        StrataEvents.PLAYER_FIRST_JOIN.invoker().onPlayerFirstJoin(player);
    }
});
```

---

### 3.5 Registry Helpers

**Package:** `io.strata.core.registry`

Fabric's registry system is straightforward, but Strata wants a consistent pattern for registration across all modules. `StrataRegistry` provides thin wrappers that enforce naming conventions and reduce boilerplate.

#### `StrataRegistry`

```java
// io.strata.core.registry.StrataRegistry
public final class StrataRegistry {

    // Register a block. Automatically uses the providing mod's namespace.
    public static <T extends Block> T registerBlock(String modId, String name, T block) {
        return Registry.register(Registries.BLOCK, new Identifier(modId, name), block);
    }

    // Register an item.
    public static <T extends Item> T registerItem(String modId, String name, T item) {
        return Registry.register(Registries.ITEM, new Identifier(modId, name), item);
    }

    // Register a block with an automatically created BlockItem.
    public static <T extends Block> T registerBlockWithItem(
            String modId, String name, T block, Item.Settings itemSettings) {
        registerBlock(modId, name, block);
        registerItem(modId, name, new BlockItem(block, itemSettings));
        return block;
    }

    // Register an entity type.
    public static <T extends Entity> EntityType<T> registerEntityType(
            String modId, String name, EntityType<T> type) {
        return Registry.register(Registries.ENTITY_TYPE, new Identifier(modId, name), type);
    }
}
```

Usage in other modules:
```java
// In strata-world:
public static final Block VERDANT_SOIL = StrataRegistry.registerBlockWithItem(
    "strata_world", "verdant_soil",
    new Block(AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK)),
    new Item.Settings()
);
```

---

### 3.6 Shared Utilities

**Package:** `io.strata.core.util`

#### `StrataLogger`

A thin wrapper around SLF4J that prefixes all Strata log messages with `[Strata]` and respects the `verboseLogging` config flag.

```java
public final class StrataLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("Strata");

    public static void info(String message, Object... args) { LOGGER.info(message, args); }
    public static void warn(String message, Object... args) { LOGGER.warn(message, args); }
    public static void error(String message, Object... args) { LOGGER.error(message, args); }

    public static void debug(String message, Object... args) {
        if (StrataConfigHelper.get(StrataCoreConfig.class).verboseLogging) {
            LOGGER.info("[DEBUG] " + message, args);
        }
    }
}
```

#### `NbtHelper` (Phase 1 — minimal)

Convenience methods for common NBT read/write patterns. Start with just what's needed; expand as modules request it.

```java
public final class StrataaNbtHelper {
    public static void putIdentifier(NbtCompound nbt, String key, Identifier id) { ... }
    public static Identifier getIdentifier(NbtCompound nbt, String key) { ... }
    public static void putStringList(NbtCompound nbt, String key, List<String> list) { ... }
    public static List<String> getStringList(NbtCompound nbt, String key) { ... }
}
```

---

## 4. Public API Summary

What other Strata modules should import and use from `strata-core`:

| Class | Used for |
|---|---|
| `StrataEvents` | Registering listeners for cross-module events |
| `StrataAttachments` | Reading/writing shared persistent player data |
| `StrataConfigHelper` | Registering and reading module config classes |
| `StrataRegistry` | Registering blocks, items, entities |
| `StrataLogger` | Logging with consistent Strata prefix |
| `StrataVersion` | Reading Strata version string |

Other modules **must not** directly access `StrataCore`, `StrataCoreConfig`, or any `io.strata.core.internal.*` classes (mark these package-private where possible).

---

## 5. File Structure

```
strata-core/
├── build.gradle                        ← No inter-module deps (this IS the base)
├── src/main/
│   ├── java/io/strata/core/
│   │   ├── StrataCore.java             ← ModInitializer (main entrypoint)
│   │   ├── StrataCoreClient.java       ← ClientModInitializer (placeholder)
│   │   ├── StrataVersion.java          ← Version constants
│   │   ├── attachment/
│   │   │   └── StrataAttachments.java  ← All attachment type definitions
│   │   ├── config/
│   │   │   ├── StrataCoreConfig.java   ← Core config class
│   │   │   └── StrataConfigHelper.java ← Config registration/access helper
│   │   ├── event/
│   │   │   ├── StrataEvents.java       ← All cross-module event definitions
│   │   │   └── callback/              ← Functional interfaces for each event
│   │   │       ├── StrataInitialized.java
│   │   │       ├── PlayerFirstJoin.java
│   │   │       ├── PlayerRespawn.java
│   │   │       ├── PlayerDataLoaded.java
│   │   │       └── PlayerDataSaving.java
│   │   ├── registry/
│   │   │   └── StrataRegistry.java    ← Registration helper methods
│   │   └── util/
│   │       ├── StrataLogger.java      ← Logging wrapper
│   │       └── StrataaNbtHelper.java  ← NBT convenience methods
│   └── resources/
│       ├── fabric.mod.json
│       └── assets/strata_core/        ← (empty for now — no visual assets)
```

---

## 6. Gradle Configuration

### `build.gradle` (strata-core)

`strata-core` has no inter-module dependencies. Its `build.gradle` only needs module-specific library deps:

```groovy
dependencies {
    // Cloth Config for configuration UI and serialization
    modImplementation "me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}"
    include "me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}"
}
```

Add `cloth_config_version` to the root `gradle.properties`.

### `fabric.mod.json`

Key fields:
```json
{
  "schemaVersion": 1,
  "id": "strata_core",
  "version": "${version}",
  "name": "Strata Core",
  "description": "Shared foundation for the Strata mod ecosystem.",
  "entrypoints": {
    "main": ["io.strata.core.StrataCore"],
    "client": ["io.strata.core.StrataCoreClient"]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-api": "*",
    "minecraft": "~1.21"
  }
}
```

---

## 7. Implementation Notes for Claude Code

- Use `net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry` and `AttachmentType` — these are the current Fabric APIs. Do not use any older capability or `PersistentState` patterns for player data.
- Use `net.fabricmc.fabric.api.event.EventFactory.createArrayBacked()` for all Strata events.
- Cloth Config: use `AutoConfig` with `GsonConfigSerializer` in Phase 1. Do not use `@ConfigEntry.Gui` annotations yet — GUI polish comes later.
- All `Identifier` construction should use `new Identifier(modId, path)` — check the current Fabric API version for whether this constructor is still preferred or if `Identifier.of()` is the newer form.
- Mark all utility class constructors `private` and the classes `final`. These are static-method-only classes.
- Do not add any gameplay content (blocks, items, biomes) to `strata-core`. If the temptation arises, it belongs in a feature module.

---

## 8. Out of Scope for Phase 1

The following are explicitly deferred to later phases or to feature modules:

- GUI screens of any kind
- Any gameplay content (items, blocks, entities)
- Network packets (add when first needed by a feature module)
- Client-side rendering hooks
- Any RPG, worldgen, or structure logic
- Keybinding registration
- Command registration (add when needed)

---

## 9. Acceptance Criteria

`strata-core` Phase 1 is complete when:

- [ ] `./gradlew :strata-core:build` compiles with zero errors and zero warnings
- [ ] `./gradlew :strata-core:runClient` launches Minecraft and the log shows the Strata startup banner
- [ ] A test player joining a world triggers `StrataEvents.PLAYER_FIRST_JOIN` (verified via a debug log message in `StrataCore`)
- [ ] `StrataCoreConfig` loads correctly and `verboseLogging = true` produces debug log output
- [ ] All public API classes are present and importable by a stub `strata-world` module
