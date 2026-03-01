package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Visual tab — color pickers and sliders for all Layer 1 properties.
 *
 * <p>Controls sky color, fog color, water color, water fog color,
 * grass tint, foliage tint, ambient sounds, mood sounds, weather type,
 * and particle effects. Changes are reflected instantly on screen
 * with no chunk regeneration.
 */
public class VisualTab extends EditorTab {

    public VisualTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Visual";
    }

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        // TODO: Add color picker widgets for sky, fog, water, grass, foliage
        // TODO: Add weather type toggle (rain/snow/none)
        // TODO: Add ambient sound selector
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer,
                "Visual Properties", x + 10, y + 10, 0xFFFFFF, true);
        context.drawText(textRenderer,
                "Color pickers and atmosphere settings will appear here.",
                x + 10, y + 26, 0x888888, false);
    }
}
