package eu.the5zig.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.the5zig.fabric.hud.ItemType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JSON config at config/the5zig.json. Field names mirror the original mod's modules.json where they overlap. */
public final class Config {

    public enum Location {
        TOP_LEFT, TOP_RIGHT, CENTER_LEFT, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CUSTOM;

        public Location next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean right() {
            return this == TOP_RIGHT || this == CENTER_RIGHT || this == BOTTOM_RIGHT;
        }
    }

    /** Original 5zig "formattingBrackets" styles. */
    public enum Brackets {
        ARROW("", ">"), COLON("", ":"), BRACKETS("[", "]"), BRACKETS_ROUND("(", ")"), ARROW2("<", ">"), DASH("", " -"), NONE("", "");

        public final String first, last;

        Brackets(String first, String last) {
            this.first = first;
            this.last = last;
        }

        public Brackets next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final class Item {
        public String type;
        /** Item-specific style (direction style, durability style, ...); null = item default. */
        public String option;
        public boolean showPrefix = true;

        public Item() {}

        public Item(ItemType t) {
            this.type = t.name();
            this.option = t.defaultOption();
        }
    }

    public static final class Module {
        public String id;
        public String name;
        public boolean enabled = true;
        public boolean showLabel = true;
        public Location location = Location.TOP_LEFT;
        /** CUSTOM location: top-left corner as a fraction of the screen. */
        public float x = 0.1f, y = 0.1f;
        public float scale = 1.0f;
        public float boxOpacity = 0.0f;
        public List<Item> items = new ArrayList<>();

        public Module() {}

        Module(String id, String name, boolean showLabel, Location location, ItemType... types) {
            this.id = id;
            this.name = name;
            this.showLabel = showLabel;
            this.location = location;
            for (ItemType t : types) items.add(new Item(t));
        }

        public Item find(ItemType t) {
            for (Item i : items) if (t.name().equals(i.type)) return i;
            return null;
        }
    }

    // ---- global formatting (5zig defaults: gold prefix, white value, gray "> ") ----
    public boolean enabled = true;
    public char colorPrefix = '6';
    public char colorMain = 'f';
    public char colorBrackets = '7';
    public Brackets formattingBrackets = Brackets.ARROW;
    public boolean textShadow = true;
    public boolean coloredEquipmentDurability = true;
    public boolean showWithDebugScreen = false;
    public List<Module> modules = new ArrayList<>();

    // ------------------------------------------------------------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("the5zig.json");
    }

    public static void load() {
        Path p = path();
        Config c = null;
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                c = GSON.fromJson(r, Config.class);
            } catch (Exception e) {
                The5zigMod.LOGGER.warn("Could not read {}, using defaults", p, e);
            }
        }
        if (c == null || c.modules == null) c = defaults();
        c.sanitize();
        instance = c;
        save();
    }

    public static void save() {
        if (instance == null) return;
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            The5zigMod.LOGGER.warn("Could not save 5zig config", e);
        }
    }

    public static void reset() {
        instance = defaults();
        save();
    }

    private void sanitize() {
        if (formattingBrackets == null) formattingBrackets = Brackets.ARROW;
        for (Module m : modules) {
            if (m.location == null) m.location = Location.TOP_LEFT;
            if (m.items == null) m.items = new ArrayList<>();
            m.items.removeIf(i -> i == null || ItemType.byName(i.type) == null);
            m.scale = Math.max(0.5f, Math.min(3f, m.scale));
            m.x = Math.max(0f, Math.min(1f, m.x));
            m.y = Math.max(0f, Math.min(1f, m.y));
        }
    }

    /** Same default layout as the original mod's modules.json (minus the server-specific modules). */
    static Config defaults() {
        Config c = new Config();
        c.modules.add(new Module("stats", "The 5zig Mod", true, Location.TOP_LEFT,
                ItemType.AFK_TIME, ItemType.FPS, ItemType.COORDINATES, ItemType.DIRECTION, ItemType.BIOME,
                ItemType.IP, ItemType.PLAYERS, ItemType.PING, ItemType.TIMER));
        c.modules.add(new Module("armor", "Armor", true, Location.TOP_RIGHT,
                ItemType.MAIN_HAND, ItemType.OFF_HAND, ItemType.ARROWS, ItemType.HELMET, ItemType.CHESTPLATE,
                ItemType.LEGGINGS, ItemType.BOOTS));
        c.modules.add(new Module("potions", "Potions", true, Location.CENTER_LEFT, ItemType.POTIONS));
        Module system = new Module("system", "System", false, Location.BOTTOM_RIGHT,
                ItemType.CPS, ItemType.SPEED, ItemType.TIME, ItemType.MEMORY);
        system.enabled = false;
        c.modules.add(system);
        return c;
    }
}
