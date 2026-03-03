package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Visual tab — RGB sliders and weather toggles for all Layer 1 color properties.
 *
 * <p>Shows sky, fog, water, water-fog, grass, and foliage colors as clickable
 * swatch rows. Clicking a row selects it; R/G/B sliders below reflect the
 * selected color and apply changes as you drag. The hex value is shown as a
 * read-only label under the sliders.
 *
 * <p>Sky and fog update the rendered world immediately (they are resolved
 * per-frame by {@code EnvironmentAttributeMapMixin}). Water, water-fog, grass,
 * and foliage are baked into chunk meshes; a {@link net.minecraft.client.render.WorldRenderer#reload()}
 * is triggered automatically 750 ms after the last slider movement to rebuild
 * the meshes without hammering the GPU on every drag tick.
 *
 * <p>Grass and foliage default to {@code -1} (biome-computed tint). Their sliders
 * start at 0 / black when first selected. Moving a slider locks in a specific RGB
 * and disables the auto-tint for that property.
 *
 * <p>Weather is toggled with three buttons: Rain, Snow, None.
 *
 * <p>All changes are Layer 1 — they affect the preview world immediately without
 * chunk regeneration (except mesh-baked colors, which use the debounced reload).
 */
public class VisualTab extends EditorTab {

    /** Display labels for the six color properties, in accessor order. */
    private static final String[] COLOR_LABELS = {
        "Sky", "Fog", "Water", "Water Fog", "Grass", "Foliage"
    };

    /** Vertical stride between color rows in pixels. */
    private static final int ROW_STRIDE = 22;

    /**
     * Debounce delay (ms) before triggering {@code WorldRenderer.reload()} after
     * a chunk-mesh color change (water, water-fog, grass, foliage).
     * Long enough to avoid rebuilding on every drag tick (~50 ms per frame × 15
     * frames = ~750 ms minimum user pause).
     */
    private static final long CHUNK_RELOAD_DEBOUNCE_MS = 750L;

    /** Index (0-5) of the color row currently selected for editing. */
    private int selectedColorIndex = 0;

    // ── RGB sliders ──────────────────────────────────────────────────────────

    /** Slider for the red channel (0–255) of the selected color. */
    private ChannelSlider rSlider;
    /** Slider for the green channel (0–255) of the selected color. */
    private ChannelSlider gSlider;
    /** Slider for the blue channel (0–255) of the selected color. */
    private ChannelSlider bSlider;

    /**
     * When non-zero, a chunk mesh rebuild is pending and should fire once
     * {@code System.currentTimeMillis() >= chunkReloadAfter}.
     * Checked and cleared in {@link #render} to avoid a dedicated tick() hook.
     */
    private long chunkReloadAfter = 0L;

    // ── Inner class: ChannelSlider ───────────────────────────────────────────

    /**
     * A single R, G, or B channel slider (0–255 range).
     *
     * <p>Calls {@link VisualTab#onSliderChanged()} from {@link #applyValue()} so
     * the selected color property in {@link BiomeEditorState} is updated on every
     * drag tick.
     *
     * <p>The display message format is {@code "R: 128"} / {@code "G: 64"} / etc.
     */
    private class ChannelSlider extends SliderWidget {

        /** 0 = Red, 1 = Green, 2 = Blue. */
        private final int channel;

        ChannelSlider(int x, int y, int width, int channel) {
            super(x, y, width, 14, Text.empty(), 0.0);
            this.channel = channel;
            updateMessage();
        }

        /** Refreshes the label text to reflect the current slider position. */
        @Override
        protected void updateMessage() {
            String label = switch (channel) {
                case 0 -> "R";
                case 1 -> "G";
                default -> "B";
            };
            setMessage(Text.literal(label + ": " + getComponent()));
        }

        /**
         * Called on every drag tick; propagates the slider value to the active
         * color property in the editor state and schedules a debounced chunk
         * reload for mesh-baked colors.
         */
        @Override
        protected void applyValue() {
            onSliderChanged();
        }

        /**
         * Sets this slider's position from an integer component value (0–255).
         * Does NOT call {@link #applyValue()}, so this can be used to sync the
         * slider to a new color without triggering a feedback loop.
         */
        void setComponent(int val) {
            this.value = Math.clamp(val, 0, 255) / 255.0;
            updateMessage();
        }

        /** Returns the current slider position as an integer (0–255). */
        int getComponent() {
            return (int) Math.round(value * 255.0);
        }
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    public VisualTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Visual";
    }

    // ── Color accessor helpers ───────────────────────────────────────────────

    /**
     * Returns the color for the property at {@code index}.
     * Grass (4) and Foliage (5) return {@code -1} when using the biome default.
     */
    private int getColor(int index) {
        return switch (index) {
            case 0 -> state.getSkyColor();
            case 1 -> state.getFogColor();
            case 2 -> state.getWaterColor();
            case 3 -> state.getWaterFogColor();
            case 4 -> state.getGrassColor();
            case 5 -> state.getFoliageColor();
            default -> 0;
        };
    }

    /** Sets the color for the property at {@code index}. */
    private void setColor(int index, int color) {
        switch (index) {
            case 0 -> state.setSkyColor(color);
            case 1 -> state.setFogColor(color);
            case 2 -> state.setWaterColor(color);
            case 3 -> state.setWaterFogColor(color);
            case 4 -> state.setGrassColor(color);
            case 5 -> state.setFoliageColor(color);
        }
    }

    /** Returns the absolute screen-y for the top of color row {@code i}. */
    private int rowY(int i) {
        return y + 28 + i * ROW_STRIDE;
    }

    // ── Slider callbacks ─────────────────────────────────────────────────────

    /**
     * Called by each {@link ChannelSlider#applyValue()} on every drag tick.
     * Assembles R/G/B into a packed RGB int, writes it to the state, and
     * schedules a debounced {@code worldRenderer.reload()} for mesh-baked colors.
     */
    private void onSliderChanged() {
        if (rSlider == null || gSlider == null || bSlider == null) return;
        int r = rSlider.getComponent();
        int g = gSlider.getComponent();
        int b = bSlider.getComponent();
        int color = (r << 16) | (g << 8) | b;
        setColor(selectedColorIndex, color);

        // Water (2), Water Fog (3), Grass (4), Foliage (5) are baked into chunk
        // meshes; schedule a debounced reload rather than calling reload() every tick.
        if (selectedColorIndex >= 2) {
            chunkReloadAfter = System.currentTimeMillis() + CHUNK_RELOAD_DEBOUNCE_MS;
        }
    }

    /**
     * Syncs the R/G/B sliders to the color of the currently selected row.
     * Grass/Foliage with value {@code -1} (auto) show all sliders at 0.
     */
    private void updateSliders() {
        if (rSlider == null) return;
        int color = getColor(selectedColorIndex);
        if (color < 0) color = 0; // auto sentinel → show as black
        rSlider.setComponent((color >> 16) & 0xFF);
        gSlider.setComponent((color >>  8) & 0xFF);
        bSlider.setComponent( color        & 0xFF);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);

        // RGB sliders — sit just below the last color row
        int sliderY = rowY(COLOR_LABELS.length) + 4;
        int sliderW = width - 20; // full content width minus left/right padding

        rSlider = new ChannelSlider(x + 10, sliderY,      sliderW, 0);
        gSlider = new ChannelSlider(x + 10, sliderY + 18, sliderW, 1);
        bSlider = new ChannelSlider(x + 10, sliderY + 36, sliderW, 2);
        screen.addTabWidget(rSlider);
        screen.addTabWidget(gSlider);
        screen.addTabWidget(bSlider);
        updateSliders();

        // Precipitation buttons — below the sliders + hex label row
        // sliderY + 50 (three sliders) + 10 (gap) + 9 (hex label) + 12 (gap) + 14 (precip label) + 4 = sliderY + 99
        int weatherBtnY = sliderY + 99;
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Rain"), b -> {
            state.setHasRain(true);
            state.setHasSnow(false);
        }).dimensions(x + 10, weatherBtnY, 36, 14).build());

        screen.addTabWidget(ButtonWidget.builder(Text.literal("Snow"), b -> {
            state.setHasRain(false);
            state.setHasSnow(true);
        }).dimensions(x + 50, weatherBtnY, 36, 14).build());

        screen.addTabWidget(ButtonWidget.builder(Text.literal("None"), b -> {
            state.setHasRain(false);
            state.setHasSnow(false);
        }).dimensions(x + 90, weatherBtnY, 36, 14).build());
    }

    // ── Input ────────────────────────────────────────────────────────────────

    /**
     * Hit-tests the color-swatch rows. Clicking a row selects it and syncs the
     * R/G/B sliders to that row's current color.
     */
    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() != 0) return false;
        int mx = (int) click.x();
        int my = (int) click.y();
        for (int i = 0; i < COLOR_LABELS.length; i++) {
            int ry = rowY(i);
            if (mx >= x + 5 && mx < x + width && my >= ry - 2 && my < ry + 14) {
                selectedColorIndex = i;
                updateSliders();
                return true;
            }
        }
        return false;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;

        // ── Debounced chunk mesh reload ───────────────────────────────────────
        // Checked here (rather than in a tick hook) to keep the implementation
        // self-contained. Fires at most once per debounce window.
        if (chunkReloadAfter > 0L && System.currentTimeMillis() >= chunkReloadAfter) {
            chunkReloadAfter = 0L;
            var mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                mc.worldRenderer.reload();
            }
        }

        // ── Section header ───────────────────────────────────────────────────
        context.drawText(tr, "Visual Properties", x + 10, y + 10, 0xFFFFFFFF, true);

        // ── Color rows ───────────────────────────────────────────────────────
        for (int i = 0; i < COLOR_LABELS.length; i++) {
            int ry = rowY(i);
            boolean isSelected = (i == selectedColorIndex);
            boolean isHovered  = mouseX >= x + 5 && mouseX < x + width
                                  && mouseY >= ry - 2 && mouseY < ry + 14;

            // Row highlight (low alpha to keep text legible)
            if (isSelected) {
                context.fill(x + 5, ry - 2, x + 240, ry + 14, 0x30FFFFFF);
            } else if (isHovered) {
                context.fill(x + 5, ry - 2, x + 240, ry + 14, 0x18FFFFFF);
            }

            // Label
            int labelColor = isSelected ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawText(tr, COLOR_LABELS[i], x + 10, ry, labelColor, false);

            // Color swatch: 1px border + filled interior
            int rawColor = getColor(i);
            int swatchFill = rawColor < 0 ? 0xFF3A3A3A : (0xFF000000 | (rawColor & 0xFFFFFF));
            context.fill(x + 88, ry - 1, x + 110, ry + 10, 0xFF555555); // border
            context.fill(x + 89, ry,     x + 109, ry +  9, swatchFill);

            // Hex value label
            String hexText = rawColor < 0 ? "auto" : String.format("#%06X", rawColor & 0xFFFFFF);
            context.drawText(tr, hexText, x + 114, ry, 0xFF888888, false);
        }

        // ── Hex label (read-only display under the sliders) ──────────────────
        int sliderY = rowY(COLOR_LABELS.length) + 4;
        int hexLabelY = sliderY + 60; // 50px (three 14px sliders + 2×8px gaps) + 10px margin
        int selectedColor = getColor(selectedColorIndex);
        String selectedHex = selectedColor < 0
                ? COLOR_LABELS[selectedColorIndex] + ": auto"
                : COLOR_LABELS[selectedColorIndex] + ": #" + String.format("%06X", selectedColor & 0xFFFFFF);
        context.drawText(tr, selectedHex, x + 10, hexLabelY, 0xFF888888, false);

        // ── Weather section ───────────────────────────────────────────────────
        int weatherBtnY = sliderY + 99;
        int weatherLabelY = weatherBtnY - 14; // "Precipitation:" label, one text row above buttons
        context.drawText(tr, "Precipitation:", x + 10, weatherLabelY, 0xFFAAAAAA, false);

        // Active-indicator bar above the active precipitation button
        String activePrecip = state.hasSnow() ? "Snow" : state.hasRain() ? "Rain" : "None";
        int indicatorX = switch (activePrecip) {
            case "Rain" -> x + 10;
            case "Snow" -> x + 50;
            default     -> x + 90;
        };
        context.fill(indicatorX, weatherBtnY - 3, indicatorX + 36, weatherBtnY - 1, 0xFF4A90D9);
    }
}
