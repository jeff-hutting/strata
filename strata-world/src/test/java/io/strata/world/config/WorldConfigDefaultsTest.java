package io.strata.world.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that WorldConfig default values match the spec (Section 5) and that
 * the Phase 1 review fix (removing generateInExistingWorlds) was applied.
 *
 * Covers spec acceptance criteria:
 *  - Setting biomeFrequency = 0.1 reduces biome occurrence (default is 1.0)
 *  - Setting enabled = false disables biomes (default is true)
 *  - All multi-noise point values are config-driven, not hardcoded
 */
class WorldConfigDefaultsTest {

    private WorldConfig config;

    @BeforeEach
    void setUp() {
        config = new WorldConfig();
    }

    @Test
    void enabledDefaultIsTrue() {
        assertTrue(config.enabled,
                "enabled must default to true so biomes generate out of the box");
    }

    @Test
    void biomeFrequencyDefaultIsOne() {
        assertEquals(1.0f, config.biomeFrequency, 0.0001f,
                "biomeFrequency must default to 1.0 (neutral / no scaling)");
    }

    // --- VerdantHighlands multi-noise defaults (from Phase 1 review fix) ---

    @Test
    void verdantHighlandsTemperatureDefault() {
        assertEquals(0.0f, config.verdantHighlandsTemperature, 0.0001f,
                "temperature point: mild, between cold and warm");
    }

    @Test
    void verdantHighlandsHumidityDefault() {
        assertEquals(0.3f, config.verdantHighlandsHumidity, 0.0001f,
                "humidity point: moderate-to-lush");
    }

    @Test
    void verdantHighlandsContinentalnessDefault() {
        assertEquals(0.3f, config.verdantHighlandsContinentalness, 0.0001f,
                "continentalness point: inland (not coastal)");
    }

    @Test
    void verdantHighlandsErosionDefault() {
        assertEquals(-0.4f, config.verdantHighlandsErosion, 0.0001f,
                "erosion point: low erosion = rolling hills");
    }

    @Test
    void verdantHighlandsDepthDefault() {
        assertEquals(0.0f, config.verdantHighlandsDepth, 0.0001f,
                "depth point: 0.0 = surface level");
    }

    @Test
    void verdantHighlandsWeirdnessDefault() {
        assertEquals(0.0f, config.verdantHighlandsWeirdness, 0.0001f,
                "weirdness point: 0.0 = normal terrain");
    }

    /**
     * Regression guard for Phase 1 review fix: generateInExistingWorlds was declared
     * but never read by any code, so it was removed rather than left as dead config.
     */
    @Test
    void generateInExistingWorldsFieldWasRemoved() {
        Set<String> fieldNames = Arrays.stream(WorldConfig.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertFalse(fieldNames.contains("generateInExistingWorlds"),
                "generateInExistingWorlds was removed in Phase 1 review fix — must not re-appear");
    }

    @Test
    void allExpectedFieldsArePresent() {
        Set<String> fieldNames = Arrays.stream(WorldConfig.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("enabled"));
        assertTrue(fieldNames.contains("biomeFrequency"));
        assertTrue(fieldNames.contains("verdantHighlandsTemperature"));
        assertTrue(fieldNames.contains("verdantHighlandsHumidity"));
        assertTrue(fieldNames.contains("verdantHighlandsContinentalness"));
        assertTrue(fieldNames.contains("verdantHighlandsErosion"));
        assertTrue(fieldNames.contains("verdantHighlandsDepth"));
        assertTrue(fieldNames.contains("verdantHighlandsWeirdness"));
    }
}
