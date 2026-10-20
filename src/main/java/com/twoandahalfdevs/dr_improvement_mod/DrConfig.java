package com.twoandahalfdevs.dr_improvement_mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client settings, stored in config/dr_improvement_mod.json.
 * The defaults below are the single source of truth (the old ModMenu screen disagreed with them).
 */
public final class DrConfig {
    private DrConfig() {}

    /** The JSON shape. Missing keys keep their defaults, so old/partial files still load. */
    public static final class Data {
        public boolean customBars = true;
        public boolean usefulInfo = true;
        public boolean guildNametags = true;
        public boolean colorBlindGlow = false;
        public boolean hotbarAbilities = true;
        public boolean throttlePackets = true;
        public boolean legacyCombat = true;
        public int shadowmeldPoints = 0;      // 0-10
        public int justMyNaturePoints = 0;    // 0-5
        /** Player chat on the left, system/server messages in a second chat on the right. */
        public boolean splitChat = true;
        /** Fold repeated system messages into "message (x3)". */
        public boolean collapseRepeats = true;
        /** Regexes (matched against the plain text) that force a message into the left/player chat. */
        public java.util.List<String> chatPlayerPatterns = new java.util.ArrayList<>();
        /** Regexes that force a message into the right/system chat (checked first). */
        public java.util.List<String> chatSystemPatterns = new java.util.ArrayList<>();
        /** Look of DR's mob health bars: ORIGINAL, BAR, BAR_PERCENT, PERCENT or HEARTS. */
        public String enemyHpStyle = "MODERN";
        /** WoW-style plates for real players too (name, <guild>, HP bar in combat). */
        public boolean playerPlates = true;
        /** WoW-style bar: size in nametag pixels, colour (REACTION, HEALTH, GOLD) and text (CURRENT_MAX, CURRENT, PERCENT, CURRENT_PERCENT, NONE). */
        public int enemyHpBarWidth = 64;
        public int enemyHpBarHeight = 8;
        public String enemyHpColor = "REACTION";
        public String enemyHpText = "CURRENT_MAX";
        public int enemyHpBarLength = 12;
        public boolean enemyHpShowLevel = true;
        /** Placement of the shader bars: energy arcs, mana bar, HP bar. */
        public Bar energyBar = new Bar();
        public Bar manaBar = new Bar();
        public Bar healthBar = new Bar();
        /** Floating combat text (replaces DR's damage holograms). */
        public boolean fctEnabled = true;
        public double fctSize = 1.0;
        public double fctDuration = 1.4;
        public boolean fctHeals = true;
        public boolean fctIncoming = true;
        public boolean fctHideDr = true;
        public boolean fctBigHits = true;
        public boolean fctThroughWalls = true;
        public boolean fctProcs = true;
        /** WoW-style cooldown bar above the hotbar. */
        public boolean cooldownBar = true;
        public int cdIconSize = 22;
        public int cdX = 0;          // sideways from centre
        public int cdY = 82;         // up from the bottom of the screen (above the action-bar text)
        public boolean cdShowCombat = true;
        public boolean cdShowItems = true;
        public boolean cdShowLabels = false;
        /** Size, text and position of the left (player) and right (system) chat boxes. */
        public ChatBox leftChat = new ChatBox();
        public ChatBox rightChat = new ChatBox();

        void clamp() {
            enemyHpBarLength = Math.max(4, Math.min(40, enemyHpBarLength));
            fctSize = Math.max(0.3, Math.min(4.0, fctSize));
            fctDuration = Math.max(0.4, Math.min(5.0, fctDuration));
            cdIconSize = Math.max(12, Math.min(48, cdIconSize));
            cdX = Math.max(-1000, Math.min(1000, cdX));
            cdY = Math.max(0, Math.min(1000, cdY));
            enemyHpBarWidth = Math.max(30, Math.min(200, enemyHpBarWidth));
            enemyHpBarHeight = Math.max(4, Math.min(24, enemyHpBarHeight));
            if (enemyHpColor == null) enemyHpColor = "REACTION";
            if (enemyHpText == null) enemyHpText = "CURRENT_MAX";
            if (energyBar == null) energyBar = new Bar();
            if (manaBar == null) manaBar = new Bar();
            if (healthBar == null) healthBar = new Bar();
            energyBar.clamp();
            manaBar.clamp();
            healthBar.clamp();
            if (leftChat == null) leftChat = new ChatBox();
            if (rightChat == null) rightChat = new ChatBox();
            leftChat.clamp();
            rightChat.clamp();
            shadowmeldPoints = Math.max(0, Math.min(10, shadowmeldPoints));
            justMyNaturePoints = Math.max(0, Math.min(5, justMyNaturePoints));
        }
    }

    public static final class Bar {
        public boolean enabled = true;
        /** 1.0 = original size. */
        public double scale = 1.0;
        /** Offset in percent of the screen; y is upwards. */
        public double x = 0.0;
        public double y = 0.0;
        public double opacity = 1.0;

        void clamp() {
            scale = Math.max(0.25, Math.min(4.0, scale));
            x = Math.max(-50, Math.min(50, x));
            y = Math.max(-50, Math.min(50, y));
            opacity = Math.max(0.05, Math.min(1.0, opacity));
        }
    }

    public static final class ChatBox {
        /** Width in GUI pixels; 0 = automatic (vanilla width, at most half the screen). */
        public int width = 0;
        public int heightFocused = 180;
        public int heightUnfocused = 90;
        /** Text size. */
        public double scale = 1.0;
        public double textOpacity = 1.0;
        public double background = 0.5;
        /** Distance from its screen edge (left edge for the left chat, right edge for the right chat). */
        public int x = 0;
        /** Distance moved up from the default position. */
        public int y = 0;

        void clamp() {
            width = Math.max(0, Math.min(1000, width));
            heightFocused = Math.max(20, Math.min(600, heightFocused));
            heightUnfocused = Math.max(20, Math.min(600, heightUnfocused));
            scale = Math.max(0.25, Math.min(3.0, scale));
            textOpacity = Math.max(0.1, Math.min(1.0, textOpacity));
            background = Math.max(0.0, Math.min(1.0, background));
            x = Math.max(0, Math.min(1000, x));
            y = Math.max(0, Math.min(1000, y));
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dr_improvement_mod.json");

    public static Data data = new Data();

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Data loaded = GSON.fromJson(r, Data.class);
                    if (loaded != null) data = loaded;
                }
            }
        } catch (Exception e) {
            DrImprovementMod.LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
            data = new Data();
        }
        data.clamp();
        save(); // writes defaults for new keys
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            DrImprovementMod.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    public static boolean customBars() { return data.customBars; }
    public static boolean usefulInfo() { return data.usefulInfo; }
    public static boolean guildNametags() { return data.guildNametags; }
    public static boolean colorBlind() { return data.colorBlindGlow; }
    public static boolean hotbarAbilities() { return data.hotbarAbilities; }
    public static boolean throttlePackets() { return data.throttlePackets; }
    public static boolean legacyCombat() { return data.legacyCombat; }
    public static EnemyHp.Style enemyHpStyle() {
        try {
            return EnemyHp.Style.valueOf(data.enemyHpStyle);
        } catch (Exception e) {
            return EnemyHp.Style.MODERN;
        }
    }
    public static int shadowmeldPoints() { return data.shadowmeldPoints; }
    public static int justMyNaturePoints() { return data.justMyNaturePoints; }
}
