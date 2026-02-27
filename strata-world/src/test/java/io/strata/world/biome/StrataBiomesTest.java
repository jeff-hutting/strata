package io.strata.world.biome;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that StrataBiomes registers the correct registry keys and exposes the right count.
 *
 * Covers spec acceptance criteria:
 *  - Log shows "strata-world initialized. 1 biomes registered." (count() == 1)
 *  - VERDANT_HIGHLANDS key is correctly namespaced under strata_world
 */
class StrataBiomesTest {

    @Test
    void verdantHighlandsKeyIsNotNull() {
        assertNotNull(StrataBiomes.VERDANT_HIGHLANDS,
                "VERDANT_HIGHLANDS registry key must not be null");
    }

    @Test
    void verdantHighlandsNamespace() {
        assertEquals("strata_world",
                StrataBiomes.VERDANT_HIGHLANDS.getValue().getNamespace(),
                "VERDANT_HIGHLANDS must use the strata_world namespace");
    }

    @Test
    void verdantHighlandsPath() {
        assertEquals("verdant_highlands",
                StrataBiomes.VERDANT_HIGHLANDS.getValue().getPath(),
                "VERDANT_HIGHLANDS path must be verdant_highlands");
    }

    @Test
    void countReturnsOne() {
        assertEquals(1, StrataBiomes.count(),
                "Phase 1 must register exactly 1 biome");
    }

    @Test
    void initializeDoesNotThrow() {
        assertDoesNotThrow(StrataBiomes::initialize,
                "initialize() must complete without throwing");
    }
}
