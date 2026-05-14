package com.ddd.guardvillagerstaczsupport;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue AMMO_SEARCH_RADIUS = BUILDER
            .comment("Block radius TACZ guards search for vanilla containers containing compatible ammo.")
            .defineInRange("ammo_search_radius", 64, 1, 1024);

    public static final ModConfigSpec.IntValue FOOD_SEARCH_RADIUS = BUILDER
            .comment("Block radius TACZ guards search for vanilla containers containing food.")
            .defineInRange("food_search_radius", 64, 1, 1024);

    public static final ModConfigSpec.IntValue AMMO_SEARCH_COOLDOWN_TICKS = BUILDER
            .comment("Ticks between failed ammo container searches.")
            .defineInRange("ammo_search_cooldown_ticks", 100, 20, 6000);

    public static final ModConfigSpec.IntValue FOOD_SEARCH_COOLDOWN_TICKS = BUILDER
            .comment("Ticks between failed food container searches.")
            .defineInRange("food_search_cooldown_ticks", 100, 20, 6000);

    public static final ModConfigSpec.IntValue CONTAINER_REACH_TIMEOUT_TICKS = BUILDER
            .comment("Ticks a TACZ guard will try to reach a selected food/ammo container before temporarily skipping it.")
            .defineInRange("container_reach_timeout_ticks", 400, 20, 6000);

    public static final ModConfigSpec.DoubleValue LOW_FOOD_HEALTH_THRESHOLD = BUILDER
            .comment("Health threshold below which TACZ guards search containers for food.")
            .defineInRange("low_food_health_threshold", 18.0D, 1.0D, 19.0D);

    public static final ModConfigSpec.DoubleValue FRIENDLY_FIRE_WIDTH = BUILDER
            .comment("Width, in blocks, used for passive-entity line-of-fire checks.")
            .defineInRange("friendly_fire_width", 2.0D, 0.0D, 5.0D);

    public static final ModConfigSpec.DoubleValue DEFAULT_ATTACK_RANGE = BUILDER
            .comment("Default TACZ guard attack range in blocks.")
            .defineInRange("default_attack_range", 32.0D, 1.0D, 128.0D);

    static final ModConfigSpec SPEC = BUILDER.build();
}
