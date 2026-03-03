package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Spawns tab — two-column layout for mob spawn entry management.
 *
 * <p>Same layout as Features tab. Right panel pulls from both vanilla
 * entity types and {@code StrataAssetRegistry.getAllSpawns()}. Each entry
 * shows weight, min/max group size (editable inline).
 *
 * <p>Changes are Layer 2 — they trigger a debounced chunk regeneration.
 */
public class SpawnsTab extends EditorTab {

    public SpawnsTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Spawns";
    }

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        // TODO: Add two-column layout (active spawns | available entities)
        // TODO: Add search bar for entities
        // TODO: Add inline weight/min/max editors per spawn entry
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer,
                "Spawns", x + 10, y + 10, 0xFFFFFFFF, true);
        context.drawText(textRenderer,
                "Mob spawn picker with weight and group size controls will appear here.",
                x + 10, y + 26, 0xFF888888, false);
    }
}
