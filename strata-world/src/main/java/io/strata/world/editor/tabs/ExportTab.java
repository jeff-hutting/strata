package io.strata.world.editor.tabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.strata.core.util.StrataLogger;
import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import io.strata.world.editor.BiomeEditorState.SpawnEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Export tab — biome JSON export and datapack creation.
 *
 * <p>Provides controls to export the current biome state as a vanilla-compatible
 * datapack JSON file. The output format mirrors {@code verdant_highlands.json}
 * and is written to {@code saves/<world>/datapacks/strata-custom-biomes/data/strata_world/worldgen/biome/<id>.json}.
 *
 * <p>Also provides a "Copy JSON to Clipboard" button for quick sharing.
 */
public class ExportTab extends EditorTab {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Status message shown after export actions. */
    private String statusMessage = "";
    private long statusExpiry = 0L;

    /** Metadata fields. */
    private TextFieldWidget nameField;
    private TextFieldWidget authorField;

    public ExportTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Export";
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);

        var tr = MinecraftClient.getInstance().textRenderer;
        int fieldW = Math.min(width - 80, 200);

        // Biome display name field
        nameField = new TextFieldWidget(tr, x + 80, y + 30, fieldW, 14,
                Text.literal("Biome Name"));
        nameField.setMaxLength(64);
        nameField.setText(state.getDisplayName());
        nameField.setPlaceholder(Text.literal("My Custom Biome"));
        screen.addTabWidget(nameField);

        // Author field
        authorField = new TextFieldWidget(tr, x + 80, y + 50, fieldW, 14,
                Text.literal("Author"));
        authorField.setMaxLength(64);
        authorField.setPlaceholder(Text.literal("Player"));
        screen.addTabWidget(authorField);

        // Apply name button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Set Name"), b -> {
            String name = nameField.getText().strip();
            if (!name.isEmpty()) {
                state.setDisplayName(name);
                setStatus("Name updated to: " + name);
            }
        }).dimensions(x + 84 + fieldW, y + 30, 60, 14).build());

        // ── Action buttons ──────────────────────────────────────────────────
        int btnY = y + 80;
        int btnW = Math.min(width - 20, 180);

        // Copy JSON to Clipboard
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Copy JSON to Clipboard"), b -> {
            String json = buildBiomeJson();
            MinecraftClient.getInstance().keyboard.setClipboard(json);
            setStatus("JSON copied to clipboard!");
        }).dimensions(x + 10, btnY, btnW, 18).build());

        // Save Biome JSON to datapack
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Save as Datapack Biome"), b -> {
            saveBiomeToDatapack();
        }).dimensions(x + 10, btnY + 24, btnW, 18).build());

        // Save draft
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Save Draft"), b -> {
            saveDraft();
        }).dimensions(x + 10, btnY + 48, btnW, 18).build());
    }

    // ── Export logic ─────────────────────────────────────────────────────────

    /**
     * Builds the biome JSON string in vanilla datapack format.
     * Mirrors the structure of {@code verdant_highlands.json}.
     */
    private String buildBiomeJson() {
        JsonObject root = new JsonObject();

        // Precipitation
        root.addProperty("has_precipitation", state.hasRain() || state.hasSnow());

        // Temperature / downfall (humidity)
        root.addProperty("temperature", roundTo3(state.getTemperature()));
        root.addProperty("downfall", roundTo3(state.getHumidity()));

        // Effects block
        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", state.getSkyColor());
        effects.addProperty("fog_color", state.getFogColor());
        effects.addProperty("water_color", state.getWaterColor());
        effects.addProperty("water_fog_color", state.getWaterFogColor());
        if (state.getGrassColor() >= 0) {
            effects.addProperty("grass_color", state.getGrassColor());
        }
        if (state.getFoliageColor() >= 0) {
            effects.addProperty("foliage_color", state.getFoliageColor());
        }

        // Mood sound (standard default)
        JsonObject moodSound = new JsonObject();
        moodSound.addProperty("sound", "minecraft:ambient.cave");
        moodSound.addProperty("tick_delay", 6000);
        moodSound.addProperty("block_search_extent", 8);
        moodSound.addProperty("offset", 2.0);
        effects.add("mood_sound", moodSound);

        root.add("effects", effects);

        // Features — flat list (all features go into generation step 10 = vegetal_decoration)
        // Vanilla uses 12 indexed steps; we put user features in step 10
        JsonArray featureSteps = new JsonArray();
        for (int i = 0; i < 11; i++) {
            featureSteps.add(new JsonArray()); // empty steps 0-9
        }
        JsonArray vegetalStep = new JsonArray();
        for (String feature : state.getFeatures()) {
            vegetalStep.add(feature);
        }
        featureSteps.add(vegetalStep); // step 10 = vegetal_decoration
        featureSteps.add(new JsonArray()); // step 11 = top_layer_modification
        root.add("features", featureSteps);

        // Spawners — all entries go into "creature" category for simplicity
        JsonObject spawners = new JsonObject();
        JsonArray creatureSpawns = new JsonArray();
        for (SpawnEntry entry : state.getSpawnEntries()) {
            JsonObject spawn = new JsonObject();
            spawn.addProperty("type", entry.getEntityId());
            spawn.addProperty("weight", entry.getWeight());
            spawn.addProperty("minCount", entry.getMinGroupSize());
            spawn.addProperty("maxCount", entry.getMaxGroupSize());
            creatureSpawns.add(spawn);
        }
        spawners.add("creature", creatureSpawns);
        spawners.add("monster", new JsonArray());
        spawners.add("ambient", new JsonArray());
        spawners.add("underground_water_creature", new JsonArray());
        spawners.add("water_creature", new JsonArray());
        spawners.add("water_ambient", new JsonArray());
        spawners.add("misc", new JsonArray());
        root.add("spawners", spawners);

        root.add("spawn_costs", new JsonObject());

        return GSON.toJson(root);
    }

    /**
     * Saves the biome JSON to the world's datapack folder.
     * Path: saves/<world>/datapacks/strata-custom-biomes/data/strata_world/worldgen/biome/<id>.json
     */
    private void saveBiomeToDatapack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() == null) {
            setStatus("Export only works in singleplayer worlds.");
            return;
        }

        String biomeId = state.getBiomeId();
        if (biomeId.isEmpty() || !biomeId.contains(":")) {
            setStatus("Set a biome name first!");
            return;
        }

        // Extract the path part after "strata_world:"
        String idPath = biomeId.contains(":") ? biomeId.split(":", 2)[1] : biomeId;
        if (idPath.isEmpty()) {
            setStatus("Invalid biome ID.");
            return;
        }

        Path worldRoot = mc.getServer().getSavePath(WorldSavePath.ROOT);
        Path datapackDir = worldRoot.resolve("datapacks")
                .resolve("strata-custom-biomes")
                .resolve("data")
                .resolve("strata_world")
                .resolve("worldgen")
                .resolve("biome");

        Path biomeFile = datapackDir.resolve(idPath + ".json");

        try {
            Files.createDirectories(datapackDir);

            // Write pack.mcmeta if it doesn't exist
            Path packMcmeta = worldRoot.resolve("datapacks")
                    .resolve("strata-custom-biomes")
                    .resolve("pack.mcmeta");
            if (!Files.exists(packMcmeta)) {
                JsonObject pack = new JsonObject();
                JsonObject packInfo = new JsonObject();
                packInfo.addProperty("pack_format", 48); // MC 1.21.x
                packInfo.addProperty("description", "Custom biomes created with Strata World");
                pack.add("pack", packInfo);
                Files.writeString(packMcmeta, GSON.toJson(pack));
            }

            String json = buildBiomeJson();
            Files.writeString(biomeFile, json);
            state.markExported();

            setStatus("Exported to: " + biomeFile.getFileName());
            StrataLogger.info("Exported biome to {}", biomeFile);
        } catch (IOException e) {
            setStatus("Export failed: " + e.getMessage());
            StrataLogger.error("Failed to export biome: {}", e.getMessage());
        }
    }

    private void saveDraft() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() != null) {
            Path worldRoot = mc.getServer().getSavePath(WorldSavePath.ROOT);
            Path draftPath = worldRoot.resolve("strata_biomes").resolve("_session.draft.json");
            state.saveDraft(draftPath);
            setStatus("Draft saved!");
        } else {
            setStatus("Draft save only works in singleplayer.");
        }
    }

    private void setStatus(String message) {
        this.statusMessage = message;
        this.statusExpiry = System.currentTimeMillis() + 5000L;
    }

    private static float roundTo3(float value) {
        return Math.round(value * 1000f) / 1000f;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;

        // Section header
        context.drawText(tr, "Export & Save", x + 10, y + 10, 0xFFFFFFFF, true);

        // Field labels
        context.drawText(tr, "Name:", x + 10, y + 33, 0xFFAAAAAA, false);
        context.drawText(tr, "Author:", x + 10, y + 53, 0xFFAAAAAA, false);

        // Current biome ID display
        String biomeId = state.getBiomeId().isEmpty() ? "strata_world:untitled" : state.getBiomeId();
        context.drawText(tr, "ID: " + biomeId, x + 10, y + 70, 0xFF888888, false);

        // Export status indicator
        if (state.isExported()) {
            context.drawText(tr, "Exported", x + width - 70, y + 10, 0xFF44FF44, false);
        } else if (state.isDirty()) {
            context.drawText(tr, "Unsaved changes", x + width - 100, y + 10, 0xFFFFAA00, false);
        }

        // Status message
        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusExpiry) {
            int statusY = y + height - 16;
            context.drawText(tr, statusMessage, x + 10, statusY, 0xFF4A90D9, false);
        }

        // JSON preview snippet
        int previewY = y + 160;
        context.drawText(tr, "JSON Preview:", x + 10, previewY, 0xFF888888, false);
        String json = buildBiomeJson();
        String[] lines = json.split("\n");
        int maxPreviewLines = Math.min(lines.length, 8);
        for (int i = 0; i < maxPreviewLines; i++) {
            String line = lines[i];
            if (line.length() > 50) line = line.substring(0, 47) + "...";
            context.drawText(tr, line, x + 10, previewY + 14 + i * 10, 0xFF777777, false);
        }
        if (lines.length > maxPreviewLines) {
            context.drawText(tr, "... (" + lines.length + " lines total)",
                    x + 10, previewY + 14 + maxPreviewLines * 10, 0xFF666666, false);
        }
    }
}
