package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.gui.DrawContext;

/**
 * Base class for Biome Editor tabs.
 *
 * <p>Each tab occupies the content area to the right of the tab sidebar
 * and below the header bar. Tabs are initialized with their available
 * screen region and render their content within those bounds.
 */
public abstract class EditorTab {

    protected final BiomeEditorScreen screen;
    protected final BiomeEditorState state;
    protected int x, y, width, height;

    protected EditorTab(BiomeEditorScreen screen, BiomeEditorState state) {
        this.screen = screen;
        this.state = state;
    }

    /**
     * Returns the display name of this tab, used in the sidebar.
     */
    public abstract String getTabName();

    /**
     * Initializes this tab's widgets within the given bounds.
     *
     * @param x      the left edge of the content area
     * @param y      the top edge of the content area
     * @param width  the width of the content area
     * @param height the height of the content area
     */
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Renders this tab's content.
     */
    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);
}
