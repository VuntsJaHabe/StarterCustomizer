package com.example.startercustomizer.client;

import com.cobblemon.mod.common.client.gui.startselection.StarterSelectionScreen;
import com.example.startercustomizer.StarterCustomizer;
import com.example.startercustomizer.StarterPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@EventBusSubscriber(modid = StarterCustomizer.MOD_ID, value = Dist.CLIENT)
public final class StarterBuilderScreen extends Screen {
    private static final List<Page> PAGES = List.of(
            new Page("Pokémon", "Choose a species to see its forms and abilities.",
                    new Field("species", "Species", ""), new Field("nickname", "Nickname", "Optional"),
                    new Field("form", "Form", ""), new Field("gender", "Gender", ""),
                    new Field("shiny", "Shiny", ""), new Field("alpha", "Alpha", "")),
            new Page("Training", "Abilities depend on the selected species and form.",
                    new Field("nature", "Nature", ""), new Field("ability", "Ability", ""),
                    new Field("level", "Level", "1–100"), new Field("friendship", "Friendship", "0–255"),
                    new Field("pokeball", "Poké Ball", "")),
            new Page("Moves", "Choose up to four moves, or keep the default moveset.",
                    new Field("move1", "Move 1", ""), new Field("move2", "Move 2", ""),
                    new Field("move3", "Move 3", ""), new Field("move4", "Move 4", "")),
            new Page("Special traits", "Use Default to let Cobblemon choose.",
                    new Field("tera_type", "Tera type", ""), new Field("scale_modifier", "Size multiplier", "0.5–2"),
                    new Field("dmax_level", "Dynamax level", "0–10"), new Field("gmax_factor", "Gigantamax factor", ""),
                    new Field("min_perfect_ivs", "Minimum perfect IVs", "0–6")),
            statPage("IVs", "Individual values range from 0 to 31.", "_iv", "0–31"),
            statPage("EVs", "Effort values range from 0 to 252, with a total of 510.", "_ev", "0–252")
    );

    private final Screen parent;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Component> choiceLabels = new LinkedHashMap<>();
    private int page;
    private boolean submitting;
    private Component status = Component.empty();

    private StarterBuilderScreen(Screen parent) {
        super(Component.translatable("startercustomizer.builder.title"));
        this.parent = parent;
    }

