package com.example.startercustomizer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.starter.StarterChosenEvent;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import com.example.startercustomizer.client.StarterBuilderScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod(StarterCustomizer.MOD_ID)
public class StarterCustomizer {
    public static final String MOD_ID = "startercustomizer";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String PRESET_KEY = MOD_ID + ":preset";
    private static final Set<String> PROPERTY_KEYS = Set.of(
            "form", "nickname", "shiny", "gender", "level", "friendship", "pokeball",
            "nature", "ability", "moves", "tera_type", "dmax_level", "gmax_factor",
            "alpha", "scale_modifier", "min_perfect_ivs", "hp_iv", "attack_iv", "defence_iv", "special_attack_iv",
            "special_defence_iv", "speed_iv", "hp_ev", "attack_ev", "defence_ev",
            "special_attack_ev", "special_defence_ev", "speed_ev"
    );

    private final Map<ServerPlayer, PokemonProperties> activeClaims = new HashMap<>();

    public StarterCustomizer(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        CobblemonEvents.STARTER_CHOSEN.subscribe(Priority.LOWEST, (Consumer<StarterChosenEvent>) this::customize);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(StarterPackets.Claim.TYPE, StarterPackets.Claim.CODEC, (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    context.reply(new StarterPackets.Result(claimStarter(player, payload.preset())));
                })
                .playToClient(StarterPackets.Result.TYPE, StarterPackets.Result.CODEC,
                        (payload, context) -> StarterBuilderScreen.receiveResult(payload));
    }

