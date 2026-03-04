package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import io.strata.world.editor.PreviewZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Features tab — scrollable list of placed-feature identifiers.
 *
 * <p>Players add feature IDs via a text field, toggle them on/off by
 * removing from the active list, and the preview zone regenerates on change.
 *
 * <p>Changes are Layer 2 — they trigger debounced chunk regeneration
 * via {@link PreviewZoneManager}.
 */
public class FeaturesTab extends EditorTab {

    /** Height of each feature list row in pixels. */
    private static final int ROW_HEIGHT = 16;

    /** Maximum visible rows before scrolling is needed. */
    private static final int MAX_VISIBLE_ROWS = 12;

    /** Vertical scroll offset (in rows) for the active features list. */
    private int scrollOffset = 0;

    /** The text field for entering new feature IDs. */
    private TextFieldWidget addField;

    public FeaturesTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Features";
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);

        int fieldW = Math.min(width - 80, 240);

        // Text field for entering feature identifiers
        addField = new TextFieldWidget(
                MinecraftClient.getInstance().textRenderer,
                x + 10, y + 28, fieldW, 14,
                Text.literal("Feature ID"));
        addField.setMaxLength(128);
        addField.setPlaceholder(Text.literal("minecraft:trees_birch_and_oak"));
        screen.addTabWidget(addField);

        // Add button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Add"), b -> {
            String id = addField.getText().strip();
            if (!id.isEmpty() && !state.getFeatures().contains(id)) {
                state.getFeatures().add(id);
                state.setFeatures(state.getFeatures()); // triggers markDirty
                addField.setText("");
                notifyLayer2Changed();
            }
        }).dimensions(x + 14 + fieldW, y + 28, 40, 14).build());

        // Clear All button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Clear All"), b -> {
            if (!state.getFeatures().isEmpty()) {
                state.setFeatures(new ArrayList<>());
                scrollOffset = 0;
                notifyLayer2Changed();
            }
        }).dimensions(x + 10, y + height - 24, 60, 14).build());
    }

    private void notifyLayer2Changed() {
        PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
        if (pzm != null) {
            pzm.onLayer2Changed();
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() != 0) return false;

        int mx = (int) click.x();
        int my = (int) click.y();

        // Check if user clicked on a remove [x] button
        int listY = y + 50;
        List<String> features = state.getFeatures();
        int listBottom = y + height - 30;
        int maxRows = Math.min(MAX_VISIBLE_ROWS, (listBottom - listY) / ROW_HEIGHT);
        int visibleRows = Math.min(features.size() - scrollOffset, maxRows);

        for (int i = 0; i < visibleRows; i++) {
            int rowY = listY + i * ROW_HEIGHT;
            int removeX = x + width - 30;
            // Hit-test the [x] area
            if (mx >= removeX && mx < removeX + 16 && my >= rowY && my < rowY + ROW_HEIGHT) {
                int featureIndex = scrollOffset + i;
                if (featureIndex < features.size()) {
                    features.remove(featureIndex);
                    state.setFeatures(features); // triggers markDirty
                    if (scrollOffset > 0 && scrollOffset >= features.size()) {
                        scrollOffset = Math.max(0, features.size() - maxRows);
                    }
                    notifyLayer2Changed();
                    return true;
                }
            }
        }
        return false;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;

        // Section header
        List<String> features = state.getFeatures();
        context.drawText(tr, "Features (" + features.size() + ")",
                x + 10, y + 10, 0xFFFFFFFF, true);

        // Feature list area
        int listY = y + 50;
        int listBottom = y + height - 30;
        int maxRows = Math.min(MAX_VISIBLE_ROWS, (listBottom - listY) / ROW_HEIGHT);
        int visibleRows = Math.min(features.size() - scrollOffset, maxRows);

        // Scroll indicator
        if (features.size() > maxRows) {
            String scrollText = String.format("(%d-%d of %d)",
                    scrollOffset + 1,
                    Math.min(scrollOffset + maxRows, features.size()),
                    features.size());
            context.drawText(tr, scrollText, x + width - 90, y + 10, 0xFF666666, false);
        }

        // Feature rows
        for (int i = 0; i < visibleRows; i++) {
            int featureIndex = scrollOffset + i;
            int rowY = listY + i * ROW_HEIGHT;
            boolean isHovered = mouseX >= x + 5 && mouseX < x + width - 5
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (isHovered) {
                context.fill(x + 5, rowY, x + width - 5, rowY + ROW_HEIGHT, 0x18FFFFFF);
            }

            // Alternating row background
            if (i % 2 == 0) {
                context.fill(x + 5, rowY, x + width - 5, rowY + ROW_HEIGHT, 0x08FFFFFF);
            }

            // Index number
            context.drawText(tr, (featureIndex + 1) + ".",
                    x + 10, rowY + 3, 0xFF666666, false);

            // Feature ID (truncate if too long)
            String featureId = features.get(featureIndex);
            int maxTextW = width - 60;
            String displayId = featureId;
            if (tr.getWidth(displayId) > maxTextW) {
                while (tr.getWidth(displayId + "...") > maxTextW && displayId.length() > 10) {
                    displayId = displayId.substring(0, displayId.length() - 1);
                }
                displayId = displayId + "...";
            }
            context.drawText(tr, displayId, x + 28, rowY + 3, 0xFFCCCCCC, false);

            // Remove [x] button
            int removeX = x + width - 30;
            boolean removeHovered = mouseX >= removeX && mouseX < removeX + 16
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            int removeColor = removeHovered ? 0xFFFF4444 : 0xFF888888;
            context.drawText(tr, "x", removeX + 4, rowY + 3, removeColor, false);
        }

        // Empty state message
        if (features.isEmpty()) {
            context.drawText(tr, "No features added yet.",
                    x + 10, listY + 4, 0xFF666666, false);
            context.drawText(tr, "Enter a feature ID above and click Add.",
                    x + 10, listY + 18, 0xFF555555, false);
        }
    }
}
