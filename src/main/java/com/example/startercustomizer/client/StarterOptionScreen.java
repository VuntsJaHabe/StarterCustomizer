package com.example.startercustomizer.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.example.startercustomizer.client.StarterChoices.Option;

final class StarterOptionScreen extends Screen {
    private final Screen parent;
    private final List<Option> choices;
    private final Consumer<Option> select;
    private final List<Button> rows = new ArrayList<>();
    private List<Option> matches;
    private Button previous;
    private Button next;
    private String query = "";
    private int offset;

    StarterOptionScreen(Screen parent, Component title, List<Option> choices, Consumer<Option> select) {
        super(title);
        this.parent = parent;
        this.choices = choices;
        this.select = select;
        this.matches = choices;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(310, width - 16);
        int left = (width - panelWidth) / 2;
        EditBox search = new EditBox(font, left, 24, panelWidth, 20, Component.literal("Search"));
        search.setHint(Component.literal("Search by name"));
        search.setValue(query);
        search.setResponder(this::filter);
        addRenderableWidget(search);
        setInitialFocus(search);

        rows.clear();
        int rowCount = Math.max(1, (height - 81) / 22);
        for (int index = 0; index < rowCount; index++) {
            int row = index;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                        select.accept(matches.get(offset + row));
                        onClose();
                    }).bounds(left, 48 + index * 22, panelWidth, 20).build()));
        }

        int bottom = height - 26;
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left, bottom, 56, 20).build());
        previous = addRenderableWidget(Button.builder(Component.literal("Previous"), button -> scroll(-rows.size()))
                .bounds(left + panelWidth - 120, bottom, 68, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Next"), button -> scroll(rows.size()))
                .bounds(left + panelWidth - 48, bottom, 48, 20).build());
        updateRows();
    }

    private void filter(String input) {
        query = input;
        String search = input.strip().toLowerCase(Locale.ROOT);
        matches = choices.stream()
                .filter(choice -> choice.label().getString().toLowerCase(Locale.ROOT).contains(search)
                        || choice.id().toLowerCase(Locale.ROOT).contains(search))
                .toList();
        offset = 0;
        updateRows();
    }

    private void scroll(int amount) {
        offset = Math.clamp(offset + amount, 0, Math.max(0, matches.size() - rows.size()));
        updateRows();
    }

    private void updateRows() {
        offset = Math.min(offset, Math.max(0, matches.size() - rows.size()));
        for (int index = 0; index < rows.size(); index++) {
            Button row = rows.get(index);
            row.visible = offset + index < matches.size();
            row.active = row.visible;
            if (row.visible) {
                Option choice = matches.get(offset + index);
                row.setMessage(choice.label());
                row.setTooltip(choice.id().isEmpty() ? null : Tooltip.create(Component.literal(choice.id())));
            }
        }
        previous.active = offset > 0;
        next.active = offset + rows.size() < matches.size();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) {
            scroll(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);
        if (matches.isEmpty()) {
            graphics.drawCenteredString(font, "No matching options", width / 2, height / 2, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
