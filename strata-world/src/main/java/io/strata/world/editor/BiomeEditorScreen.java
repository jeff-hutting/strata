package io.strata.world.editor;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.config.WorldConfig;
import io.strata.world.editor.tabs.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/**
 * Full-screen Biome Editor panel using Fabric's Screen API.
 *
 * <p>Layout: a header bar with editable display name, auto-derived biome ID,
 * and action buttons (New, Load, Save, Export, Close). Below the header is
 * a five-tab panel: Visual, Terrain, Features, Spawns, Export.
 *
 * <p>The editor operates on a {@link BiomeEditorState} instance. Layer 1 changes
 * are applied instantly; Layer 2 changes trigger a debounced chunk regen via
 * {@link PreviewZoneManager}.
 *
 * @see BiomeEditorState
 * @see PreviewZoneManager
 */
public class BiomeEditorScreen extends Screen {

    /** Width of the tab sidebar in pixels. */
    private static final int TAB_SIDEBAR_WIDTH = 80;

    /** Height of the header bar in pixels. */
    private static final int HEADER_HEIGHT = 40;

    private final BiomeEditorState state;
    private final List<EditorTab> tabs;
    private int activeTabIndex = 0;

    /**
     * Creates a new BiomeEditorScreen with the given editor state.
     *
     * <p>The {@link BiomeEditorState.UndoManager} is owned by the state object
     * (see {@link BiomeEditorState#getUndoManager()}). The undo depth is
     * synchronized from {@link WorldConfig#editorUndoDepth} on construction.
     *
     * @param state the current editor state (owns the undo/redo manager)
     */
    public BiomeEditorScreen(BiomeEditorState state) {
        super(Text.translatable("screen.strata_world.biome_editor"));
        this.state = state;
        this.tabs = List.of(
                new VisualTab(this, state),
                new TerrainTab(this, state),
                new FeaturesTab(this, state),
                new SpawnsTab(this, state),
                new ExportTab(this, state)
        );
        this.activeTabIndex = state.getActiveTab();
        // Apply the configured undo depth so the manager reflects current preferences
        int configDepth = StrataConfigHelper.get(WorldConfig.class).editorUndoDepth;
        state.getUndoManager().setMaxDepth(configDepth);
        // Open the editor session so Layer 1 mixin overrides take effect immediately
        BiomeEditorSession.open(state);
    }

    @Override
    protected void init() {
        super.init();
        // TODO: Initialize header bar widgets (display name field, biome ID label, buttons)
        // TODO: Initialize tab sidebar buttons
        // TODO: Add Load button to header bar — triggers loadBiome() with a file picker
        // TODO: Initialize active tab content
        initActiveTab();
    }

