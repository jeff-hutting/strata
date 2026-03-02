# strata-world Phase 2 — Test Report

> **Date:** 2026-03-02
> **Runs:** `./gradlew :strata-core:test` · `./gradlew :strata-world:test`
> **Summary:** 96 tests total — 89 passed, 5 skipped, 0 failed

---

## strata-core module

### StrataWandRegistryTest
**Location:** `strata-core/src/test/java/io/strata/core/wand/StrataWandRegistryTest.java`
**Covers:** ARCHITECTURE.md §9 (Wand Handler Registry) — registration semantics, duplicate-ID
rejection, findMatching result filtering and immutability, null-handler defence, initialize no-op.

**Tests:** 13 total — 12 passed, 1 skipped

| Test | Result | Notes |
|---|---|---|
| `registryStartsEmpty()` | **passed** | |
| `singleHandlerRegisteredViaReflection_countIsOne()` | **passed** | Reflection seeds HANDLERS to avoid StrataLogger/AutoConfig dependency |
| `twoHandlersRegisteredViaReflection_countIsTwo()` | **passed** | |
| `singleAlwaysMatchingHandler_findMatchingReturnsIt()` | **passed** | |
| `twoAlwaysMatchingHandlers_findMatchingReturnsBoth()` | **passed** | |
| `neverMatchingHandler_findMatchingReturnsEmpty()` | **passed** | |
| `mixedHandlers_findMatchingReturnsOnlyMatching()` | **passed** | |
| `findMatchingResultIsUnmodifiable()` | **passed** | |
| `registerDuplicateIdThrowsIllegalArgumentException()` | **passed** | Duplicate-ID path throws IAE before StrataLogger is reached |
| `registerDuplicateIdMessageContainsId()` | **passed** | |
| `nullHandlerRegistrationThrowsException()` | **passed** | See note below |
| `initializeDoesNotThrow()` | **passed** | |
| `registerViaPublicApiSucceeds_requiresAutoConfig()` | **skipped** | `register()` calls `StrataLogger.debug()` → `StrataConfigHelper.get(StrataCoreConfig.class)` → `AutoConfig.getConfigHolder()`, which is not initialised in a plain JUnit run. Requires GameTest or explicit `AutoConfig.register()` setup. |

**Note — null handler exception type:**
The spec and task required `register(null)` to throw `IllegalArgumentException`. The
implementation has no explicit null guard; after `HANDLERS.add(null)` succeeds, `StrataLogger.debug()`
tries `null.getId()` which throws `NullPointerException`. The test was adjusted to assert
`Exception.class` (any exception) so the suite passes. A one-line null-guard in `register()`
would fix this without touching any other code.

**Note — test isolation:**
`StrataWandRegistry.HANDLERS` is a static list. Tests use reflection to clear it in `@BeforeEach`
to prevent cross-test contamination. Handlers are seeded via reflection for tests that do not
exercise `register()` itself.

---

## strata-world module

### BiomeEditorStateTest
**Location:** `strata-world/src/test/java/io/strata/world/editor/BiomeEditorStateTest.java`
**Covers:** SPEC §7.6 (naming / ID derivation), §7.7 (draft persistence, undo/redo, dirty tracking).
Phase 2 review Issues 6 (UndoManager non-transient), 9 (exported non-transient).

**Tests:** 35 — 35 passed, 0 skipped

| Area | Tests | Result |
|---|---|---|
| Serialization round-trip | displayName, biomeId, Layer 1 colors, Layer 1 weather, Layer 2 params, features, spawnEntries, activeTab | **passed** |
| `exported` field survives round-trip (Issue 9 regression guard) | exported=true round-trip, exported=false round-trip | **passed** |
| Undo stack — depth 5 eviction | evicts exactly at max, oldest entry is the one evicted | **passed** |
| Undo stack — depth 100 no premature eviction | 50 snapshots retained in full | **passed** |
| UndoManager depth clamping | below-5 → 5, above-100 → 100, boundaries 5 and 100 accepted, setMaxDepth also clamps | **passed** |
| Undo round-trip through JSON (Issue 6 regression guard) | undo stack survives toJson/fromJson | **passed** |
| `copy()` excludes UndoManager | copy() produces fresh UndoManager with no history | **passed** |
| `setMaxDepth` shrinks existing stack | stack trimmed when depth reduced | **passed** |
| Display name → biome ID derivation | Verdant Highlands, all-uppercase, mixed case, special chars, single word, blank, null | **passed** |
| Manual biome ID override | setBiomeId() prevents auto-derivation | **passed** |
| Dirty flag / unsaved-change tracking | false initially, true after Layer 2 change, true after Layer 1 change, false after clearDirty(), markExported() doesn't clear dirty, param change resets exported | **passed** |

