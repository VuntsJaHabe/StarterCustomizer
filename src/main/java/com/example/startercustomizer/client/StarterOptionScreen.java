package com.example.startercustomizer.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import com.example.startercustomizer.client.StarterChoices.Option;
import com.example.startercustomizer.client.StarterChoices.SpeciesSort;

final class StarterOptionScreen extends StarterFrame {
    private final StarterBuilderScreen parent;
    private final List<Option> choices;
    private final Consumer<Option> select;
    private final RenderablePokemon selectedPokemon;
    private final Component selectedLabel;
    private final boolean speciesPicker;
    private SpeciesSort sort;
    private ModelWidget hoverModel;
    private Option hoveredOption;
    private final List<Button> rows = new ArrayList<>();
    private List<Option> matches;
    private Button previous;
    private Button next;
    private String query = "";
    private int offset;

    StarterOptionScreen(StarterBuilderScreen parent, Component title, List<Option> choices, RenderablePokemon selectedPokemon,
                        Component selectedLabel, SpeciesSort sort, Consumer<Option> select) {
        super(title);
        this.parent = parent;
        this.choices = choices;
        this.select = select;
        this.selectedPokemon = selectedPokemon;
        this.selectedLabel = selectedLabel;
        this.sort = sort;
        this.speciesPicker = sort != null;
        this.matches = choices;
    }

    @Override
    protected void init() {
        int left = left();
        int top = top();
        hoveredOption = null;
        hoverModel = null;
        if (speciesPicker) {
            hoverModel = createPreview(selectedPokemon);
        } else {
            addPreview(selectedPokemon);
        }
        EditBox search = new EditBox(font, left + RIGHT_PANE, top + 27, RIGHT_WIDTH, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search by name"));
        search.setValue(query);
        search.setResponder(this::filter);
        addRenderableWidget(search);
        setInitialFocus(search);

        rows.clear();
        for (int index = 0; index < 7; index++) {
            int row = index;
            rows.add(addRenderableWidget(button(Component.empty(), left + RIGHT_PANE, top + 45 + index * 18,
                    RIGHT_WIDTH, widget -> {
                        select.accept(matches.get(offset + row));
                        onClose();
                    })));
        }

        if (speciesPicker) {
            Button sortButton = addRenderableWidget(button(sort.label, left + 13, top + 143, 106, widget -> {
                sort = sort.next();
                widget.setMessage(sort.label);
                filter(query);
            }));
            sortButton.setTooltip(Tooltip.create(Component.translatable("startercustomizer.sort.tooltip")));
        }
        addRenderableWidget(button(Component.literal("Back"), left + 13, top + 180, 106, widget -> onClose()));
        previous = addRenderableWidget(button(Component.literal("<"), left + RIGHT_PANE, top + 180, 22,
                widget -> scroll(-rows.size())));
        next = addRenderableWidget(button(Component.literal(">"), left + RIGHT_PANE + RIGHT_WIDTH - 22, top + 180, 22,
                widget -> scroll(rows.size())));
        filter(query);
    }

    private void filter(String input) {
        query = input;
        String search = input.strip().toLowerCase(Locale.ROOT);
        var filtered = choices.stream()
                .filter(choice -> choice.label().getString().toLowerCase(Locale.ROOT).contains(search)
                        || choice.id().toLowerCase(Locale.ROOT).contains(search));
        matches = (speciesPicker ? filtered.sorted(sort.order) : filtered).toList();
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
                Component tooltip = Component.literal(choice.id());
                if (speciesPicker) {
                    String number = choice.pokedexNumber() > 0 ? "#" + choice.pokedexNumber() : "—";
                    tooltip = Component.translatable("startercustomizer.sort.species_details",
                            number, choice.baseStatTotal()).append("\n").append(tooltip);
                }
                row.setTooltip(choice.id().isEmpty() ? null : Tooltip.create(tooltip));
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
        Component name = selectedLabel;
        if (speciesPicker) {
            Option hovered = null;
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).visible && rows.get(index).isMouseOver(mouseX, mouseY)) {
                    hovered = matches.get(offset + index);
                    break;
                }
            }
            updateHoverModel(hovered);
            if (hovered != null) {
                name = hovered.label();
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverModel != null) {
            hoverModel.render(graphics, mouseX, mouseY, partialTick);
        }
        drawHeading(graphics, title);
        drawPreviewName(graphics, name, 124);
        drawPreviewName(graphics, Component.literal(
                matches.isEmpty() ? "No matching options" : matches.size() + " options"), speciesPicker ? 160 : 154);
    }

    private void updateHoverModel(Option hovered) {
        if (hovered == hoveredOption) {
            return;
        }
        hoveredOption = hovered;
        RenderablePokemon pokemon = selectedPokemon;
        if (hovered != null) {
            var species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(hovered.id()));
            if (species != null) {
                pokemon = new RenderablePokemon(species, Set.of(), ItemStack.EMPTY);
            }
        }
        hoverModel = createPreview(pokemon);
    }

    @Override
    public void onClose() {
        if (speciesPicker) {
            parent.rememberSpeciesSort(sort);
        }
        minecraft.setScreen(parent);
    }

}
