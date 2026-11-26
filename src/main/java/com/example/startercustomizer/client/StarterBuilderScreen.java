package com.example.startercustomizer.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.startselection.StarterSelectionScreen;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.example.startercustomizer.StarterCustomizer;
import com.example.startercustomizer.StarterPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@EventBusSubscriber(modid = StarterCustomizer.MOD_ID, value = Dist.CLIENT)
public final class StarterBuilderScreen extends StarterFrame {
    private static final List<Page> PAGES = List.of(
            new Page("Pokémon",
                    new Field("species", "Species", ""), new Field("nickname", "Nickname", "Optional"),
                    new Field("form", "Form", ""), new Field("gender", "Gender", ""),
                    new Field("shiny", "Shiny", ""), new Field("alpha", "Alpha", "")),
            new Page("Training",
                    new Field("nature", "Nature", ""), new Field("ability", "Ability", ""),
                    new Field("level", "Level", "1–100"), new Field("friendship", "Friendship", "0–255"),
                    new Field("pokeball", "Poké Ball", "")),
            new Page("Moves",
                    new Field("move1", "Move 1", ""), new Field("move2", "Move 2", ""),
                    new Field("move3", "Move 3", ""), new Field("move4", "Move 4", "")),
            new Page("Special traits",
                    new Field("tera_type", "Tera type", ""), new Field("scale_modifier", "Size multiplier", "0.5–2"),
                    new Field("dmax_level", "Dynamax level", "0–10"), new Field("gmax_factor", "Gigantamax factor", ""),
                    new Field("min_perfect_ivs", "Minimum perfect IVs", "0–6")),
            statPage("IVs", "_iv", "0–31"),
            statPage("EVs", "_ev", "0–252")
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
            int left = (screen.width - WIDTH) / 2;
            int top = (screen.height - HEIGHT) / 2;
            var build = button(Component.translatable("startercustomizer.builder.short_title"),
                    left + RIGHT_PANE, top + 180, 72,
                    widget -> Minecraft.getInstance().setScreen(new StarterBuilderScreen(screen)));
            build.setTooltip(Tooltip.create(Component.translatable("startercustomizer.builder.title")));
            event.addListener(build);
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
        int x = left();
        int y = top();
        addPreview(preview());
        List<Field> fields = PAGES.get(page).fields();
        for (int index = 0; index < fields.size(); index++) {
            Field field = fields.get(index);
            int inputX = x + RIGHT_PANE;
            int inputY = y + 37 + index * 24;
            if (StarterChoices.hasPicker(field.key())) {
                Component defaultLabel = field.key().equals("species")
                        ? Component.literal("Choose Pokémon…") : StarterChoices.DEFAULT.label();
                Component selected = choiceLabels.getOrDefault(field.key(), defaultLabel);
                var picker = addRenderableWidget(button(selected, inputX, inputY, RIGHT_WIDTH,
                        widget -> openPicker(field)));
                picker.active = !submitting && (!StarterChoices.needsSpecies(field.key()) || values.containsKey("species"));
                if (!picker.active && !submitting) {
                    picker.setTooltip(Tooltip.create(Component.literal("Choose a species first.")));
                }
                continue;
            }
            EditBox input = new EditBox(font, inputX, inputY, RIGHT_WIDTH, 14, Component.literal(field.label()));
            input.setMaxLength(96);
            input.setValue(values.getOrDefault(field.key(), ""));
            input.setHint(Component.literal(field.hint()).withStyle(ChatFormatting.GRAY));
            input.setEditable(!submitting);
            input.setResponder(value -> values.put(field.key(), value));
            addRenderableWidget(input);
        }

        addRenderableWidget(button(Component.literal("<"), x + 9, y + 151, 16,
                widget -> turnPage(-1))).active = page > 0 && !submitting;
        addRenderableWidget(button(Component.literal(">"), x + 105, y + 151, 16,
                widget -> turnPage(1))).active = page < PAGES.size() - 1 && !submitting;
        addRenderableWidget(button(Component.literal("Claim starter"), x + 13, y + 180, 106,
                widget -> submit())).active = !submitting && values.containsKey("species");
        addRenderableWidget(button(Component.literal("Back"), x + RIGHT_PANE, y + 180, 44,
                widget -> onClose())).active = !submitting;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        Page current = PAGES.get(page);
        drawHeading(graphics, Component.translatable("startercustomizer.builder.short_title"));
        drawPreviewName(graphics, speciesLabel(), 124);
        drawPreviewName(graphics, Component.literal(current.name() + "  " + (page + 1) + "/" + PAGES.size()), 154);

        for (int index = 0; index < current.fields().size(); index++) {
            graphics.drawString(font, current.fields().get(index).label(), x + RIGHT_PANE, y + 27 + index * 24, 0xFFFFFF);
        }

        if (!status.getString().isEmpty()) {
            int lineY = y + 76;
            graphics.fill(x + 8, lineY - 3, x + 121, lineY + 34, 0xEE282828);
            for (var line : font.split(status, 106).stream().limit(3).toList()) {
                graphics.drawString(font, line, x + 11, lineY, submitting ? 0xFFFFFF : 0xFFAAAA);
                lineY += 10;
            }
        }
    }

    @Override
    public void onClose() {
        if (!submitting) {
            minecraft.setScreen(parent);
        }
    }

    private Component speciesLabel() {
        return choiceLabels.getOrDefault("species", Component.literal("Choose a Pokémon"));
    }

    private RenderablePokemon preview() {
        String id = values.get("species");
        if (id == null) {
            return null;
        }
        var species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(id));
        if (species == null) {
            return null;
        }

        var aspects = new HashSet<String>();
        String form = values.get("form");
        if (form != null) {
            species.getForms().stream()
                    .filter(candidate -> candidate.formOnlyShowdownId().equals(form))
                    .findFirst().ifPresent(candidate -> aspects.addAll(candidate.getAspects()));
        }
        if ("true".equals(values.get("shiny"))) {
            aspects.add("shiny");
        }
        String gender = values.get("gender");
        if (gender != null) {
            aspects.add(gender);
        }
        return new RenderablePokemon(species, aspects, ItemStack.EMPTY);
    }

    private void turnPage(int offset) {
        page += offset;
        rebuildWidgets();
    }

    private void openPicker(Field field) {
        List<StarterChoices.Option> options = StarterChoices.forField(field.key(), values);
        minecraft.setScreen(new StarterOptionScreen(this, Component.literal("Choose " + field.label()), options,
                preview(), speciesLabel(), field.key().equals("species"), option -> selectOption(field.key(), option)));
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

    private static Page statPage(String name, String suffix, String hint) {
        return new Page(name, new Field("hp" + suffix, "HP", hint),
                new Field("attack" + suffix, "Attack", hint), new Field("defence" + suffix, "Defence", hint),
                new Field("special_attack" + suffix, "Special Attack", hint),
                new Field("special_defence" + suffix, "Special Defence", hint),
                new Field("speed" + suffix, "Speed", hint));
    }

    private record Page(String name, List<Field> fields) {
        Page(String name, Field... fields) {
            this(name, List.of(fields));
        }
    }

    private record Field(String key, String label, String hint) {
    }
}
