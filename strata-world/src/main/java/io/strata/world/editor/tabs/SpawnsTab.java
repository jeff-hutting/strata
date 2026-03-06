package io.strata.world.editor.tabs;

import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import io.strata.world.editor.BiomeEditorState.SpawnEntry;
import io.strata.world.editor.PreviewZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spawns tab — list editor for mob spawn entries.
 *
 * <p>Each {@link SpawnEntry} has an entity ID, weight, min group size, and
 * max group size. Players add entities via a text field and configure
 * spawn parameters with inline number fields.
 *
 * <p>Changes are Layer 2 — they trigger debounced chunk regeneration.
 */
public class SpawnsTab extends EditorTab {

    /** Height of each spawn row in pixels. */
    private static final int ROW_HEIGHT = 20;

    /** Maximum visible rows before scrolling. */
    private static final int MAX_VISIBLE_ROWS = 8;

    /** Vertical scroll offset (in rows). */
    private int scrollOffset = 0;

    /** Index of the currently selected spawn entry for editing, or -1. */
    private int selectedIndex = -1;

    /** Text field for adding new entity IDs. */
    private TextFieldWidget entityField;

    /** Inline edit fields for the selected spawn entry. */
    private TextFieldWidget weightField;
    private TextFieldWidget minField;
    private TextFieldWidget maxField;

    /** Filtered entity suggestions shown below the text field. */
    private List<String> suggestions = List.of();
    private int selectedSuggestion = -1;
    private int suggestionScrollOffset = 0;
    /** Hard cap on suggestions fetched, to prevent UI hangs with very large modded registries. */
    private static final int MAX_SUGGESTIONS = 500;
    /** Maximum number of suggestions visible in the dropdown at once. */
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;
    private static final int SUGGESTION_HEIGHT = 12;

    public SpawnsTab(BiomeEditorScreen screen, BiomeEditorState state) {
        super(screen, state);
    }

    @Override
    public String getTabName() {
        return "Spawns";
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);

        int fieldW = Math.min(width - 120, 180);

        // Entity ID text field — positioned after "Entity:" label drawn in render()
        entityField = new TextFieldWidget(
                MinecraftClient.getInstance().textRenderer,
                x + 60, y + 28, fieldW, 14,
                Text.literal("Entity ID"));
        entityField.setMaxLength(128);
        entityField.setPlaceholder(Text.literal("minecraft:pig"));
        entityField.setChangedListener(text -> {
            String query = text.strip().toLowerCase();
            if (query.isEmpty()) {
                suggestions = List.of();
                selectedSuggestion = -1;
            } else {
                suggestions = Registries.ENTITY_TYPE.getIds().stream()
                        .map(Identifier::toString)
                        .filter(id -> id.contains(query))
                        .sorted()
                        .limit(MAX_SUGGESTIONS)
                        .collect(Collectors.toList());
                selectedSuggestion = suggestions.isEmpty() ? -1 : 0;
                suggestionScrollOffset = 0;
            }
        });
        screen.addTabWidget(entityField);