    @SubscribeEvent
    public static void addBuilderButton(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof StarterSelectionScreen screen) {
            event.addListener(Button.builder(Component.translatable("startercustomizer.builder.title"),
                            button -> Minecraft.getInstance().setScreen(new StarterBuilderScreen(screen)))
                    .bounds((screen.width - 158) / 2, Math.max(2, (screen.height - 197) / 2 - 22), 158, 20)
                    .build());
        }
    }

    public static void receiveResult(StarterPackets.Result result) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof StarterBuilderScreen screen && screen.submitting) {
            if (result.error().isEmpty()) {
                client.setScreen(null);
            } else {
                screen.submitting = false;
                screen.status = Component.literal(result.error());
                screen.rebuildWidgets();
            }
        }
    }

    @Override
    protected void init() {
        int left = (width - formWidth()) / 2;
        int columnWidth = (formWidth() - 10) / 2;
        int top = top();
        List<Field> fields = PAGES.get(page).fields();
        for (int index = 0; index < fields.size(); index++) {
            Field field = fields.get(index);
            int x = left + index % 2 * (columnWidth + 10);
            int y = top + 49 + index / 2 * 34;
            if (StarterChoices.hasPicker(field.key())) {
                Component defaultLabel = field.key().equals("species")
                        ? Component.literal("Choose Pokémon…") : StarterChoices.DEFAULT.label();
                Component selected = choiceLabels.getOrDefault(field.key(), defaultLabel);
                Button picker = addRenderableWidget(Button.builder(selected, button -> openPicker(field))
                        .bounds(x, y, columnWidth, 18).build());
                picker.active = !submitting && (!StarterChoices.needsSpecies(field.key()) || values.containsKey("species"));
                if (!picker.active && !submitting) {
                    picker.setTooltip(Tooltip.create(Component.literal("Choose a species first.")));
                }
                continue;
            }
            EditBox input = new EditBox(font, x, y, columnWidth, 18, Component.literal(field.label()));
            input.setMaxLength(96);
            input.setValue(values.getOrDefault(field.key(), ""));
            input.setHint(Component.literal(field.hint()).withStyle(ChatFormatting.DARK_GRAY));
            input.setEditable(!submitting);
            input.setResponder(value -> values.put(field.key(), value));
            addRenderableWidget(input);
        }

        int buttonY = top + 163;
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left, buttonY, 52, 20).build()).active = !submitting;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(-1))
                .bounds(left + 56, buttonY, 24, 20).build()).active = page > 0 && !submitting;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1))
                .bounds(left + 84, buttonY, 24, 20).build()).active = page < PAGES.size() - 1 && !submitting;
        addRenderableWidget(Button.builder(Component.literal("Claim starter"), button -> submit())
                .bounds(left + formWidth() - 88, buttonY, 88, 20).build()).active =
                !submitting && values.containsKey("species");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - formWidth()) / 2;
        int columnWidth = (formWidth() - 10) / 2;
        int top = top();
        Page current = PAGES.get(page);
        graphics.drawString(font, title, left, top, 0xFFFFFF);
        graphics.drawString(font, current.name(), left, top + 15, 0xFFFFFF);
        String pageCount = (page + 1) + " / " + PAGES.size();
        graphics.drawString(font, pageCount, left + formWidth() - font.width(pageCount), top + 15, 0xC0C0C0);
        graphics.drawString(font, font.plainSubstrByWidth(current.help(), formWidth()),
                left, top + 26, 0xC0C0C0);
        for (int index = 0; index < current.fields().size(); index++) {
            int x = left + index % 2 * (columnWidth + 10);
            int y = top + 39 + index / 2 * 34;
            graphics.drawString(font, current.fields().get(index).label(), x, y, 0xFFFFFF);
        }
        for (var line : font.split(status, formWidth()).stream().limit(2).toList()) {
            graphics.drawString(font, line, left, top + 138, submitting ? 0xFFFFFF : 0xFFAAAA);
            top += 10;
        }
    }

    @Override
    public void onClose() {
        if (!submitting) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void turnPage(int offset) {
        page += offset;
        rebuildWidgets();
    }

    private void openPicker(Field field) {
        List<StarterChoices.Option> options = StarterChoices.forField(field.key(), values);
        minecraft.setScreen(new StarterOptionScreen(this, Component.literal("Choose " + field.label()), options,
                option -> selectOption(field.key(), option)));
    }

    private void selectOption(String key, StarterChoices.Option option) {
        boolean changed = !option.id().equals(values.getOrDefault(key, ""));
        if (changed && key.equals("species")) {
            clearOption("form");
        }
        if (changed && (key.equals("species") || key.equals("form"))) {
            clearOption("ability");
            clearOption("gender");
        }

        if (option.id().isEmpty()) {
            clearOption(key);
        } else {
            values.put(key, option.id());
            choiceLabels.put(key, option.label());
        }
        status = Component.empty();
    }

    private void clearOption(String key) {
        values.remove(key);
        choiceLabels.remove(key);
    }

    private void submit() {
        StringJoiner preset = new StringJoiner(" ").add(values.get("species"));
        for (var field : values.entrySet()) {
            String value = field.getValue().strip();
            if (!field.getKey().equals("species") && !field.getKey().startsWith("move") && !value.isEmpty()) {
                preset.add(field.getKey() + "=" + value);
            }
        }
        StringJoiner moves = new StringJoiner(",");
        for (int slot = 1; slot <= 4; slot++) {
            String move = values.get("move" + slot);
            if (move != null) {
                moves.add(move);
            }
        }
        if (moves.length() > 0) {
            preset.add("moves=" + moves);
        }
        if (preset.length() > StarterPackets.MAX_PRESET_LENGTH) {
            status = Component.literal("Shorten your starter settings before claiming.");
            return;
        }
        submitting = true;
        status = Component.literal("Claiming your starter…");
        rebuildWidgets();
        PacketDistributor.sendToServer(new StarterPackets.Claim(preset.toString()));
    }

    private int formWidth() {
        return Math.min(330, width - 16);
    }

    private int top() {
        return Math.max(3, (height - 185) / 2);
    }

    private static Page statPage(String name, String help, String suffix, String hint) {
        return new Page(name, help, new Field("hp" + suffix, "HP", hint),
                new Field("attack" + suffix, "Attack", hint), new Field("defence" + suffix, "Defence", hint),
                new Field("special_attack" + suffix, "Special Attack", hint),
                new Field("special_defence" + suffix, "Special Defence", hint),
                new Field("speed" + suffix, "Speed", hint));
    }

    private record Page(String name, String help, List<Field> fields) {
        Page(String name, String help, Field... fields) {
            this(name, help, List.of(fields));
        }
    }

    private record Field(String key, String label, String hint) {
    }
}
