package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

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

    /**
     * Handles a mouse click in the content area.
     *
     * <p>Called by {@link BiomeEditorScreen#mouseClicked} for clicks that fall
     * outside the tab sidebar, before delegating to registered child widgets.
     * Tabs that need custom hit-testing (e.g. color-swatch rows) override this.
     *
     * @param click       carries position ({@code x()}, {@code y()}) and button index
     * @param doubleClick {@code true} if this is a double-click
     * @return {@code true} if the click was consumed; {@code false} to pass it on
     */
    public boolean mouseClicked(Click click, boolean doubleClick) {
        return false;
    }

    /**
     * Handles a key press in the content area.
     * Called before BiomeEditorScreen's tab navigation handling.
     *
     * @param keyInput the key input event
     * @return {@code true} if the key was consumed; {@code false} to pass it on
     */
    public boolean keyPressed(KeyInput keyInput) {
        return false;
    }

    /**
     * Handles a mouse scroll in the content area.
     *
     * @param mouseX           cursor X
     * @param mouseY           cursor Y
     * @param horizontalAmount horizontal scroll delta
     * @param verticalAmount   vertical scroll delta (positive = scroll up)
     * @return {@code true} if the scroll was consumed; {@code false} to pass it on
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }
}
