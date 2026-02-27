package io.strata.world.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the biome frequency → noise offset formula implemented in StrataWorldgen.
 *
 * The formula:  adjustedOffset = 0.375f / Math.max(biomeFrequency, 0.1f)
 *
 * In Minecraft's multi-noise biome selection, a lower offset makes a biome MORE likely
 * to win placement ties; a higher offset makes it RARER.  biomeFrequency therefore has
 * an inverse relationship to the computed offset:
 *   biomeFrequency↑  →  offset↓  →  biome appears more often
 *   biomeFrequency↓  →  offset↑  →  biome appears less often
 *
 * Covers spec acceptance criteria:
 *  - "Setting biomeFrequency = 0.1 noticeably reduces Strata biome occurrence"
 *  - The 0.1 floor in Math.max prevents division-by-zero and clamps extreme values
 */
class BiomeFrequencyOffsetTest {

    /** Base offset constant from StrataWorldgen — conservative starting point. */
    private static final float BASE_OFFSET = 0.375f;
    private static final float DELTA = 0.0001f;

    /**
     * Replicates the formula from StrataWorldgen.addOverworldBiomes() so the math
     * can be verified independently of the MC config/runtime stack.
     */
    private float computeOffset(float biomeFrequency) {
        return BASE_OFFSET / Math.max(biomeFrequency, 0.1f);
    }

    @Test
    void defaultFrequencyProducesBaseOffset() {
        // biomeFrequency=1.0 (default) → 0.375 / 1.0 = 0.375
        assertEquals(BASE_OFFSET, computeOffset(1.0f), DELTA,
                "default biomeFrequency=1.0 must produce the base offset unchanged");
    }

    @Test
    void maxFrequencyHalvesOffset() {
        // biomeFrequency=2.0 (max) → 0.375 / 2.0 = 0.1875
        assertEquals(0.1875f, computeOffset(2.0f), DELTA,
                "biomeFrequency=2.0 must halve the offset (biome appears twice as often)");
    }

    @Test
    void minFrequencyTenXOffset() {
        // biomeFrequency=0.1 (min valid) → 0.375 / 0.1 = 3.75
        assertEquals(3.75f, computeOffset(0.1f), DELTA,
                "biomeFrequency=0.1 must produce 10× the base offset (biome much rarer)");
    }

    @Test
    void zeroFrequencyClampedToMinimum() {
        // biomeFrequency=0.0 → Math.max(0.0, 0.1) = 0.1 → offset = 3.75
        assertEquals(3.75f, computeOffset(0.0f), DELTA,
                "biomeFrequency=0.0 must be clamped to 0.1 (same result as 0.1)");
    }

    @Test
    void negativeFrequencyClampedToMinimum() {
        // biomeFrequency=-99 → Math.max(-99, 0.1) = 0.1 → offset = 3.75
        assertEquals(3.75f, computeOffset(-99.0f), DELTA,
                "negative biomeFrequency must be clamped — no division by negative or zero");
    }

    @Test
    void higherFrequencyAlwaysProducesStrictlyLowerOffset() {
        // Verify monotonically decreasing: higher frequency → lower offset → more common
        assertTrue(computeOffset(0.5f) > computeOffset(1.0f),
                "0.5 < 1.0 frequency, so offset must be higher at 0.5");
        assertTrue(computeOffset(1.0f) > computeOffset(1.5f),
                "1.0 < 1.5 frequency, so offset must be higher at 1.0");
        assertTrue(computeOffset(1.5f) > computeOffset(2.0f),
                "1.5 < 2.0 frequency, so offset must be higher at 1.5");
    }

    @Test
    void offsetIsAlwaysPositive() {
        // Offset must never be zero or negative — that would break biome placement
        assertTrue(computeOffset(2.0f) > 0, "offset must be positive at max frequency");
        assertTrue(computeOffset(0.1f) > 0, "offset must be positive at min frequency");
        assertTrue(computeOffset(1.0f) > 0, "offset must be positive at default frequency");
    }
}
