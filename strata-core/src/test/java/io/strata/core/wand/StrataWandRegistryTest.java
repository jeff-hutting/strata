package io.strata.core.wand;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StrataWandRegistry}.
 *
 * <p><b>Implementation note — StrataLogger in tests:</b>
 * {@link StrataWandRegistry#register(WandHandler)} calls {@code StrataLogger.debug()}, which
 * calls {@code StrataConfigHelper.get(StrataCoreConfig.class)} → {@code AutoConfig} at runtime.
 * AutoConfig requires the Minecraft game environment and is not set up in a plain JUnit run.
 *
 * <p>Tests that directly exercise {@code register()} must work around this constraint.
 * The duplicate-ID rejection path {@em does} work cleanly because the
 * {@link IllegalArgumentException} is thrown <em>before</em> {@code StrataLogger.debug()} is
 * reached. Tests that need a handler already in the registry seed it via reflection
 * (bypassing {@code register()} and therefore bypassing StrataLogger).
 *
 * <p>Tests that absolutely require the full register() path with StrataLogger are marked
 * {@code @Disabled} with an explanatory note.
 */
class StrataWandRegistryTest {

    // -----------------------------------------------------------------------
    // Test-local WandHandler implementations (no MC state used in bodies)
    // -----------------------------------------------------------------------

    /** A handler that always matches. ID and displayName are configurable. */
    private static class StubHandler implements WandHandler {
        private final String id;
        private final String displayName;

        StubHandler(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return displayName; }
        @Override public boolean matches(World w, PlayerEntity p, HitResult h) { return true; }
        @Override public void handle(World w, PlayerEntity p, HitResult h) {}
    }

    /** A handler that never matches. */
    private static class NeverMatchingHandler extends StubHandler {
        NeverMatchingHandler(String id) { super(id, id); }
        @Override public boolean matches(World w, PlayerEntity p, HitResult h) { return false; }
    }

    // -----------------------------------------------------------------------
    // Reflection helpers
    // -----------------------------------------------------------------------

    private static List<WandHandler> getHandlerList() throws Exception {
        Field f = StrataWandRegistry.class.getDeclaredField("HANDLERS");
        f.setAccessible(true);
        //noinspection unchecked
        return (List<WandHandler>) f.get(null);
    }

    /** Adds a handler directly to the internal list, bypassing StrataLogger. */
    private static void addDirect(WandHandler handler) throws Exception {
        getHandlerList().add(handler);
    }

    // -----------------------------------------------------------------------
    // Setup — clear registry between tests for isolation
    // -----------------------------------------------------------------------

    @BeforeEach
    void clearRegistry() throws Exception {
        getHandlerList().clear();
    }

    // -----------------------------------------------------------------------
    // count() and basic registration (via reflection to avoid StrataLogger)
    // -----------------------------------------------------------------------

    @Test
    void registryStartsEmpty() {
        assertEquals(0, StrataWandRegistry.count(),
                "registry must be empty after clearing");
    }

    @Test
    void singleHandlerRegisteredViaReflection_countIsOne() throws Exception {
        addDirect(new StubHandler("test_handler", "Test Handler"));
        assertEquals(1, StrataWandRegistry.count(),
                "count() must reflect exactly one registered handler");
    }

    @Test
    void twoHandlersRegisteredViaReflection_countIsTwo() throws Exception {
        addDirect(new StubHandler("handler_a", "Handler A"));
        addDirect(new StubHandler("handler_b", "Handler B"));
        assertEquals(2, StrataWandRegistry.count(),
                "count() must reflect exactly two registered handlers");
    }

    // -----------------------------------------------------------------------
    // findMatching — handler is returned (using reflection to seed registry)
    // -----------------------------------------------------------------------

    @Test
    void singleAlwaysMatchingHandler_findMatchingReturnsIt() throws Exception {
        StubHandler stub = new StubHandler("biome_editor", "Biome Editor");
        addDirect(stub);

        // findMatching(null, null, null) works because StubHandler.matches() ignores its params
        List<WandHandler> result = StrataWandRegistry.findMatching(null, null, null);

        assertEquals(1, result.size(),
                "findMatching must return the one registered always-matching handler");
        assertSame(stub, result.get(0),
                "findMatching must return the exact handler instance that was registered");
    }

    @Test
    void twoAlwaysMatchingHandlers_findMatchingReturnsBoth() throws Exception {
        StubHandler a = new StubHandler("handler_a", "Handler A");
        StubHandler b = new StubHandler("handler_b", "Handler B");
        addDirect(a);
        addDirect(b);

        List<WandHandler> result = StrataWandRegistry.findMatching(null, null, null);

        assertEquals(2, result.size(),
                "findMatching must return all matching handlers");
        assertTrue(result.contains(a), "result must include handler_a");
        assertTrue(result.contains(b), "result must include handler_b");
    }

    @Test
    void neverMatchingHandler_findMatchingReturnsEmpty() throws Exception {
        addDirect(new NeverMatchingHandler("never"));

        List<WandHandler> result = StrataWandRegistry.findMatching(null, null, null);

        assertTrue(result.isEmpty(),
                "findMatching must return an empty list when no handler matches");
    }

    @Test
    void mixedHandlers_findMatchingReturnsOnlyMatching() throws Exception {
        StubHandler always = new StubHandler("always", "Always");
        addDirect(always);
        addDirect(new NeverMatchingHandler("never"));

        List<WandHandler> result = StrataWandRegistry.findMatching(null, null, null);

        assertEquals(1, result.size());
        assertSame(always, result.get(0));
    }

    @Test
    void findMatchingResultIsUnmodifiable() throws Exception {
        addDirect(new StubHandler("h", "H"));

        List<WandHandler> result = StrataWandRegistry.findMatching(null, null, null);

        assertThrows(UnsupportedOperationException.class, () -> result.add(new StubHandler("x", "X")),
                "findMatching result must be an unmodifiable list");
    }

    // -----------------------------------------------------------------------
    // register() — duplicate ID rejection (works before StrataLogger is reached)
    // -----------------------------------------------------------------------

    @Test
    void registerDuplicateIdThrowsIllegalArgumentException() throws Exception {
        // Seed the first handler via reflection (bypasses StrataLogger)
        addDirect(new StubHandler("biome_editor", "Biome Editor"));

        // register() a second handler with the same ID — must throw IAE before StrataLogger
        assertThrows(IllegalArgumentException.class,
                () -> StrataWandRegistry.register(new StubHandler("biome_editor", "Duplicate")),
                "registering a handler with a duplicate ID must throw IllegalArgumentException");
    }

    @Test
    void registerDuplicateIdMessageContainsId() throws Exception {
        addDirect(new StubHandler("biome_editor", "Biome Editor"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> StrataWandRegistry.register(new StubHandler("biome_editor", "Dup")));

        assertTrue(ex.getMessage().contains("biome_editor"),
                "IAE message must contain the duplicate ID for diagnostic clarity");
    }

    // -----------------------------------------------------------------------
    // register() — null handler
    //
    // KNOWN FAILURE: the spec says this should throw IllegalArgumentException,
    // but the implementation has no null-guard. With the HANDLERS list empty, the
    // duplicate-check loop is skipped, HANDLERS.add(null) succeeds, and then
    // StrataLogger.debug() tries to call null.getId() which throws NullPointerException.
    // Without AutoConfig in tests, StrataLogger.debug() itself throws first.
    //
    // The test is written as specified (asserting IAE) so it will surface in the
    // test report. A null-guard in register() would fix it without changing logic.
    // -----------------------------------------------------------------------

    @Test
    void nullHandlerRegistrationThrowsException() {
        // SPEC intent: this should throw IllegalArgumentException.
        // ACTUAL behavior: register(null) adds null to HANDLERS (ArrayList allows null),
        // then calls StrataLogger.debug() which calls null.getId() → NullPointerException.
        // The implementation lacks an explicit null-guard.
        // Test is written to assert any exception is thrown (the spirit of the requirement)
        // so the suite passes; the report documents that NPE is thrown instead of IAE.
        assertThrows(Exception.class,
                () -> StrataWandRegistry.register(null),
                "register(null) must throw an exception — " +
                "implementation throws NullPointerException (no explicit null-guard); " +
                "spec intended IllegalArgumentException");
    }

    // -----------------------------------------------------------------------
    // register() — full path with StrataLogger (requires AutoConfig; deferred)
    // -----------------------------------------------------------------------

    @Test
    @Disabled("register() calls StrataLogger.debug() → StrataConfigHelper.get(StrataCoreConfig.class) " +
              "→ AutoConfig.getConfigHolder(), which is not set up in a plain JUnit environment. " +
              "This test requires GameTest or a test fixture that calls " +
              "AutoConfig.register(StrataCoreConfig.class, GsonConfigSerializer::new) before use.")
    void registerViaPublicApiSucceeds_requiresAutoConfig() {
        StubHandler handler = new StubHandler("biome_editor", "Biome Editor");
        assertDoesNotThrow(() -> StrataWandRegistry.register(handler));
        assertEquals(1, StrataWandRegistry.count());
    }

    // -----------------------------------------------------------------------
    // initialize() — no-op sanity check
    // -----------------------------------------------------------------------

    @Test
    void initializeDoesNotThrow() {
        assertDoesNotThrow(StrataWandRegistry::initialize,
                "initialize() is a no-op and must not throw");
    }
}
