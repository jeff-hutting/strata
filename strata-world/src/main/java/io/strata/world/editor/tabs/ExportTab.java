package io.strata.world.editor.tabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.strata.core.util.StrataLogger;
import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /** Two-click overwrite confirmation flag. */
    private boolean confirmOverwrite = false;

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

        // Apply name button — also syncs to the header display name field
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Set Name"), b -> {
            String name = nameField.getText().strip();
            if (!name.isEmpty()) {
                state.setDisplayName(name);
                setStatus("Name set: " + name + " -> " + state.getBiomeId());
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

        // Export Strata-Pack
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Export Strata-Pack"), b -> {
            exportStrataPack();
        }).dimensions(x + 10, btnY + 48, btnW, 18).build());

        // Save draft
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Save Draft"), b -> {
            saveDraft();
        }).dimensions(x + 10, btnY + 72, btnW, 18).build());
    }

    // ── Export logic ─────────────────────────────────────────────────────────

    /**
     * Builds the biome JSON string in vanilla datapack format.
     * Delegates to {@link BiomeEditorState#buildBiomeJson()}.
     */
    private String buildBiomeJson() {
        return state.buildBiomeJson();
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

        // Auto-derive biome ID from display name if not yet set
        String biomeId = state.getBiomeId();
        if ((biomeId.isEmpty() || !biomeId.contains(":")) && !state.getDisplayName().isEmpty()) {
            state.setDisplayName(state.getDisplayName()); // triggers auto-derive
            biomeId = state.getBiomeId();
        }
        if (biomeId.isEmpty() || !biomeId.contains(":")) {
            setStatus("Set a biome name first (in the header Name field).");
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

        // Warn on overwrite — first click shows warning, second click confirms
        if (Files.exists(biomeFile) && !confirmOverwrite) {
            confirmOverwrite = true;
            setStatus("File exists! Click again to overwrite: " + biomeFile.getFileName());
            return;
        }
        confirmOverwrite = false;

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

    /**
     * Exports the biome as a Strata-Pack (.stratapack) ZIP archive containing
     * the biome JSON, en_us.json lang entry, and strata-pack.json manifest.
     */
    private void exportStrataPack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() == null) {
            setStatus("Export only works in singleplayer worlds.");
            return;
        }

        String biomeId = state.getBiomeId();
        if ((biomeId.isEmpty() || !biomeId.contains(":")) && !state.getDisplayName().isEmpty()) {
            state.setDisplayName(state.getDisplayName());
            biomeId = state.getBiomeId();
        }
        if (biomeId.isEmpty() || !biomeId.contains(":")) {
            setStatus("Set a biome name first (in the header Name field).");
            return;
        }

        String idPath = biomeId.split(":", 2)[1];
        String author = authorField != null ? authorField.getText().strip() : "";
        if (author.isEmpty()) author = "Unknown";

        Path worldRoot = mc.getServer().getSavePath(WorldSavePath.ROOT);
        Path packDir = worldRoot.resolve("strata_biomes");
        Path packFile = packDir.resolve(idPath + ".stratapack");

        // Warn on overwrite — first click shows warning, second click confirms
        if (Files.exists(packFile) && !confirmOverwrite) {
            confirmOverwrite = true;
            setStatus("File exists! Click again to overwrite: " + packFile.getFileName());
            return;
        }
        confirmOverwrite = false;

        try {
            Files.createDirectories(packDir);

            String biomeJson = buildBiomeJson();

            // Manifest
            JsonObject manifest = new JsonObject();
            manifest.addProperty("name", state.getDisplayName());
            manifest.addProperty("author", author);
            manifest.addProperty("description", "Created with Strata Biome Editor");
            manifest.addProperty("version", "1.0.0");
            manifest.addProperty("strata_version", "0.1.0");
            JsonObject contents = new JsonObject();
            com.google.gson.JsonArray biomes = new com.google.gson.JsonArray();
            biomes.add(idPath);
            contents.add("biomes", biomes);
            manifest.add("contents", contents);

            // Lang entry
            JsonObject lang = new JsonObject();
            lang.addProperty("biome.strata_world." + idPath, state.getDisplayName());

            // Write ZIP
            try (OutputStream fos = Files.newOutputStream(packFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                zos.putNextEntry(new ZipEntry("strata-pack.json"));
                zos.write(GSON.toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("content/biomes/" + idPath + ".json"));
                zos.write(biomeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("content/lang/en_us.json"));
                zos.write(GSON.toJson(lang).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            state.markExported();
            setStatus("Exported: " + packFile.getFileName());
            StrataLogger.info("Exported Strata-Pack to {}", packFile);
        } catch (IOException e) {
            setStatus("Export failed: " + e.getMessage());
            StrataLogger.error("Failed to export Strata-Pack: {}", e.getMessage());
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

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;

        // Sync name field with current state (may have been changed in the header)
        if (nameField != null && !nameField.isFocused()
                && !nameField.getText().equals(state.getDisplayName())) {
            nameField.setText(state.getDisplayName());
        }

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
        int previewY = y + 184;
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
