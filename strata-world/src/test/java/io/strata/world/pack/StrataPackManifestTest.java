package io.strata.world.pack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Strata-Pack manifest format described in ARCHITECTURE.md §10 and SPEC §7.9.
 *
 * <p>The manifest is a JSON file ({@code strata-pack.json}) embedded in a {@code .stratapack}
 * archive. This test class validates the schema contract — required fields, optional fields,
 * and extensibility — without a full implementation class (the export pipeline is
 * scaffolded as TODO in {@code ExportTab}).
 *
 * <p>Tests operate on raw {@link JsonObject} / Gson since no {@code StrataPackManifest} POJO
 * exists yet. The contract is:
 * <ul>
 *   <li>Required: {@code name}, {@code author}, {@code version}</li>
 *   <li>Optional: {@code description}, {@code thumbnail}, {@code strata_version}, {@code contents}</li>
 *   <li>Unknown keys in {@code contents} must not cause parse errors (extensibility)</li>
 * </ul>
 */
class StrataPackManifestTest {

    private static final Gson GSON = new Gson();

    // -----------------------------------------------------------------------
    // Minimal helper — validates required fields
    // -----------------------------------------------------------------------

    /**
     * Simulates the manifest validation that the export pipeline would perform.
     * Returns null if valid, or a descriptive error message if a required field is missing.
     */
    private static String validate(JsonObject manifest) {
        for (String required : new String[]{"name", "author", "version"}) {
            if (!manifest.has(required) || manifest.get(required).isJsonNull()) {
                return "Missing required field: '" + required + "'";
            }
            String value = manifest.get(required).getAsString().trim();
            if (value.isEmpty()) {
                return "Required field '" + required + "' must not be blank";
            }
        }
        return null; // valid
    }

    // -----------------------------------------------------------------------
    // Valid manifest
    // -----------------------------------------------------------------------

    @Test
    void validManifestPassesValidation() {
        String json = """
                {
                  "name": "Highland Collection",
                  "author": "Jeff",
                  "version": "1.0.0",
                  "description": "Rolling green highlands.",
                  "thumbnail": "thumbnail.png",
                  "strata_version": "0.1.0",
                  "contents": {
                    "biomes": ["verdant_highlands"]
                  }
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        assertNull(validate(manifest),
                "a manifest with all required and optional fields must be valid");
    }

    @Test
    void minimalManifestWithOnlyRequiredFieldsIsValid() {
        String json = """
                {
                  "name": "My Pack",
                  "author": "Alice",
                  "version": "0.1.0"
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        assertNull(validate(manifest),
                "a manifest with only the three required fields must be valid");
    }

    // -----------------------------------------------------------------------
    // Missing required fields → descriptive error
    // -----------------------------------------------------------------------

    @Test
    void missingNameFailsWithDescriptiveMessage() {
        String json = """
                {
                  "author": "Bob",
                  "version": "1.0.0"
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        String error = validate(manifest);
        assertNotNull(error, "missing 'name' must fail validation");
        assertTrue(error.contains("name"),
                "error message must identify the missing field: got '" + error + "'");
    }

    @Test
    void missingAuthorFailsWithDescriptiveMessage() {
        String json = """
                {
                  "name": "My Pack",
                  "version": "1.0.0"
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        String error = validate(manifest);
        assertNotNull(error, "missing 'author' must fail validation");
        assertTrue(error.contains("author"),
                "error message must identify the missing field: got '" + error + "'");
    }

    @Test
    void missingVersionFailsWithDescriptiveMessage() {
        String json = """
                {
                  "name": "My Pack",
                  "author": "Carol"
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        String error = validate(manifest);
        assertNotNull(error, "missing 'version' must fail validation");
        assertTrue(error.contains("version"),
                "error message must identify the missing field: got '" + error + "'");
    }

    @Test
    void emptyNameFailsValidation() {
        String json = """
                {
                  "name": "   ",
                  "author": "Dave",
                  "version": "1.0.0"
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        String error = validate(manifest);
        assertNotNull(error, "blank 'name' must fail validation");
    }

    // -----------------------------------------------------------------------
    // Unknown fields in contents → parsed without error (extensibility guard)
    // -----------------------------------------------------------------------

    @Test
    void unknownContentTypeKeysParsedWithoutError() {
        // Future Strata modules add new content type keys; existing readers must not reject them.
        String json = """
                {
                  "name": "Full Ecosystem Pack",
                  "author": "Eve",
                  "version": "2.0.0",
                  "contents": {
                    "biomes": ["verdant_highlands"],
                    "structures": ["highland_tower"],
                    "entities": ["stag"],
                    "items": ["verdant_sword"],
                    "future_content_type": ["unknown_asset_1", "unknown_asset_2"]
                  }
                }
                """;
        // Must not throw — Gson parses all unknown keys without error
        JsonObject manifest = assertDoesNotThrow(
                () -> JsonParser.parseString(json).getAsJsonObject(),
                "unknown keys in 'contents' must not cause a parse error");

        // Validate that the manifest is still valid despite unknown fields
        assertNull(validate(manifest),
                "a manifest with unknown content keys must still pass required-field validation");

        // The known fields must still be accessible
        JsonObject contents = manifest.getAsJsonObject("contents");
        assertTrue(contents.has("biomes"),  "'biomes' key must be readable");
        assertTrue(contents.has("future_content_type"), "unknown content key must survive parsing");
        assertEquals(2, contents.getAsJsonArray("future_content_type").size(),
                "unknown content array must retain its entries");
    }

    @Test
    void extraTopLevelFieldsDoNotBreakParsing() {
        // Newer Strata versions might add top-level fields; older readers must not break.
        String json = """
                {
                  "name": "Future Pack",
                  "author": "Frank",
                  "version": "3.0.0",
                  "new_field_from_future": true,
                  "another_future_field": {"nested": "value"}
                }
                """;
        JsonObject manifest = assertDoesNotThrow(
                () -> JsonParser.parseString(json).getAsJsonObject(),
                "extra top-level fields must parse without error");
        assertNull(validate(manifest),
                "manifest with extra fields must still pass required-field validation");
    }

    // -----------------------------------------------------------------------
    // Field type verification
    // -----------------------------------------------------------------------

    @Test
    void requiredFieldsAreStrings() {
        String json = """
                {
                  "name": "My Pack",
                  "author": "Grace",
                  "version": "1.2.3"
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("My Pack",  manifest.get("name").getAsString());
        assertEquals("Grace",    manifest.get("author").getAsString());
        assertEquals("1.2.3",    manifest.get("version").getAsString());
    }

    @Test
    void contentsFieldHoldsStringArraysPerBiomeKey() {
        String json = """
                {
                  "name": "Pack",
                  "author": "Hank",
                  "version": "1.0.0",
                  "contents": {
                    "biomes": ["verdant_highlands", "crimson_badlands"]
                  }
                }
                """;
        JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
        JsonObject contents = manifest.getAsJsonObject("contents");

        assertEquals(2, contents.getAsJsonArray("biomes").size());
        assertEquals("verdant_highlands", contents.getAsJsonArray("biomes").get(0).getAsString());
        assertEquals("crimson_badlands",  contents.getAsJsonArray("biomes").get(1).getAsString());
    }
}
