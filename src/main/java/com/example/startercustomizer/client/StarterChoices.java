package com.example.startercustomizer.client;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class StarterChoices {
    private static final Comparator<Option> BY_NAME = Comparator
            .comparing((Option option) -> option.label().getString().toLowerCase(Locale.ROOT))
            .thenComparing(Option::id);
    static final Option DEFAULT = new Option("", Component.literal("Default"));

    private StarterChoices() {
    }

    static boolean hasPicker(String key) {
        return switch (key) {
            case "species", "form", "gender", "ability", "nature", "pokeball", "tera_type",
                 "shiny", "alpha", "gmax_factor", "move1", "move2", "move3", "move4" -> true;
            default -> false;
        };
    }

    static boolean needsSpecies(String key) {
        return key.equals("form") || key.equals("gender") || key.equals("ability");
    }

    static List<Option> forField(String key, Map<String, String> values) {
        if (key.equals("shiny") || key.equals("alpha") || key.equals("gmax_factor")) {
            return List.of(DEFAULT, new Option("true", Component.literal("Yes")),
                    new Option("false", Component.literal("No")));
        }

        var speciesId = ResourceLocation.tryParse(values.getOrDefault("species", ""));
        var species = speciesId == null ? null : PokemonSpecies.getByIdentifier(speciesId);
        FormData form = species == null ? null : species.getForms().stream()
                .filter(candidate -> candidate.formOnlyShowdownId().equals(values.get("form")))
                .findFirst()
                .orElseGet(species::getStandardForm);

        var choices = switch (key) {
            case "species" -> PokemonSpecies.getImplemented().stream().map(candidate -> {
                var id = candidate.getResourceIdentifier();
                Component label = candidate.getTranslatedName();
                if (!id.getNamespace().equals("cobblemon")) {
                    label = label.copy().append(" (" + id.getNamespace() + ")");
                }
                var baseStats = candidate.getStandardForm().getBaseStats();
                int total = Stats.Companion.getPERMANENT().stream()
                        .mapToInt(stat -> baseStats.getOrDefault(stat, 0))
                        .sum();
                return new Option(id.toString(), label, candidate.getNationalPokedexNumber(), total);
            });
            case "form" -> species == null ? Stream.<Option>empty() : species.getForms().stream()
                    .filter(candidate -> !candidate.formOnlyShowdownId().isEmpty())
                    .map(candidate -> new Option(candidate.formOnlyShowdownId(), Component.literal(candidate.getName())));
            case "gender" -> form == null ? Stream.<Option>empty() : form.getPossibleGenders().stream()
                    .map(gender -> new Option(gender.getSerializedName(), readable(gender.getSerializedName())));
            case "ability" -> form == null ? Stream.<Option>empty()
                    : StreamSupport.stream(form.getAbilities().spliterator(), false)
                            .map(ability -> new Option(ability.getTemplate().getName(),
                                    Component.translatable(ability.getTemplate().getDisplayName())));
            case "nature" -> Natures.all().stream()
                    .map(nature -> new Option(nature.getName().toString(), Component.translatable(nature.getDisplayName())));
            case "pokeball" -> PokeBalls.all().stream()
                    .map(ball -> new Option(ball.getName().toString(),
                            Component.translatableWithFallback("item." + ball.getName().toLanguageKey(),
                                    readable(ball.getName().getPath()).getString())));
            case "tera_type" -> StreamSupport.stream(TeraTypes.INSTANCE.spliterator(), false)
                    .map(type -> new Option(type.getId().toString(), type.getDisplayName()));
            case "move1", "move2", "move3", "move4" -> Moves.all().stream()
                    .filter(move -> move.getName().equals(values.get(key)) || values.entrySet().stream()
                            .noneMatch(entry -> entry.getKey().startsWith("move") && move.getName().equals(entry.getValue())))
                    .map(move -> new Option(move.getName(), move.getDisplayName()));
            default -> throw new IllegalArgumentException("No picker for " + key);
        };

        var sorted = choices.distinct()
                .sorted(BY_NAME)
                .toList();
        if (key.equals("species")) {
            return sorted;
        }
        return Stream.concat(Stream.of(DEFAULT), sorted.stream()).toList();
    }

    private static Component readable(String id) {
        String text = id.replace('_', ' ');
        return Component.literal(text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1));
    }

    enum SpeciesSort {
        NAME("name", BY_NAME),
        POKEDEX("pokedex", Comparator.comparingInt(
                (Option option) -> option.pokedexNumber() > 0 ? option.pokedexNumber() : Integer.MAX_VALUE)
                .thenComparing(BY_NAME)),
        BASE_STATS("base_stats", Comparator.comparingInt(Option::baseStatTotal).reversed().thenComparing(BY_NAME));

        final Component label;
        final Comparator<Option> order;

        SpeciesSort(String key, Comparator<Option> order) {
            this.label = Component.translatable("startercustomizer.sort." + key);
            this.order = order;
        }

        SpeciesSort next() {
            var modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    record Option(String id, Component label, int pokedexNumber, int baseStatTotal) {
        Option(String id, Component label) {
            this(id, label, 0, 0);
        }
    }
}