    private String claimStarter(ServerPlayer player, String preset) {
        var cobblemon = Cobblemon.INSTANCE;
        var data = cobblemon.getPlayerDataManager().getGenericData(player);
        if (data.getStarterSelected() || data.getStarterLocked()) {
            return "Cobblemon has closed starter selection for you.";
        }

        try {
            PokemonProperties properties = parsePreset(preset);
            var handler = cobblemon.getStarterHandler();
            var category = handler.getStarterList(player).stream()
                    .filter(entry -> !entry.getPokemon().isEmpty())
                    .findFirst();
            if (category.isEmpty()) {
                return "This world has no available Cobblemon starter choices.";
            }

            // Starter selection and STARTER_CHOSEN run synchronously on the server thread.
            activeClaims.put(player, properties);
            try {
                handler.chooseStarter(player, category.get().getName(), 0);
            } finally {
                activeClaims.remove(player);
            }
            return data.getStarterSelected() ? "" : "Cobblemon did not grant this starter. Check your settings and try again.";
        } catch (IllegalArgumentException exception) {
            return exception.getMessage() != null ? exception.getMessage() : "Check your starter settings and try again.";
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not claim a custom starter for {}", player.getGameProfile().getName(), exception);
            return "The server could not grant this starter.";
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal(MOD_ID)
                .then(literal("set").then(argument("properties", StringArgumentType.greedyString())
                        .executes(context -> savePreset(context.getSource(),
                                StringArgumentType.getString(context, "properties"), false))))
                .then(literal("update").then(argument("properties", StringArgumentType.greedyString())
                        .executes(context -> savePreset(context.getSource(),
                                StringArgumentType.getString(context, "properties"), true))))
                .then(literal("show").executes(context -> {
                    var source = context.getSource();
                    var data = presetData(source.getPlayerOrException());
                    String preset = data.getString(PRESET_KEY);
                    source.sendSuccess(() -> Component.literal(preset.isEmpty()
                            ? "No custom starter is saved." : "Saved starter: " + preset), false);
                    return 1;
                }))
                .then(literal("clear").executes(context -> {
                    var source = context.getSource();
                    presetData(source.getPlayerOrException()).remove(PRESET_KEY);
                    source.sendSuccess(() -> Component.literal("Custom starter cleared."), false);
                    return 1;
                }))
                .then(literal("choose").executes(context -> {
                    Cobblemon.INSTANCE.getStarterHandler().requestStarterChoice(context.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private int savePreset(CommandSourceStack source, String preset, boolean update) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var starterData = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player);
        if (starterData.getStarterSelected() || starterData.getStarterLocked()) {
            source.sendFailure(Component.literal("Cobblemon has closed starter selection for you."));
            return 0;
        }

        CompoundTag data = presetData(player);
        preset = preset.strip();
        try {
            if (update) {
                preset = mergePreset(data.getString(PRESET_KEY), preset);
            }
            parsePreset(preset);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }

        data.putString(PRESET_KEY, preset);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
        source.sendSuccess(() -> Component.literal("Custom starter saved. Use /startercustomizer choose to open the starter menu."), false);
        return 1;
    }

    private static CompoundTag presetData(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static String mergePreset(String saved, String changes) {
        if (saved.isEmpty()) {
            throw new IllegalArgumentException("Use /startercustomizer set to save a species first.");
        }

        String[] previous = saved.split("\\s+");
        var properties = new LinkedHashMap<String, String>();
        for (int index = 1; index < previous.length; index++) {
            String token = previous[index];
            properties.put(token.substring(0, token.indexOf('=')), token);
        }
        Set<String> changedKeys = new HashSet<>();
        for (String token : changes.strip().split("\\s+")) {
            int separator = token.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Use key=value for each starter property.");
            }
            String key = token.substring(0, separator);
            if (!changedKeys.add(key)) {
                throw new IllegalArgumentException("Repeated starter property: " + key);
            }
            properties.put(key, token);
        }
        return previous[0] + " " + String.join(" ", properties.values());
    }

    private PokemonProperties parsePreset(String preset) {
        if (preset.length() > StarterPackets.MAX_PRESET_LENGTH) {
            throw new IllegalArgumentException("Starter properties must fit within " + StarterPackets.MAX_PRESET_LENGTH + " characters.");
        }

        String[] tokens = preset.strip().split("\\s+");
        ResourceLocation species = ResourceLocation.tryParse(tokens[0].contains(":") ? tokens[0] : "cobblemon:" + tokens[0]);
        var registeredSpecies = species == null ? null : PokemonSpecies.getByIdentifier(species);
        if (registeredSpecies == null || !registeredSpecies.getImplemented()) {
            throw new IllegalArgumentException("Start with an implemented Pokémon species, such as eevee.");
        }

        Set<String> keys = new HashSet<>();
        int totalEvs = 0;
        String nickname = null;
        for (int index = 1; index < tokens.length; index++) {
            String token = tokens[index];
            int separator = token.indexOf('=');
            if (separator <= 0 || !token.substring(separator + 1).matches("[a-zA-Z0-9_.,:+-]+")) {
                throw new IllegalArgumentException("Use key=value for each starter property: " + token.substring(0, Math.min(token.length(), 32)));
            }
            String key = token.substring(0, separator);
            if (!PROPERTY_KEYS.contains(key) || !keys.add(key)) {
                throw new IllegalArgumentException("Unsupported or repeated starter property: " + key);
            }
            String value = token.substring(separator + 1);
            totalEvs += validateValue(key, value);
            if (key.equals("nickname")) {
                if (value.length() > 32) {
                    throw new IllegalArgumentException("Nicknames must fit within 32 characters.");
                }
                nickname = value;
            }
        }
        if (totalEvs > 510) {
            throw new IllegalArgumentException("Starter EVs must add up to 510 or less.");
        }

        PokemonProperties properties = PokemonProperties.Companion.parse(String.join(" ", tokens));
        for (String key : keys) {
            boolean parsed = switch (key) {
                case "gender" -> properties.getGender() != null;
                case "nature" -> properties.getNature() != null;
                case "ability" -> properties.getAbility() != null;
                case "pokeball" -> properties.getPokeball() != null;
                case "tera_type" -> properties.getTeraType() != null;
                default -> true;
            };
            if (!parsed) {
                throw new IllegalArgumentException("Invalid value for starter property: " + key);
            }
        }
        if (keys.contains("form")) {
            String requestedForm = properties.getForm();
            String formId = registeredSpecies.getForms().stream()
                    .filter(form -> form.formOnlyShowdownId().equalsIgnoreCase(requestedForm))
                    .findFirst()
                    .map(FormData::formOnlyShowdownId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown form for " + species + ": " + requestedForm));
            properties.setForm(formId);
        }
        if (nickname != null) {
            properties.setNickname(Component.literal(nickname));
        }
        properties.setSpecies(species.toString());
        return properties;
    }

    private static int validateValue(String key, String value) {
        if (key.endsWith("_iv")) {
            requireInteger(key, value, 0, 31);
        } else if (key.endsWith("_ev")) {
            return requireInteger(key, value, 0, 252);
        }

        switch (key) {
            case "level" -> requireInteger(key, value, 1, 100);
            case "friendship" -> requireInteger(key, value, 0, 255);
            case "dmax_level" -> requireInteger(key, value, 0, 10);
            case "min_perfect_ivs" -> requireInteger(key, value, 0, 6);
            case "shiny", "alpha", "gmax_factor" -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw new IllegalArgumentException(key + " must be true or false.");
                }
            }
            case "scale_modifier" -> {
                float scale = Float.parseFloat(value);
                if (!Float.isFinite(scale) || scale < 0.5f || scale > 2.0f) {
                    throw new IllegalArgumentException("scale_modifier must be between 0.5 and 2.");
                }
            }
            case "moves" -> {
                String[] moves = value.split(",", -1);
                Set<String> names = new HashSet<>();
                for (String move : moves) {
                    var template = Moves.getByName(move);
                    if (template == null || !names.add(template.getName())) {
                        throw new IllegalArgumentException("Unknown or repeated move: " + move);
                    }
                }
                if (moves.length > 4) {
                    throw new IllegalArgumentException("Choose up to four starter moves.");
                }
            }
            default -> {
            }
        }
        return 0;
    }

    private static int requireInteger(String key, String value, int min, int max) {
        try {
            int number = Integer.parseInt(value);
            if (number >= min && number <= max) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // Report the valid range for both non-integer and out-of-range values.
        }
        throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max + ".");
    }

    private void customize(StarterChosenEvent event) {
        ServerPlayer player = event.getPlayer();
        PokemonProperties activeClaim = activeClaims.get(player);
        String preset = presetData(player).getString(PRESET_KEY);
        if (event.isCanceled() || (activeClaim == null && preset.isEmpty())) {
            return;
        }

        try {
            PokemonProperties properties = activeClaim != null ? activeClaim : parsePreset(preset);
            Pokemon original = event.getPokemon();
            if (properties.getLevel() == null) {
                properties.setLevel(original.getLevel());
            }

            Pokemon custom = properties.create(player);
            // Keep the identity used by Cobblemon and other starter event listeners.
            custom.setUuid(original.getUuid());
            if (player.level().getGameRules().getBoolean(CobblemonGameRules.SHINY_STARTERS)) {
                custom.setShiny(true);
            }
            event.setPokemon(custom);
        } catch (RuntimeException exception) {
            event.cancel();
            if (activeClaim == null) {
                player.sendSystemMessage(Component.literal("Your custom starter could not be created. Update it or use /startercustomizer clear."));
            }
            LOGGER.warn("Could not create a custom starter for {}", player.getGameProfile().getName(), exception);
        }
    }
}
