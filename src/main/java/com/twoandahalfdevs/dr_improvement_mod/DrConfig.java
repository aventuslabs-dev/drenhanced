package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config, stored in config/dr_improvement_mod-client.toml.
 * Defaults here are the single source of truth (the old ModMenu screen disagreed with them).
 */
public final class DrConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CUSTOM_BARS;
    public static final ForgeConfigSpec.BooleanValue USEFUL_INFO;
    public static final ForgeConfigSpec.BooleanValue GUILD_NAMETAGS;
    public static final ForgeConfigSpec.BooleanValue COLOR_BLIND;
    public static final ForgeConfigSpec.BooleanValue HOTBAR_ABILITIES;
    public static final ForgeConfigSpec.BooleanValue THROTTLE_PACKETS;
    public static final ForgeConfigSpec.BooleanValue LEGACY_COMBAT;
    public static final ForgeConfigSpec.IntValue SHADOWMELD_POINTS;
    public static final ForgeConfigSpec.IntValue JUST_MY_NATURE_POINTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("hud");
        CUSTOM_BARS = b.comment("Shader energy arcs, mana bar and top HP bar; hides the HP boss bar.")
            .define("customBars", true);
        USEFUL_INFO = b.comment("Ability cooldown, potion count/cooldown and combat timer text around the crosshair.")
            .define("usefulInfo", true);
        b.pop();

        b.push("visuals");
        GUILD_NAMETAGS = b.comment("Light green nametags for members of your guild.")
            .define("guildNametags", true);
        COLOR_BLIND = b.comment("Colour-blind friendly item rarity glow colours.")
            .define("colorBlindGlow", false);
        b.pop();

        b.push("gameplay");
        HOTBAR_ABILITIES = b.comment("Scrolling/pressing onto an ability head right-clicks it and returns to your previous slot.",
                "Check the server's rules before enabling this.")
            .define("hotbarAbilities", true);
        THROTTLE_PACKETS = b.comment("Debounce client-settings (1/s) and tab-completion (2/s) packets to avoid spam kicks.")
            .define("throttlePackets", true);
        LEGACY_COMBAT = b.comment("1.8 feel: no double-tap sprint, no miss-swing delay, no client crit effects, no re-equip bob, no swing on drop.")
            .define("legacyCombat", true);
        b.pop();

        b.push("rogue");
        SHADOWMELD_POINTS = b.comment("Points in the Rogue 'Shadowmeld' node (-3.5% combat time per point).")
            .defineInRange("shadowmeldPoints", 0, 0, 10);
        JUST_MY_NATURE_POINTS = b.comment("Points in the Rogue 'It's Just My Nature' node (+0.5s bonus window per point).")
            .defineInRange("justMyNaturePoints", 0, 0, 5);
        b.pop();

        SPEC = b.build();
    }

    private DrConfig() {}

    /** Safe read: mixins can run before the config file is loaded. */
    public static boolean on(ForgeConfigSpec.BooleanValue v, boolean fallback) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int num(ForgeConfigSpec.IntValue v, int fallback) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean customBars() { return on(CUSTOM_BARS, true); }
    public static boolean usefulInfo() { return on(USEFUL_INFO, true); }
    public static boolean guildNametags() { return on(GUILD_NAMETAGS, true); }
    public static boolean colorBlind() { return on(COLOR_BLIND, false); }
    public static boolean hotbarAbilities() { return on(HOTBAR_ABILITIES, true); }
    public static boolean throttlePackets() { return on(THROTTLE_PACKETS, true); }
    public static boolean legacyCombat() { return on(LEGACY_COMBAT, true); }
    public static int shadowmeldPoints() { return num(SHADOWMELD_POINTS, 0); }
    public static int justMyNaturePoints() { return num(JUST_MY_NATURE_POINTS, 0); }
}
