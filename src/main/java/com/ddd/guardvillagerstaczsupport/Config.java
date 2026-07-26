package com.ddd.guardvillagerstaczsupport;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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

    public static final ModConfigSpec.IntValue HOSTILE_TACZ_MAGAZINE_SIZE = BUILDER
            .comment("Number of successful TACZ shots hostile mobs fire before simulating a reload.")
            .defineInRange("hostile_tacz_magazine_size", 8, 1, 256);

    public static final ModConfigSpec.IntValue HOSTILE_TACZ_RELOAD_TICKS = BUILDER
            .comment("Ticks hostile TACZ mobs wait when simulating a reload.")
            .defineInRange("hostile_tacz_reload_ticks", 60, 1, 6000);

    public static final ModConfigSpec.DoubleValue ZOMBIE_TACZ_WEAPON_SPAWN_CHANCE = BUILDER
            .comment("Chance for a newly spawned vanilla zombie to receive a TACZ weapon from zombie_tacz_weapon_ids.")
            .defineInRange("zombie_tacz_weapon_spawn_chance", 0.05D, 0.0D, 1.0D);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ZOMBIE_TACZ_WEAPON_IDS = BUILDER
            .comment("TACZ gun IDs vanilla zombies may spawn with. Invalid IDs are ignored.")
            .defineList(List.of("zombie_tacz_weapon_ids"),
                    () -> List.of("tacz:ak47", "tacz:m4a1", "tacz:m16a1"),
                    () -> "tacz:ak47",
                    entry -> entry instanceof String,
                    ModConfigSpec.Range.of(0, 256));

    public static final ModConfigSpec.DoubleValue ZOMBIE_TACZ_WEAPON_DROP_CHANCE = BUILDER
            .comment("Chance for a TACZ-armed zombie to drop its TACZ weapon on death.")
            .defineInRange("zombie_tacz_weapon_drop_chance", 0.25D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue ZOMBIE_TACZ_AMMO_DROP_MIN = BUILDER
            .comment("Minimum compatible TACZ ammo items dropped when a TACZ-armed zombie drops ammo.")
            .defineInRange("zombie_tacz_ammo_drop_min", 3, 0, 256);

    public static final ModConfigSpec.IntValue ZOMBIE_TACZ_AMMO_DROP_MAX = BUILDER
            .comment("Maximum compatible TACZ ammo items dropped when a TACZ-armed zombie drops ammo.")
            .defineInRange("zombie_tacz_ammo_drop_max", 12, 0, 256);

    public static final ModConfigSpec.DoubleValue ZOMBIE_TACZ_INACCURACY_DEGREES = BUILDER
            .comment("Maximum random yaw/pitch offset in degrees applied when TACZ-armed zombies fire.")
            .defineInRange("zombie_tacz_inaccuracy_degrees", 8.0D, 0.0D, 45.0D);

    public static final ModConfigSpec.DoubleValue PILLAGER_TACZ_WEAPON_SPAWN_CHANCE = BUILDER
            .comment("Chance for a newly spawned vanilla pillager to receive a TACZ weapon from pillager_tacz_weapon_ids.")
            .defineInRange("pillager_tacz_weapon_spawn_chance", 0.05D, 0.0D, 1.0D);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PILLAGER_TACZ_WEAPON_IDS = BUILDER
            .comment("TACZ gun IDs vanilla pillagers may spawn with. Invalid IDs are ignored.")
            .defineList(List.of("pillager_tacz_weapon_ids"),
                    () -> List.of("tacz:ak47", "tacz:rpg7", "tacz:glock_7"),
                    () -> "tacz:ak47",
                    entry -> entry instanceof String,
                    ModConfigSpec.Range.of(0, 256));

    public static final ModConfigSpec.DoubleValue PILLAGER_TACZ_WEAPON_DROP_CHANCE = BUILDER
            .comment("Chance for a TACZ-armed pillager to drop its TACZ weapon on death.")
            .defineInRange("pillager_tacz_weapon_drop_chance", 0.25D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue PILLAGER_TACZ_AMMO_DROP_MIN = BUILDER
            .comment("Minimum compatible TACZ ammo items dropped when a TACZ-armed pillager drops ammo.")
            .defineInRange("pillager_tacz_ammo_drop_min", 3, 0, 256);

    public static final ModConfigSpec.IntValue PILLAGER_TACZ_AMMO_DROP_MAX = BUILDER
            .comment("Maximum compatible TACZ ammo items dropped when a TACZ-armed pillager drops ammo.")
            .defineInRange("pillager_tacz_ammo_drop_max", 12, 0, 256);

    static final ModConfigSpec SPEC = BUILDER.build();
}