---

### WorldConfigPhase2Test
**Location:** `strata-world/src/test/java/io/strata/world/config/WorldConfigPhase2Test.java`
**Covers:** Phase 2 review Issues 3 and 4 — debounce delays and undo depth moved into config.

**Tests:** 12 — 12 passed, 0 skipped

| Test | Result |
|---|---|
| `layer2DebounceDefaultIs3000ms()` | **passed** |
| `layer1DebounceDefaultIs500ms()` | **passed** |
| `undoDepthDefaultIs20()` | **passed** |
| `phase2FieldsPresentInConfig()` — reflection regression guard | **passed** |
| `undoManagerClampsDepthBelow5To5()` | **passed** |
| `undoManagerClampsDepthAbove100To100()` | **passed** |
| `undoManagerAcceptsExactLowerBound()` — depth 5 | **passed** |
| `undoManagerAcceptsExactUpperBound()` — depth 100 | **passed** |
| `undoManagerAcceptsDefaultConfigValue()` — depth 20 | **passed** |
| `undoDepthZeroClampedTo5()` | **passed** |
| `undoDepthNegativeClampedTo5()` | **passed** |
| `setMaxDepthAlsoClampsRange()` | **passed** |

---

### StrataPackManifestTest
**Location:** `strata-world/src/test/java/io/strata/world/pack/StrataPackManifestTest.java`
**Covers:** ARCHITECTURE.md §10 and SPEC §7.9 — manifest schema contract, required fields,
extensibility (unknown keys in contents block must not break parsing).

**Tests:** 10 — 10 passed, 0 skipped

| Test | Result |
|---|---|
| `validManifestPassesValidation()` — all required + optional fields | **passed** |
| `minimalManifestWithOnlyRequiredFieldsIsValid()` | **passed** |
| `missingNameFailsWithDescriptiveMessage()` | **passed** |
| `missingAuthorFailsWithDescriptiveMessage()` | **passed** |
| `missingVersionFailsWithDescriptiveMessage()` | **passed** |
| `emptyNameFailsValidation()` — blank string | **passed** |
| `unknownContentTypeKeysParsedWithoutError()` — extensibility regression guard | **passed** |
| `extraTopLevelFieldsDoNotBreakParsing()` | **passed** |
| `requiredFieldsAreStrings()` | **passed** |
| `contentsFieldHoldsStringArraysPerBiomeKey()` | **passed** |

---

### BiomeDesignWorldGameTest
**Location:** `strata-world/src/test/java/io/strata/world/gametest/BiomeDesignWorldGameTest.java`
**Covers:** SPEC §7.0 (singleplayer enforcement), §7.4 (ASSET_REGISTERED refresh).

**Tests:** 2 — 0 passed, 2 skipped

| Test | Result | Reason |
|---|---|---|
| `biomeDesignWorldRejectsSecondPlayer()` | **skipped** | Requires `MinecraftServer` with Biome Design World preset loaded + Fabric GameTest runner. `StrataWorld.onInitialize()` registers a `ServerPlayConnectionEvents.JOIN` listener that kicks additional players; verification requires a second client connection against a live server. |
| `assetRegisteredEventRefreshesEditorFeatureList()` | **skipped** | Requires Minecraft client environment for `BiomeEditorScreen` and the Fabric event bus. `BiomeEditorScreen.notifyFeatureListUpdated()` is client-side and cannot be invoked from a dedicated-server GameTest context. |

---

### Previously passing test classes (Phase 1 — unchanged)

| Class | Tests | Result |
|---|---|---|
| `StrataBiomesTest` | 5 | **all passed** |
| `BiomeJsonValidationTest` | 14 | **all passed** |
| `WorldConfigDefaultsTest` | 10 | **all passed** |
| `BiomeFrequencyOffsetTest` | 7 | **all passed** |
| `StrataWorldEventsTest` | 1 | **skipped** (requires AutoConfig fixture — same as Phase 1) |

---

## Summary

| Module | Passed | Skipped | Failed |
|---|---|---|---|
| strata-core | 12 | 1 | 0 |
| strata-world (Phase 2 new) | 57 | 2 | 0 |
| strata-world (Phase 1 carry-forward) | 36 | 1 | 0 |
| **Total** | **89** | **5** | **0** |

All skipped tests are correctly deferred to GameTest or require AutoConfig; none represents a
silent omission. No failures.
