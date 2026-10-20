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

        void clamp() {
            shadowmeldPoints = Math.max(0, Math.min(10, shadowmeldPoints));
            justMyNaturePoints = Math.max(0, Math.min(5, justMyNaturePoints));
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
    public static int shadowmeldPoints() { return data.shadowmeldPoints; }
    public static int justMyNaturePoints() { return data.justMyNaturePoints; }
}
