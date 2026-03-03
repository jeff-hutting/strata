package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Features tab — two-column layout for biome feature management.
 *
 * <p>Left panel lists features currently in the biome. Right panel is a
 * searchable picker of all available features, including vanilla placed
 * features and everything registered in {@code StrataAssetRegistry}.
 * Filterable by {@code FeatureCategory}. Each feature shows its display
 * name, category, and source (vanilla / strata-built-in / custom).
 *
 * <p>Changes are Layer 2 — they trigger a debounced chunk regeneration.
 */
public class FeaturesTab extends EditorTab {

    public FeaturesTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Features";
    }

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        // TODO: Add two-column layout (active features | available features)
        // TODO: Add search/filter bar for available features
        // TODO: Add category filter buttons
        // TODO: Add/remove feature buttons
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer,
                "Features", x + 10, y + 10, 0xFFFFFFFF, true);
        context.drawText(textRenderer,
                "Feature picker with search and category filter will appear here.",
                x + 10, y + 26, 0xFF888888, false);
    }
}
