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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    /** Row height for entries in the Load biome picker dropdown. */
    private static final int LOAD_PICKER_ROW_H = 14;

    /** Maximum rows visible in the Load biome picker before scrolling. */
    private static final int LOAD_PICKER_MAX_VISIBLE = 10;

    private final BiomeEditorState state;
    private final List<EditorTab> tabs;
    private int activeTabIndex = 0;

    /** Editable display name in the header bar. */
    private TextFieldWidget displayNameField;

    /**
     * When non-null, a list of saved-biome JSON paths is shown as a dropdown
     * picker anchored below the Load button.
     */
    private List<Path> loadPickerPaths = null;

    /** Scroll offset into {@link #loadPickerPaths}. */
    private int loadPickerScroll = 0;

    /** {@code true} when the Reset World button in the header is waiting for a second click. */
    private boolean resetConfirmPending = false;

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

        // ── Header bar: editable display name field ──────────────────────────
        // "Name:" label is drawn in renderHeader(); the field sits to its right.
        int nameFieldX = TAB_SIDEBAR_WIDTH + 50;
        int nameFieldW = Math.min(width - nameFieldX - 220, 180);
        displayNameField = new TextFieldWidget(
                textRenderer,
                nameFieldX, 5, nameFieldW, 16,
                Text.literal("Biome display name"));
        displayNameField.setMaxLength(64);
        displayNameField.setText(state.getDisplayName().isEmpty() ? "" : state.getDisplayName());
        displayNameField.setPlaceholder(Text.literal("Untitled Biome"));
        displayNameField.setDrawsBackground(true);
        // Update the display name & auto-derived biome ID on every keystroke
        displayNameField.setChangedListener(text -> {
            String stripped = text.strip();
            if (!stripped.equals(state.getDisplayName())) {
                state.setDisplayName(stripped);
            }
        });
        addDrawableChild(displayNameField);

        // ── Header bar: Load button ──────────────────────────────────────────
        // Clicking opens a dropdown of saved biome JSON files from strata_biomes/.
        // Clicking the same button again (or pressing Escape) dismisses the picker.
        int loadBtnX = nameFieldX + nameFieldW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal("Load"), b -> {
            if (loadPickerPaths != null) {
                // Toggle off
                loadPickerPaths = null;
            } else {
                loadPickerPaths = scanSavedBiomes();
                loadPickerScroll = 0;
            }
        }).dimensions(loadBtnX, 5, 36, 16).build());

        // ── Header bar: Reset World button ───────────────────────────────────
        // Two-click confirm pattern: first click changes the label; second executes.
        int resetBtnX = width - 100;
        Text resetLabel = resetConfirmPending
                ? Text.literal("Confirm?")
                : Text.literal("Reset World");
        addDrawableChild(ButtonWidget.builder(resetLabel, b -> {
            if (resetConfirmPending) {
                resetConfirmPending = false;
                PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
                if (pzm != null) {
                    pzm.resetWorld();
                }
            } else {
                resetConfirmPending = true;
                b.setMessage(Text.literal("Confirm?"));
            }
        }).dimensions(resetBtnX, 5, 90, 16).build());

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
            PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
            if (pzm != null) pzm.cancelLayer1Snapshot();
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
        // === Background fills FIRST (all colors are ARGB) ===
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
        // Content area — lighter background so widgets (buttons, sliders, text fields) are readable
        context.fill(TAB_SIDEBAR_WIDTH, HEADER_HEIGHT, width, height, 0xC0202038);

        // === Text labels and tab content (behind widgets) ===
        renderHeader(context, mouseX, mouseY);
        renderTabSidebar(context, mouseX, mouseY);

        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).render(context, mouseX, mouseY, delta);
        }

        // === Child widgets (buttons, sliders, text fields) rendered LAST = on top ===
        super.render(context, mouseX, mouseY, delta);

        // === Load picker dropdown — drawn above everything else ===
        if (loadPickerPaths != null) {
            renderLoadPicker(context, mouseX, mouseY);
        }
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY) {
        // "Name:" label to the left of the display name text field
        context.drawText(textRenderer, "Name:", TAB_SIDEBAR_WIDTH + 8, 8, 0xFFCCCCCC, false);

        // Below the display name field, show the auto-derived biome ID
        String biomeId = state.getBiomeId().isEmpty() ? "strata_world:untitled" : state.getBiomeId();
        context.drawText(textRenderer, biomeId, TAB_SIDEBAR_WIDTH + 8, 26, 0xFFAAAAAA, false);

        // Show template source if present (e.g. "Loaded template: minecraft:plains")
        String tmpl = state.getTemplateSource();
        if (tmpl != null && !tmpl.isEmpty()) {
            context.drawText(textRenderer, "Loaded template: " + tmpl,
                    TAB_SIDEBAR_WIDTH + 200, 26, 0xFF66AAFF, false);
        }

        // Unsaved/unexported indicator — placed to the left of the Reset World button
        if (!state.isExported() && state.isDirty()) {
            context.drawText(textRenderer, "\u25CF Unsaved", width - 170, 10, 0xFFFFAA00, false);
        } else if (state.isExported()) {
            context.drawText(textRenderer, "\u2713 Exported", width - 170, 10, 0xFF44FF44, false);
        }
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
        // ── Load picker dropdown ─────────────────────────────────────────────
        if (loadPickerPaths != null) {
            int pickerResult = hitTestLoadPicker((int) click.x(), (int) click.y());
            if (pickerResult >= 0) {
                // User selected a file
                Path chosen = loadPickerPaths.get(loadPickerScroll + pickerResult);
                loadPickerPaths = null;
                loadBiome(chosen);
                return true;
            }
            // Click outside the picker — dismiss it
            loadPickerPaths = null;
            // Don't consume — fall through so the click still registers normally
        }

        // ── Tab sidebar ──────────────────────────────────────────────────────
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (loadPickerPaths != null && !loadPickerPaths.isEmpty()) {
            int delta = verticalAmount > 0 ? -1 : 1;
            int maxScroll = Math.max(0, loadPickerPaths.size() - LOAD_PICKER_MAX_VISIBLE);
            loadPickerScroll = Math.max(0, Math.min(loadPickerScroll + delta, maxScroll));
            return true;
        }
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            if (tabs.get(activeTabIndex).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

        // ── Escape: dismiss load picker or reset-confirm, then fall through ───
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (loadPickerPaths != null) {
                loadPickerPaths = null;
                return true;
            }
            if (resetConfirmPending) {
                resetConfirmPending = false;
                // Re-init to restore the button label
                clearChildren();
                init();
                return true;
            }
        }

        // ── Undo / Redo ────────────────────────────────────────────────────────
        boolean ctrlHeld = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrlHeld) {
            if (keyInput.key() == GLFW.GLFW_KEY_Z) { performUndo(); return true; }
            if (keyInput.key() == GLFW.GLFW_KEY_Y) { performRedo(); return true; }
        }

        // ── Delegate to active tab (e.g. suggestion list navigation) ───────────
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            if (tabs.get(activeTabIndex).keyPressed(keyInput)) {
                return true;
            }
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
            state.restoreFrom(previous);
            // Rebuild widgets to reflect the restored state
            clearChildren();
            init();
            // Trigger a chunk reload so mesh-baked colors update
            var mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                mc.worldRenderer.reload();
            }
        }
    }

    private void performRedo() {
        BiomeEditorState next = state.getUndoManager().redo(state);
        if (next != null) {
            state.restoreFrom(next);
            clearChildren();
            init();
            var mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                mc.worldRenderer.reload();
            }
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
     * Loads a saved biome JSON or draft JSON from {@code jsonPath} into the
     * editor state, then triggers a Reset World so the new parameters take
     * effect in the preview zone.
     *
     * <p>The file is expected to be a {@link BiomeEditorState} JSON written by
     * {@link BiomeEditorState#saveDraft} or by the Export tab. If the state
     * cannot be parsed (e.g. it is a vanilla biome JSON) a warning is logged
     * and the load is silently skipped.
     *
     * @param jsonPath the path to the biome JSON file to load
     */
    public void loadBiome(Path jsonPath) {
        BiomeEditorState loaded = BiomeEditorState.loadDraft(jsonPath);
        if (loaded == null) {
            StrataLogger.warn("loadBiome: could not parse state from {}", jsonPath);
            return;
        }
        state.restoreFrom(loaded);
        clearChildren();
        init();

        // Trigger a Reset World so structural parameters (features/spawns) apply
        PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
        if (pzm != null) {
            pzm.resetWorld();
        }
        StrataLogger.info("Loaded biome state from {}", jsonPath);
    }

    /**
     * Scans {@code strata_biomes/} in the current world save for loadable JSON files.
     *
     * <p>Returns all {@code *.json} files in that directory, excluding the active
     * session draft ({@code _session.draft.json}). The list is sorted alphabetically.
     *
     * @return a mutable list of paths; empty if the directory does not exist or
     *         no files are found
     */
    private List<Path> scanSavedBiomes() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() == null) return new ArrayList<>();
        Path biomesDir = mc.getServer().getSavePath(WorldSavePath.ROOT).resolve("strata_biomes");
        if (!Files.isDirectory(biomesDir)) return new ArrayList<>();
        try {
            return Files.list(biomesDir)
                    .filter(p -> p.getFileName().toString().endsWith(".json")
                            && !p.getFileName().toString().equals("_session.draft.json"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            StrataLogger.warn("scanSavedBiomes: could not list {}: {}", biomesDir, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Load picker geometry constants (derived from button position set in init()) ──

    /** X origin of the Load picker dropdown (aligns with the Load button). */
    private int loadPickerX() {
        // nameFieldX + nameFieldW + 6  (same origin as the Load button in init())
        return TAB_SIDEBAR_WIDTH + 50 + Math.min(width - (TAB_SIDEBAR_WIDTH + 50) - 220, 180) + 6;
    }

    /** Width of the Load picker dropdown. */
    private static final int LOAD_PICKER_WIDTH = 240;

    /**
     * Renders the Load-biome picker dropdown below the Load button.
     */
    private void renderLoadPicker(DrawContext context, int mouseX, int mouseY) {
        int px = loadPickerX();
        int py = HEADER_HEIGHT + 2; // just below the header bar

        int visibleCount = Math.min(LOAD_PICKER_MAX_VISIBLE,
                loadPickerPaths.isEmpty() ? 1 : loadPickerPaths.size() - loadPickerScroll);
        int totalH = visibleCount * LOAD_PICKER_ROW_H + 4;

        // Background + border
        context.fill(px, py, px + LOAD_PICKER_WIDTH, py + totalH, 0xF0222244);
        context.fill(px, py, px + LOAD_PICKER_WIDTH, py + 1, 0xFF4A90D9);
        context.fill(px, py + totalH - 1, px + LOAD_PICKER_WIDTH, py + totalH, 0xFF4A90D9);
        context.fill(px, py, px + 1, py + totalH, 0xFF4A90D9);
        context.fill(px + LOAD_PICKER_WIDTH - 1, py, px + LOAD_PICKER_WIDTH, py + totalH, 0xFF4A90D9);

        if (loadPickerPaths.isEmpty()) {
            context.drawText(textRenderer, "No saved biomes found", px + 4, py + 4, 0xFF888888, false);
            return;
        }

        int drawn = Math.min(visibleCount, loadPickerPaths.size() - loadPickerScroll);
        for (int vi = 0; vi < drawn; vi++) {
            int si = loadPickerScroll + vi;
            int rowTop = py + 2 + vi * LOAD_PICKER_ROW_H;
            boolean hovered = mouseX >= px && mouseX < px + LOAD_PICKER_WIDTH
                    && mouseY >= rowTop && mouseY < rowTop + LOAD_PICKER_ROW_H;
            if (hovered) {
                context.fill(px + 1, rowTop, px + LOAD_PICKER_WIDTH - 1,
                        rowTop + LOAD_PICKER_ROW_H, 0x50FFFFFF);
            }
            String name = loadPickerPaths.get(si).getFileName().toString();
            // Strip .json extension for readability
            if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
            int textW = textRenderer.getWidth(name);
            if (textW > LOAD_PICKER_WIDTH - 8) {
                while (textRenderer.getWidth(name + "…") > LOAD_PICKER_WIDTH - 8
                        && name.length() > 4) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            context.drawText(textRenderer, name, px + 4, rowTop + 3, 0xFFCCCCCC, false);
        }

        // Scroll indicators
        if (loadPickerScroll > 0) {
            context.drawText(textRenderer, "▲", px + LOAD_PICKER_WIDTH - 12, py + 2, 0xFF4A90D9, false);
        }
        if (loadPickerScroll + visibleCount < loadPickerPaths.size()) {
            context.drawText(textRenderer, "▼", px + LOAD_PICKER_WIDTH - 12,
                    py + totalH - LOAD_PICKER_ROW_H, 0xFF4A90D9, false);
        }
    }

    /**
     * Returns the 0-based row index under ({@code mx}, {@code my}) within the
     * load picker, or {@code -1} if the coordinates are outside the picker.
     */
    private int hitTestLoadPicker(int mx, int my) {
        if (loadPickerPaths == null || loadPickerPaths.isEmpty()) return -1;
        int px = loadPickerX();
        int py = HEADER_HEIGHT + 2;
        int visibleCount = Math.min(LOAD_PICKER_MAX_VISIBLE, loadPickerPaths.size() - loadPickerScroll);
        int totalH = visibleCount * LOAD_PICKER_ROW_H + 4;
        if (mx < px || mx >= px + LOAD_PICKER_WIDTH || my < py || my >= py + totalH) return -1;
        int relY = my - py - 2;
        if (relY < 0) return -1;
        int row = relY / LOAD_PICKER_ROW_H;
        return (row < visibleCount) ? row : -1;
    }

    /**
     * Called by the {@code ASSET_REGISTERED} event listener to refresh
     * the feature and spawn lists in the Features and Spawns tabs.
     */
    public static void notifyFeatureListUpdated() {
        // TODO: If an editor screen is currently open, refresh its feature/spawn lists
    }
}
