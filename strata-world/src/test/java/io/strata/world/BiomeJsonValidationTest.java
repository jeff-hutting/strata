package io.strata.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the VerdantHighlands biome JSON against the values specified in
 * SPEC.md Section 6 (Phase 1 Biome: Verdant Highlands).
 *
 * Tests run against the actual resource file on the classpath — no MC server needed.
 *
 * Covers spec acceptance criteria:
 *  - VerdantHighlands has the correct sky color, fog color, and water color
 *  - VerdantHighlands has trees, grass, and flowers generating correctly
 *  - JSON follows vanilla biome format (has_precipitation, temperature, downfall, etc.)
 */
class BiomeJsonValidationTest {

    private static final String JSON_PATH =
            "/data/strata_world/worldgen/biome/verdant_highlands.json";

    private static JsonObject root;

    @BeforeAll
    static void loadJson() throws Exception {
        InputStream stream = BiomeJsonValidationTest.class.getResourceAsStream(JSON_PATH);
        assertNotNull(stream,
                "verdant_highlands.json not found on classpath at " + JSON_PATH
                + " — check that src/main/resources is included in the test classpath");
        root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        ).getAsJsonObject();
    }

    // ---- Required top-level fields ----

    @Test
    void hasPrecipitation() {
        assertTrue(root.has("has_precipitation"),
                "biome JSON must declare has_precipitation");
        assertTrue(root.get("has_precipitation").getAsBoolean(),
                "VerdantHighlands must have precipitation (rain)");
    }

    @Test
    void temperature() {
        assertEquals(0.65, root.get("temperature").getAsDouble(), 0.001,
                "temperature must be 0.65 per spec");
    }

    @Test
    void downfall() {
        assertEquals(0.75, root.get("downfall").getAsDouble(), 0.001,
                "downfall must be 0.75 per spec");
    }

    // ---- Effects / color values (SPEC Section 6) ----

    @Test
    void skyColor() {
        // spec: #6eb4d4 → R=110 G=180 B=212 → 7255252
        assertEquals(7255252, effects().get("sky_color").getAsInt(),
                "sky_color must be 7255252 (#6eb4d4 — soft blue-green)");
    }

    @Test
    void fogColor() {
        // spec: #c8e8d4 → R=200 G=232 B=212 → 13166804
        assertEquals(13166804, effects().get("fog_color").getAsInt(),
                "fog_color must be 13166804 (#c8e8d4 — pale green mist)");
    }

    @Test
    void waterColor() {
        // spec: #3fa67a → R=63 G=166 B=122 → 4171386
        assertEquals(4171386, effects().get("water_color").getAsInt(),
                "water_color must be 4171386 (#3fa67a — teal-green)");
    }

    @Test
    void grassColor() {
        // spec: #5a9e3a → R=90 G=158 B=58 → 5938746
        assertEquals(5938746, effects().get("grass_color").getAsInt(),
                "grass_color must be 5938746 (#5a9e3a — rich green)");
    }

    @Test
    void foliageColor() {
        // spec: #4a8e2a → R=74 G=142 B=42 → 4886058
        assertEquals(4886058, effects().get("foliage_color").getAsInt(),
                "foliage_color must be 4886058 (#4a8e2a — deep green)");
    }

    @Test
    void moodSoundPresent() {
        assertTrue(effects().has("mood_sound"),
                "effects must contain mood_sound for ambient cave audio");
        assertEquals("minecraft:ambient.cave",
                effects().getAsJsonObject("mood_sound").get("sound").getAsString());
    }

    // ---- Features (Phase 1 minimal set from SPEC Section 6) ----

    @Test
    void hasOakAndBirchTrees() {
        assertTrue(findInFlatFeatures("minecraft:trees_birch_and_oak"),
                "features must include minecraft:trees_birch_and_oak (spec: vanilla oak + birch trees)");
    }

    @Test
    void hasGrassPatch() {
        assertTrue(findInFlatFeatures("minecraft:patch_grass_forest"),
                "features must include minecraft:patch_grass_forest");
    }

    @Test
    void hasFlowers() {
        assertTrue(findInFlatFeatures("minecraft:flower_default"),
                "features must include minecraft:flower_default (spec: dandelion, poppy, azure bluet)");
    }

    @Test
    void hasTallGrass() {
        assertTrue(findInFlatFeatures("minecraft:patch_tall_grass"),
                "features must include minecraft:patch_tall_grass");
    }

    // ---- Structural requirements ----

    @Test
    void hasCarvers() {
        assertTrue(root.has("carvers"), "biome JSON must define carvers section");
        assertTrue(root.getAsJsonObject("carvers").has("air"),
                "carvers must include an 'air' list");
    }

    @Test
    void hasSpawners() {
        assertTrue(root.has("spawners"), "biome JSON must define spawners");
        JsonObject spawners = root.getAsJsonObject("spawners");
        assertTrue(spawners.has("monster"), "spawners must include monster category");
        assertTrue(spawners.has("creature"), "spawners must include creature category");
    }

    @Test
    void hasForestCreatureSpawns() {
        // Spec: inherit vanilla forest spawns (wolves, foxes, rabbits, etc.)
        JsonArray creatures = root.getAsJsonObject("spawners")
                .getAsJsonArray("creature");
        boolean hasWolf = false;
        boolean hasFox = false;
        for (JsonElement e : creatures) {
            String type = e.getAsJsonObject().get("type").getAsString();
            if ("minecraft:wolf".equals(type)) hasWolf = true;
            if ("minecraft:fox".equals(type)) hasFox = true;
        }
        assertTrue(hasWolf, "creature spawners must include wolves (vanilla forest spawns)");
        assertTrue(hasFox, "creature spawners must include foxes (vanilla forest spawns)");
    }

    @Test
    void spawnCostsIsEmpty() {
        JsonObject spawnCosts = root.getAsJsonObject("spawn_costs");
        assertNotNull(spawnCosts, "spawn_costs must be present");
        assertEquals(0, spawnCosts.size(),
                "spawn_costs must be empty in Phase 1 (no custom spawn cost entries)");
    }

    // ---- Helpers ----

    private static JsonObject effects() {
        return root.getAsJsonObject("effects");
    }

    /**
     * Flattens the nested features array-of-arrays and checks for a named feature entry.
     * Vanilla biome JSON represents features as an array of arrays (one per generation step).
     */
    private static boolean findInFlatFeatures(String featureId) {
        JsonArray featureGroups = root.getAsJsonArray("features");
        for (JsonElement group : featureGroups) {
            if (!group.isJsonArray()) continue;
            for (JsonElement entry : group.getAsJsonArray()) {
                if (featureId.equals(entry.getAsString())) return true;
            }
        }
        return false;
    }
}
