package com.example.startercustomizer;

import net.neoforged.neoforge.common.ModConfigSpec;

final class StarterServerConfig {
    static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Starter policy settings reserved for the upcoming enforcement feature.")
                .push("starter_rules");
        builder.comment("Reserve a setting to exclude legendary starters.")
                .define("banLegendaries", false);
        builder.comment("Reserve a cap on final-evolution base stat totals. Zero disables the cap.")
                .defineInRange("maxFinalEvolutionBaseStatTotal", 0, 0, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private StarterServerConfig() {
    }
}
