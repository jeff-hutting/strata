package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Export tab — biome export and Strata-Pack creation controls.
 *
 * <p>Buttons: "Copy JSON to Clipboard", "Save Biome JSON" (writes to
 * {@code saves/<world>/strata_biomes/<name>.json}), "Export Strata-Pack"
 * (creates {@code .stratapack} archive).
 *
 * <p>Also contains Strata-Pack metadata fields (name, author, description,
 * version) and a "Create Thumbnail" button that captures the current view
 * as a 512x512 PNG.
 */
public class ExportTab extends EditorTab {

    public ExportTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Export";
    }

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        // TODO: Add "Copy JSON to Clipboard" button
        // TODO: Add "Save Biome JSON" button
        // TODO: Add "Export Strata-Pack" button
        // TODO: Add metadata fields (name, author, description, version)
        // TODO: Add "Create Thumbnail" button
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer,
                "Export", x + 10, y + 10, 0xFFFFFF, true);
        context.drawText(textRenderer,
                "JSON export, Strata-Pack creation, and metadata fields will appear here.",
                x + 10, y + 26, 0x888888, false);
    }
}