    private void initActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).init(
                    TAB_SIDEBAR_WIDTH, HEADER_HEIGHT,
                    width - TAB_SIDEBAR_WIDTH, height - HEADER_HEIGHT);
        }
    }

    /**
     * Switches to the tab at the given index.
     *
     * @param tabIndex the tab index (0 = Visual, 1 = Terrain, etc.)
     */
    public void setActiveTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabs.size()) {
            this.activeTabIndex = tabIndex;
            this.state.setActiveTab(tabIndex);
            clearChildren();
            init();
        }
    }

    /**
     * Renders the editor screen: background fills, header bar, tab sidebar, and active tab.
     *
     * <p>In MC 1.21.11 the render pipeline is driven by {@code renderWithTooltip}, which
     * calls in order: {@code createNewRootLayer} → {@code renderBackground} → {@code render}
     * (this method) → {@code drawDeferredElements}.  Because {@code renderBackground} is
     * called by {@code renderWithTooltip} <em>before</em> this method runs, {@code super.render()}
     * here only iterates registered child drawables (none yet).
     *
     * <p>IMPORTANT — color format for {@code drawText()}: in MC 1.21.11's retained-mode GUI
     * system the {@code color} parameter is <strong>ARGB</strong> (32-bit).  A bare RGB literal
     * such as {@code 0xFFFFFF} is silently treated as {@code 0x00FFFFFF} (alpha = 0, fully
     * transparent) and the text will be invisible.  Always supply a full ARGB value, e.g.
     * {@code 0xFFFFFFFF} for opaque white.
     *
     * <p>Do NOT call {@code renderBackground()} explicitly here; {@code renderWithTooltip}
     * already calls it, and a second call throws
     * {@code IllegalStateException: Can only blur once per frame}.
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render child widgets (none yet — all TODOs in init()).
        super.render(context, mouseX, mouseY, delta);

        // === Background fills (all colors are ARGB) ===
        // Full-screen near-opaque dark overlay
        context.fill(0, 0, width, height, 0xD0101010);
        // Header bar (dark navy)
        context.fill(0, 0, width, HEADER_HEIGHT, 0xFF1A1A2E);
        // Header bottom border (blue accent)
        context.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF4A90D9);
        // Tab sidebar (dark navy)
        context.fill(0, HEADER_HEIGHT, TAB_SIDEBAR_WIDTH, height, 0xFF16213E);
        // Sidebar right border (blue accent)
        context.fill(TAB_SIDEBAR_WIDTH - 1, HEADER_HEIGHT, TAB_SIDEBAR_WIDTH, height, 0xFF4A90D9);

        // === Text and tab content ===
        renderHeader(context, mouseX, mouseY);
        renderTabSidebar(context, mouseX, mouseY);

        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).render(context, mouseX, mouseY, delta);
        }
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY) {
        // TODO: Render display name (editable), biome ID, action buttons
        // Placeholder: draw display name text
        // Colors are ARGB — 0xFFFFFFFF is opaque white, 0xFFAAAAAA is opaque light-gray.
        String displayName = state.getDisplayName().isEmpty() ? "Untitled Biome" : state.getDisplayName();
        context.drawText(textRenderer, displayName, TAB_SIDEBAR_WIDTH + 10, 10, 0xFFFFFFFF, true);

        String biomeId = state.getBiomeId().isEmpty() ? "strata_world:untitled" : state.getBiomeId();
        context.drawText(textRenderer, biomeId, TAB_SIDEBAR_WIDTH + 10, 24, 0xFFAAAAAA, false);
    }

    private void renderTabSidebar(DrawContext context, int mouseX, int mouseY) {
        // TODO: Render tab buttons in sidebar
        // Placeholder: draw tab labels with hover/active highlights.
        // Colors are ARGB — 0xFFFFFFFF is opaque white, 0xFF888888 is opaque mid-gray.
        String[] tabLabels = {"Visual", "Terrain", "Features", "Spawns", "Export"};
        for (int i = 0; i < tabLabels.length; i++) {
            int y = HEADER_HEIGHT + 5 + (i * 22);
            boolean isActive  = (i == activeTabIndex);
            boolean isHovered = mouseX >= 0 && mouseX < TAB_SIDEBAR_WIDTH
                                && mouseY >= y - 2 && mouseY < y + 18;

            // Row highlight fill (must use fill() before drawText() — both go through the
            // retained-mode pipeline, but fills occupy simpleElementRenderStates while text
            // occupies textElementRenderStates; the compositor renders simples after text,
            // so fills that exactly share a layer with text are drawn on top. To keep the
            // highlight visibly BEHIND the text label, use a low-alpha tint so the text is
            // legible regardless of order.  A full opaque highlight would need a separate
            // createNewRootLayer() call, which is deferred to the proper tab-button widgets.)
            if (isActive) {
                context.fill(1, y - 2, TAB_SIDEBAR_WIDTH - 2, y + 18, 0x40FFFFFF);
            } else if (isHovered) {
                context.fill(1, y - 2, TAB_SIDEBAR_WIDTH - 2, y + 18, 0x20FFFFFF);
            }

            int color = isActive ? 0xFFFFFFFF : (isHovered ? 0xFFCCCCCC : 0xFF888888);
            context.drawText(textRenderer, tabLabels[i], 10, y, color, false);
        }
    }

    /**
     * Handles mouse clicks. Clicks inside the tab sidebar switch the active tab.
     *
     * <p>In MC 1.21.11 the legacy {@code (double, double, int)} overload was replaced
     * by {@code (Click, boolean)} where {@link Click} carries position, button, and
     * modifier state, and the second argument indicates a double-click.
     *
     * @param click       carries {@code x()}, {@code y()}, and {@code button()} (0 = left)
     * @param doubleClick {@code true} if this is a double-click event
     * @return {@code true} if the click was consumed
     */
    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        // Hit-test the tab sidebar strip (below the header).
        if (click.button() == 0 && click.x() < TAB_SIDEBAR_WIDTH && click.y() >= HEADER_HEIGHT) {
            int relY     = (int) click.y() - HEADER_HEIGHT - 5;
            int tabIndex = relY / 22;
            if (relY >= 0 && tabIndex >= 0 && tabIndex < tabs.size()) {
                setActiveTab(tabIndex);
                return true;
            }
        }
        // Forward content-area clicks to the active tab before falling through to
        // registered child widgets (buttons / text fields added via addTabWidget).
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            if (tabs.get(activeTabIndex).mouseClicked(click, doubleClick)) {
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    /**
     * Handles keyboard input.
     *
     * <ul>
     *   <li>Ctrl+Z — undo</li>
     *   <li>Ctrl+Y — redo</li>
     *   <li>↑ / ↓  — cycle through the tab sidebar (wraps at bounds)</li>
     *   <li>Tab / Shift+Tab — same as ↓ / ↑</li>
     * </ul>
     *
     * All other keys are delegated to the superclass (and then to the active tab).
     *
     * @param keyInput the key input event
     * @return {@code true} if the key was consumed
     */
    @Override
    public boolean keyPressed(KeyInput keyInput) {
        var window = MinecraftClient.getInstance().getWindow();

        // ── Undo / Redo ────────────────────────────────────────────────────────
        boolean ctrlHeld = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrlHeld) {
            if (keyInput.key() == GLFW.GLFW_KEY_Z) { performUndo(); return true; }
            if (keyInput.key() == GLFW.GLFW_KEY_Y) { performRedo(); return true; }
        }

        // ── Tab sidebar navigation ─────────────────────────────────────────────
        boolean shiftHeld = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        int key = keyInput.key();
        boolean nextTab = (key == GLFW.GLFW_KEY_DOWN)
                || (key == GLFW.GLFW_KEY_TAB && !shiftHeld);
        boolean prevTab = (key == GLFW.GLFW_KEY_UP)
                || (key == GLFW.GLFW_KEY_TAB && shiftHeld);
        if (nextTab) {
            setActiveTab(Math.min(tabs.size() - 1, activeTabIndex + 1));
            return true;
        }
        if (prevTab) {
            setActiveTab(Math.max(0, activeTabIndex - 1));
            return true;
        }

        return super.keyPressed(keyInput);
    }

    private void performUndo() {
        BiomeEditorState previous = state.getUndoManager().undo(state);
        if (previous != null) {
            // TODO: apply previous state to the editor
        }
    }

    private void performRedo() {
        BiomeEditorState next = state.getUndoManager().redo(state);
        if (next != null) {
            // TODO: apply next state to the editor
        }
    }

    /**
     * Returns {@code false} so the game continues ticking while the editor is open,
     * allowing {@link PreviewZoneManager} debounce timers to fire normally.
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Called by {@link net.minecraft.client.MinecraftClient#setScreen} when this
     * screen is replaced or closed.
     *
     * <p>The {@link BiomeEditorSession} is intentionally NOT cleared here —
     * Layer 1 overrides must remain active so the player can explore the world
     * with changes applied and reopen the editor later. The session is cleared
     * on world disconnect via {@link io.strata.world.StrataWorldClient}'s
     * {@code ClientPlayConnectionEvents.DISCONNECT} handler.
     */
    @Override
    public void removed() {
        super.removed();
        // Do NOT call BiomeEditorSession.close() here.
        // Session lifetime = wand right-click → world disconnect, not screen lifetime.
    }

    /**
     * Exposes {@link #addDrawableChild} for tab implementations.
     *
     * <p>{@code Screen.addDrawableChild} is {@code protected}, so tab classes in the
     * {@code tabs} sub-package cannot call it directly. Tabs call this wrapper instead.
     *
     * @param <T>    any type that implements {@link Element}, {@link Drawable}, and
     *               {@link Selectable} — satisfied by all standard MC widget types
     * @param widget the widget to register
     * @return the widget, for chaining
     */
    public <T extends Element & Drawable & Selectable> T addTabWidget(T widget) {
        return addDrawableChild(widget);
    }

    /** Returns the current editor state. */
    public BiomeEditorState getState() { return state; }

    /**
     * Returns the undo manager for this editor session.
     * Delegates to {@link BiomeEditorState#getUndoManager()}.
     */
    public BiomeEditorState.UndoManager getUndoManager() { return state.getUndoManager(); }

    /**
     * Loads a biome from an existing JSON file into the editor state.
     *
     * <p>If there are unsaved changes, a confirmation prompt should be shown
     * before proceeding. Once confirmed, the JSON is parsed into a new
     * {@link BiomeEditorState}, the editor state is replaced, and a Reset World
     * regen is triggered to apply the new parameters in the preview zone.
     *
     * @param jsonPath the path to the biome JSON file to load
     * @todo Implement: parse JSON → populate state → unsaved-change prompt → Reset World regen (SPEC §11 Phase 2)
     */
    public void loadBiome(Path jsonPath) {
        // TODO: Implement load biome from list
        // 1. If state.isDirty() || !state.isExported(), show confirmation prompt
        // 2. Read and parse the biome JSON at jsonPath into a new BiomeEditorState
        // 3. Replace the current state's fields with the loaded values
        // 4. Trigger a Reset World regen to apply the new structural parameters
        StrataLogger.debug("loadBiome() called for path {} — not yet implemented", jsonPath);
    }

    /**
     * Called by the {@code ASSET_REGISTERED} event listener to refresh
     * the feature and spawn lists in the Features and Spawns tabs.
     */
    public static void notifyFeatureListUpdated() {
        // TODO: If an editor screen is currently open, refresh its feature/spawn lists
    }
}
