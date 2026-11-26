# Starter Customizer

Starter Customizer lets each player define their first Pokémon before claiming it through Cobblemon. It targets Java 21, Minecraft 1.21.1, NeoForge 21.1.265 or later, and Cobblemon 1.8.0 or later on that Minecraft version. Install the addon alongside Cobblemon and its matching Kotlin for Forge dependency.

## Choose a starter

1. Open Cobblemon’s starter selection while your character is still eligible for a starter.
2. Click **Build your own starter** at the top of the screen.
3. Click **Choose Pokémon** and browse or search the installed species, including implemented species from other mods and datapacks. Use the arrows to open the Nature, Ability, Moves, and other pickers or enter numeric stats such as IVs and EVs. Optional fields can stay at their defaults. A blank level uses the level of the first configured starter.
4. Click **Claim starter**. Cobblemon grants the Pokémon and records your starter choice. **Back** returns to the usual starter list.

Install the addon on both the client and the server for multiplayer. Cobblemon controls starter eligibility and still applies its shiny-starters gamerule.

The `/startercustomizer` commands remain available. Run `/startercustomizer set eevee nickname=Nova shiny=true` to save a preset for the standard selection flow. Use `show`, `update`, `clear`, and `choose` to manage that preset or open the starter screen.

## Supported properties

Use an implemented species ID followed by space-separated `key=value` options. You can use a namespaced species ID for an installed datapack Pokémon.

| Options | Values |
| --- | --- |
| `form`, `gender`, `nickname`, `shiny`, `alpha` | Cobblemon’s form ID, a gender, a single-word nickname, or `true`/`false` for toggles |
| `level`, `friendship`, `scale_modifier` | 1–100, 0–255, and 0.5–2 respectively |
| `ability`, `nature`, `pokeball`, `tera_type` | Cobblemon registry names |
| `moves` | Up to four comma-separated move IDs |
| `dmax_level`, `gmax_factor` | 0–10 and `true`/`false` |
| `min_perfect_ivs`, `<stat>_iv`, `<stat>_ev` | 0–6, 0–31, and 0–252 respectively. EVs must total at most 510. |

Stat IDs are `hp`, `attack`, `defence`, `special_attack`, `special_defence`, and `speed`. Cobblemon applies the properties using its Pokémon creation rules.

## Build

Run `./gradlew build` with Java 21. The addon JAR is in `build/libs/`. The default Cobblemon compile target is 1.8.0. Use `./gradlew build -Pcobblemon_version_id=xsVHrAsY` to compile against 1.8.4.
