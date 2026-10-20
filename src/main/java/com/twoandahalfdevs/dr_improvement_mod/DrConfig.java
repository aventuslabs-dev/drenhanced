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
        /** Regexes (matched against the plain text) that force a message into the left/player chat. */
        public java.util.List<String> chatPlayerPatterns = new java.util.ArrayList<>();
        /** Regexes that force a message into the right/system chat (checked first). */
        public java.util.List<String> chatSystemPatterns = new java.util.ArrayList<>();
        /** Look of DR's mob health bars: ORIGINAL, BAR, BAR_PERCENT, PERCENT or HEARTS. */
        public String enemyHpStyle = "BAR_PERCENT";
        public int enemyHpBarLength = 12;
        public boolean enemyHpShowLevel = true;
        /** Size, text and position of the left (player) and right (system) chat boxes. */
        public ChatBox leftChat = new ChatBox();
        public ChatBox rightChat = new ChatBox();

        void clamp() {
            enemyHpBarLength = Math.max(4, Math.min(40, enemyHpBarLength));
            if (leftChat == null) leftChat = new ChatBox();
            if (rightChat == null) rightChat = new ChatBox();
            leftChat.clamp();
            rightChat.clamp();
            shadowmeldPoints = Math.max(0, Math.min(10, shadowmeldPoints));
            justMyNaturePoints = Math.max(0, Math.min(5, justMyNaturePoints));
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
            return EnemyHp.Style.BAR_PERCENT;
        }
    }
    public static int shadowmeldPoints() { return data.shadowmeldPoints; }
    public static int justMyNaturePoints() { return data.justMyNaturePoints; }
}
