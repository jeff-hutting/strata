package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Terrain tab — six sliders for multi-noise parameters.
 *
 * <p>Controls temperature, humidity, continentalness, erosion, weirdness,
 * and depth. Each slider shows a range display. Includes a "Refresh Preview"
 * button, auto-refresh toggle, and shows the biome name detected at the
 * player's feet for reference.
 *
 * <p>Changes are Layer 2 — they trigger a 3-second debounced chunk
 * regeneration via {@link io.strata.world.editor.PreviewZoneManager}.
 */
public class TerrainTab extends EditorTab {

    public TerrainTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Terrain";
    }

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        // TODO: Add six noise parameter sliders (temperature, humidity, continentalness, erosion, weirdness, depth)
        // TODO: Add "Refresh Preview" button
        // TODO: Add auto-refresh toggle
        // TODO: Show current biome at player's feet
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer,
                "Terrain Parameters", x + 10, y + 10, 0xFFFFFFFF, true);
        context.drawText(textRenderer,
                "Multi-noise sliders and preview controls will appear here.",
                x + 10, y + 26, 0xFF888888, false);
    }
}
