package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import io.strata.world.editor.PreviewZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Terrain tab — six sliders for multi-noise parameters.
 *
 * <p>Controls temperature, humidity, continentalness, erosion, weirdness,
 * and depth. Each slider maps a normalized [0, 1] range to the parameter's
 * actual range and displays the current value.
 *
 * <p>A "Refresh Preview" button forces immediate chunk regeneration.
 * An "Auto-refresh" toggle controls whether changes automatically trigger
 * debounced regen via {@link PreviewZoneManager}.
 *
 * <p>Changes are Layer 2 — they trigger a 3-second debounced chunk
 * regeneration via {@link PreviewZoneManager}.
 */
public class TerrainTab extends EditorTab {

    /** Slider definitions: label, min, max. */
    private static final NoiseParam[] PARAMS = {
        new NoiseParam("Temperature",       0.0f, 2.0f),
        new NoiseParam("Humidity",          0.0f, 1.0f),
        new NoiseParam("Continentalness",  -1.0f, 1.0f),
        new NoiseParam("Erosion",          -1.0f, 1.0f),
        new NoiseParam("Weirdness",        -1.0f, 1.0f),
        new NoiseParam("Depth",             0.0f, 1.0f),
    };

    /** Vertical stride between slider rows in pixels. */
    private static final int ROW_STRIDE = 30;

    private NoiseSlider[] sliders;
    private boolean autoRefresh = true;

    public TerrainTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Terrain";
    }

    // ── Noise parameter value accessors ─────────────────────────────────────

    private float getParamValue(int index) {
        return switch (index) {
            case 0 -> state.getTemperature();
            case 1 -> state.getHumidity();
            case 2 -> state.getContinentalness();
            case 3 -> state.getErosion();
            case 4 -> state.getWeirdness();
            case 5 -> state.getDepth();
            default -> 0.0f;
        };
    }

    private void setParamValue(int index, float value) {
        switch (index) {
            case 0 -> state.setTemperature(value);
            case 1 -> state.setHumidity(value);
            case 2 -> state.setContinentalness(value);
            case 3 -> state.setErosion(value);
            case 4 -> state.setWeirdness(value);
            case 5 -> state.setDepth(value);
        }
        if (autoRefresh) {
            PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
            if (pzm != null) {
                pzm.onLayer2Changed();
            }
        }
    }

    // ── Inner class: NoiseParam ──────────────────────────────────────────────

    private record NoiseParam(String label, float min, float max) {
        float range() { return max - min; }
        float toNormalized(float value) { return (value - min) / range(); }
        float fromNormalized(double normalized) { return (float) (min + normalized * range()); }
    }

    // ── Inner class: NoiseSlider ─────────────────────────────────────────────

    /**
     * A slider for a single multi-noise parameter with custom range display.
     */
    private class NoiseSlider extends SliderWidget {

        private final int paramIndex;

        NoiseSlider(int x, int y, int width, int paramIndex, double initialValue) {
            super(x, y, width, 20, Text.empty(), initialValue);
            this.paramIndex = paramIndex;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            NoiseParam param = PARAMS[paramIndex];
            float actual = param.fromNormalized(value);
            setMessage(Text.literal(String.format("%s: %.2f", param.label, actual)));
        }

        @Override
        protected void applyValue() {
            NoiseParam param = PARAMS[paramIndex];
            float actual = param.fromNormalized(value);
            setParamValue(paramIndex, actual);
        }

        /** Sets the slider from an actual parameter value without triggering applyValue. */
        void setFromActual(float actual) {
            this.value = Math.clamp(PARAMS[paramIndex].toNormalized(actual), 0.0, 1.0);
            updateMessage();
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);

        int sliderW = Math.min(width - 20, 260);
        sliders = new NoiseSlider[PARAMS.length];

        for (int i = 0; i < PARAMS.length; i++) {
            int sliderY = y + 28 + i * ROW_STRIDE;
            float currentVal = getParamValue(i);
            double normalized = PARAMS[i].toNormalized(currentVal);
            sliders[i] = new NoiseSlider(x + 10, sliderY, sliderW, i, normalized);
            screen.addTabWidget(sliders[i]);
        }

        // Buttons below the sliders
        int btnY = y + 28 + PARAMS.length * ROW_STRIDE + 12;

        // Refresh Preview button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Refresh Preview"), b -> {
            PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
            if (pzm != null) {
                pzm.forceRegeneration();
            }
        }).dimensions(x + 10, btnY, 110, 16).build());

        // Auto-refresh toggle
        screen.addTabWidget(ButtonWidget.builder(
                Text.literal(autoRefresh ? "Auto: ON" : "Auto: OFF"), b -> {
            autoRefresh = !autoRefresh;
            b.setMessage(Text.literal(autoRefresh ? "Auto: ON" : "Auto: OFF"));
        }).dimensions(x + 126, btnY, 70, 16).build());
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;

        // Section header
        context.drawText(tr, "Terrain Parameters", x + 10, y + 10, 0xFFFFFFFF, true);

        // Range labels to the right of each slider
        if (sliders != null) {
            int sliderW = Math.min(width - 20, 260);
            for (int i = 0; i < PARAMS.length; i++) {
                int sliderY = y + 28 + i * ROW_STRIDE;
                NoiseParam param = PARAMS[i];
                String rangeText = String.format("[%.1f, %.1f]", param.min, param.max);
                context.drawText(tr, rangeText, x + 14 + sliderW, sliderY + 3, 0xFF666666, false);
            }
        }

        // Status line: show if preview zone is regenerating
        int statusY = y + 28 + PARAMS.length * ROW_STRIDE + 34;
        PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
        if (pzm != null && pzm.isRegenerating()) {
            context.drawText(tr, "Refreshing preview...", x + 10, statusY, 0xFFFFAA00, false);
        }

        // Show the current biome at player's feet
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null) {
            var biomeEntry = mc.world.getBiome(mc.player.getBlockPos());
            String biomeName = biomeEntry.getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("unknown");
            context.drawText(tr, "Current biome: " + biomeName,
                    x + 10, statusY + 14, 0xFF888888, false);
        }
    }
}