        // Add button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Add"), b -> {
            String id = entityField.getText().strip();
            if (!id.isEmpty()) {
                SpawnEntry entry = new SpawnEntry(id, 10, 1, 4);
                state.getSpawnEntries().add(entry);
                state.setSpawnEntries(state.getSpawnEntries());
                entityField.setText("");
                selectedIndex = state.getSpawnEntries().size() - 1;
                refreshEditFields();
                notifyLayer2Changed();
            }
        }).dimensions(x + 64 + fieldW, y + 28, 40, 14).build());

        // Inline edit fields for selected entry (below the list)
        int editY = y + height - 60;
        var tr = MinecraftClient.getInstance().textRenderer;

        weightField = new TextFieldWidget(tr, x + 60, editY, 40, 14, Text.literal("Weight"));
        weightField.setMaxLength(5);
        screen.addTabWidget(weightField);

        minField = new TextFieldWidget(tr, x + 150, editY, 30, 14, Text.literal("Min"));
        minField.setMaxLength(3);
        screen.addTabWidget(minField);

        maxField = new TextFieldWidget(tr, x + 220, editY, 30, 14, Text.literal("Max"));
        maxField.setMaxLength(3);
        screen.addTabWidget(maxField);

        // Apply button for inline edits
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Apply"), b -> {
            applyEditFields();
        }).dimensions(x + 260, editY, 40, 14).build());

        // Remove Selected button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Remove"), b -> {
            if (selectedIndex >= 0 && selectedIndex < state.getSpawnEntries().size()) {
                state.getSpawnEntries().remove(selectedIndex);
                state.setSpawnEntries(state.getSpawnEntries());
                selectedIndex = Math.min(selectedIndex, state.getSpawnEntries().size() - 1);
                refreshEditFields();
                notifyLayer2Changed();
            }
        }).dimensions(x + 10, y + height - 24, 60, 14).build());

        // Clear All button
        screen.addTabWidget(ButtonWidget.builder(Text.literal("Clear All"), b -> {
            if (!state.getSpawnEntries().isEmpty()) {
                state.setSpawnEntries(new ArrayList<>());
                selectedIndex = -1;
                scrollOffset = 0;
                refreshEditFields();
                notifyLayer2Changed();
            }
        }).dimensions(x + 76, y + height - 24, 60, 14).build());

        refreshEditFields();
    }

    private void refreshEditFields() {
        if (selectedIndex >= 0 && selectedIndex < state.getSpawnEntries().size()) {
            SpawnEntry entry = state.getSpawnEntries().get(selectedIndex);
            weightField.setText(String.valueOf(entry.getWeight()));
            minField.setText(String.valueOf(entry.getMinGroupSize()));
            maxField.setText(String.valueOf(entry.getMaxGroupSize()));
        } else {
            weightField.setText("");
            minField.setText("");
            maxField.setText("");
        }
    }

    private void applyEditFields() {
        if (selectedIndex < 0 || selectedIndex >= state.getSpawnEntries().size()) return;
        SpawnEntry entry = state.getSpawnEntries().get(selectedIndex);
        try {
            entry.setWeight(Integer.parseInt(weightField.getText().strip()));
        } catch (NumberFormatException ignored) {}
        try {
            entry.setMinGroupSize(Integer.parseInt(minField.getText().strip()));
        } catch (NumberFormatException ignored) {}
        try {
            entry.setMaxGroupSize(Integer.parseInt(maxField.getText().strip()));
        } catch (NumberFormatException ignored) {}
        state.setSpawnEntries(state.getSpawnEntries());
        notifyLayer2Changed();
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

        // Hit-test suggestion dropdown (only visible rows)
        if (entityField != null && entityField.isFocused() && !suggestions.isEmpty()) {
            int sugX = entityField.getX();
            int sugY = entityField.getY() + entityField.getHeight() + 1;
            int sugW = entityField.getWidth();
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size() - suggestionScrollOffset);
            for (int vi = 0; vi < visibleCount; vi++) {
                int rowTop = sugY + vi * SUGGESTION_HEIGHT;
                if (mx >= sugX && mx < sugX + sugW && my >= rowTop && my < rowTop + SUGGESTION_HEIGHT) {
                    entityField.setText(suggestions.get(suggestionScrollOffset + vi));
                    suggestions = List.of();
                    suggestionScrollOffset = 0;
                    return true;
                }
            }
        }

        // Hit-test spawn rows to select for editing
        int listY = y + 58;
        int listBottom = y + height - 70;
        int maxRows = Math.min(MAX_VISIBLE_ROWS, (listBottom - listY) / ROW_HEIGHT);
        List<SpawnEntry> entries = state.getSpawnEntries();
        int visibleRows = Math.min(entries.size() - scrollOffset, maxRows);

        for (int i = 0; i < visibleRows; i++) {
            int rowY = listY + i * ROW_HEIGHT;
            if (mx >= x + 5 && mx < x + width - 5 && my >= rowY && my < rowY + ROW_HEIGHT) {
                selectedIndex = scrollOffset + i;
                refreshEditFields();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (entityField == null || !entityField.isFocused() || suggestions.isEmpty()) {
            return false;
        }
        int key = keyInput.key();
        if (key == GLFW.GLFW_KEY_DOWN) {
            selectedSuggestion = Math.min(selectedSuggestion + 1, suggestions.size() - 1);
            if (selectedSuggestion >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                suggestionScrollOffset = selectedSuggestion - MAX_VISIBLE_SUGGESTIONS + 1;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            selectedSuggestion = Math.max(selectedSuggestion - 1, 0);
            if (selectedSuggestion < suggestionScrollOffset) {
                suggestionScrollOffset = selectedSuggestion;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER) {
            if (selectedSuggestion >= 0 && selectedSuggestion < suggestions.size()) {
                entityField.setText(suggestions.get(selectedSuggestion));
                suggestions = List.of();
                selectedSuggestion = -1;
                suggestionScrollOffset = 0;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            selectedSuggestion = Math.min(selectedSuggestion + MAX_VISIBLE_SUGGESTIONS, suggestions.size() - 1);
            if (selectedSuggestion >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                suggestionScrollOffset = selectedSuggestion - MAX_VISIBLE_SUGGESTIONS + 1;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            selectedSuggestion = Math.max(selectedSuggestion - MAX_VISIBLE_SUGGESTIONS, 0);
            if (selectedSuggestion < suggestionScrollOffset) {
                suggestionScrollOffset = selectedSuggestion;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            suggestions = List.of();
            selectedSuggestion = -1;
            suggestionScrollOffset = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (entityField == null || !entityField.isFocused() || suggestions.isEmpty()) {
            return false;
        }
        // verticalAmount > 0 = scroll up (show earlier entries)
        int delta = verticalAmount > 0 ? -1 : 1;
        suggestionScrollOffset = Math.max(0,
                Math.min(suggestionScrollOffset + delta, suggestions.size() - MAX_VISIBLE_SUGGESTIONS));
        selectedSuggestion = Math.max(suggestionScrollOffset,
                Math.min(selectedSuggestion, suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS - 1));
        return true;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;

        List<SpawnEntry> entries = state.getSpawnEntries();
        context.drawText(tr, "Mob Spawns (" + entries.size() + ")",
                x + 10, y + 10, 0xFFFFFFFF, true);

        // "Entity:" label to the left of the entity text field
        context.drawText(tr, "Entity:", x + 10, y + 31, 0xFFCCCCCC, false);

        // Column headers — positioned below the entity field with spacing
        int listY = y + 58;
        context.drawText(tr, "Entity", x + 10, listY - 10, 0xFF888888, false);
        context.drawText(tr, "Wt", x + width - 100, listY - 12, 0xFF888888, false);
        context.drawText(tr, "Min", x + width - 70, listY - 12, 0xFF888888, false);
        context.drawText(tr, "Max", x + width - 40, listY - 12, 0xFF888888, false);

        // Spawn entry rows
        int listBottom = y + height - 70;
        int maxRows = Math.min(MAX_VISIBLE_ROWS, (listBottom - listY) / ROW_HEIGHT);
        int visibleRows = Math.min(entries.size() - scrollOffset, maxRows);

        for (int i = 0; i < visibleRows; i++) {
            int entryIndex = scrollOffset + i;
            int rowY = listY + i * ROW_HEIGHT;
            SpawnEntry entry = entries.get(entryIndex);
            boolean isSelected = (entryIndex == selectedIndex);
            boolean isHovered = mouseX >= x + 5 && mouseX < x + width - 5
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            // Row background
            if (isSelected) {
                context.fill(x + 5, rowY, x + width - 5, rowY + ROW_HEIGHT, 0x30FFFFFF);
            } else if (isHovered) {
                context.fill(x + 5, rowY, x + width - 5, rowY + ROW_HEIGHT, 0x18FFFFFF);
            }
            if (i % 2 == 0 && !isSelected) {
                context.fill(x + 5, rowY, x + width - 5, rowY + ROW_HEIGHT, 0x08FFFFFF);
            }

            // Entity ID
            String entityId = entry.getEntityId();
            // Shorten "minecraft:" prefix for display
            String displayId = entityId.startsWith("minecraft:") ? entityId.substring(10) : entityId;
            int maxTextW = width - 120;
            if (tr.getWidth(displayId) > maxTextW) {
                while (tr.getWidth(displayId + "...") > maxTextW && displayId.length() > 5) {
                    displayId = displayId.substring(0, displayId.length() - 1);
                }
                displayId = displayId + "...";
            }
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawText(tr, displayId, x + 10, rowY + 5, textColor, false);

            // Numeric values
            context.drawText(tr, String.valueOf(entry.getWeight()),
                    x + width - 100, rowY + 5, 0xFFAAAAAA, false);
            context.drawText(tr, String.valueOf(entry.getMinGroupSize()),
                    x + width - 70, rowY + 5, 0xFFAAAAAA, false);
            context.drawText(tr, String.valueOf(entry.getMaxGroupSize()),
                    x + width - 40, rowY + 5, 0xFFAAAAAA, false);
        }

        // Empty state
        if (entries.isEmpty()) {
            context.drawText(tr, "No spawn entries yet.",
                    x + 10, listY + 4, 0xFF999999, false);
            context.drawText(tr, "Enter an entity ID above and click Add.",
                    x + 10, listY + 18, 0xFF888888, false);
        }

        // Visual separator between list and edit section
        int editY = y + height - 60;
        context.fill(x + 5, editY - 8, x + width - 5, editY - 7, 0x40FFFFFF);

        // Edit section labels
        context.drawText(tr, "Weight:", x + 10, editY + 3, 0xFFAAAAAA, false);
        context.drawText(tr, "Min:", x + 120, editY + 3, 0xFFAAAAAA, false);
        context.drawText(tr, "Max:", x + 192, editY + 3, 0xFFAAAAAA, false);

        // Selected entry indicator
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            String selLabel = "Editing: " + entries.get(selectedIndex).getEntityId();
            context.drawText(tr, selLabel, x + 10, editY - 20, 0xFF4A90D9, false);
        } else {
            context.drawText(tr, "Click a row to edit", x + 10, editY - 20, 0xFF888888, false);
        }

        // Suggestion dropdown below the entity text field
        if (entityField != null && entityField.isFocused() && !suggestions.isEmpty()) {
            int sugX = entityField.getX();
            int sugY = entityField.getY() + entityField.getHeight() + 1;
            int sugW = entityField.getWidth();
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size() - suggestionScrollOffset);
            int totalH = visibleCount * SUGGESTION_HEIGHT;
            context.fill(sugX, sugY, sugX + sugW, sugY + totalH, 0xF0222244);
            context.fill(sugX, sugY, sugX + sugW, sugY + 1, 0xFF4A90D9); // top border
            if (suggestionScrollOffset + visibleCount < suggestions.size()) {
                context.fill(sugX, sugY + totalH - 1, sugX + sugW, sugY + totalH, 0xFF4A90D9);
            }
            for (int vi = 0; vi < visibleCount; vi++) {
                int si = suggestionScrollOffset + vi;
                int rowTop = sugY + vi * SUGGESTION_HEIGHT;
                boolean isSelected = (si == selectedSuggestion);
                boolean hovered = mouseX >= sugX && mouseX < sugX + sugW
                        && mouseY >= rowTop && mouseY < rowTop + SUGGESTION_HEIGHT;
                if (isSelected) {
                    context.fill(sugX, rowTop, sugX + sugW, rowTop + SUGGESTION_HEIGHT, 0x60FFFFFF);
                } else if (hovered) {
                    context.fill(sugX, rowTop, sugX + sugW, rowTop + SUGGESTION_HEIGHT, 0x40FFFFFF);
                }
                int textColor = isSelected ? 0xFFFFFFFF : 0xFFCCCCCC;
                context.drawText(tr, suggestions.get(si), sugX + 3, rowTop + 2, textColor, false);
            }
        }
    }
}
