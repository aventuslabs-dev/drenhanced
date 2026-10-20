package eu.the5zig.fabric.hud;

import eu.the5zig.fabric.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/**
 * Every HUD item the port supports. Names match the original mod's item ids where an equivalent exists.
 * TEXT items return a String (one "Label> value" line), a String[][] of {label, value} lines, or null to hide.
 * STACK items return an ItemStack (null/empty hides). POTIONS is drawn specially by {@link HudRenderer}.
 */
public enum ItemType {
    FPS("FPS", Kind.TEXT, null,
            c -> String.valueOf(c.mc.getFps()),
            o -> "144"),
    COORDINATES("Coordinates", Kind.TEXT, new String[]{"BELOW_OTHER", "SIDE_BY_SIDE"},
            c -> coords(c.option, c.p.getX(), c.p.getY(), c.p.getZ()),
            o -> coords(o, 128.5, 64, -256.5)),
    DIRECTION("F", Kind.TEXT, new String[]{"STRING_AXIS", "STRING", "NUMBER", "BOTH", "DEGREE"},
            c -> direction(c.option, c.p.getYRot()),
            o -> direction(o, 0f)),
    YAW_PITCH("Yaw/Pitch", Kind.TEXT, null,
            c -> one(Mth.wrapDegrees(c.p.getYRot())) + " / " + one(c.p.getXRot()),
            o -> "-90.0 / 12.5"),
    BIOME("Biome", Kind.TEXT, null,
            c -> c.mc.level.getBiome(c.p.blockPosition()).unwrapKey()
                    .map(k -> biomeName(k.location())).orElse(null),
            o -> "Plains"),
    SPEED("Speed", Kind.TEXT, null,
            c -> String.format(Locale.ROOT, "%.2f m/s", Tracker.speed()),
            o -> "5.61 m/s"),
    LIGHT("Light", Kind.TEXT, null,
            c -> {
                BlockPos pos = c.p.blockPosition();
                return c.mc.level.getBrightness(LightLayer.BLOCK, pos) + " (sky " + c.mc.level.getBrightness(LightLayer.SKY, pos) + ")";
            },
            o -> "12 (sky 15)"),
    CHUNK("Chunk", Kind.TEXT, null,
            c -> c.p.chunkPosition().x + ", " + c.p.chunkPosition().z + " (" + (c.p.getBlockX() & 15) + ", " + (c.p.getBlockZ() & 15) + ")",
            o -> "8, -16 (4, 9)"),
    TARGET_BLOCK("Looking at", Kind.TEXT, null,
            c -> {
                BlockPos pos = targetBlock(c.mc);
                return pos == null ? null : c.mc.level.getBlockState(pos).getBlock().getName().getString();
            },
            o -> "Stone"),
    TARGET_COORDS("Target", Kind.TEXT, null,
            c -> {
                BlockPos pos = targetBlock(c.mc);
                return pos == null ? null : pos.getX() + "/" + pos.getY() + "/" + pos.getZ();
            },
            o -> "130/63/-250"),
    ENTITY_HEALTH("Health", Kind.TEXT, null,
            c -> {
                Entity e = c.mc.crosshairPickEntity;
                if (!(e instanceof LivingEntity le)) return null;
                return le.getName().getString() + " " + one(le.getHealth()) + "/" + one(le.getMaxHealth());
            },
            o -> "Zombie 14.0/20.0"),
    ENTITY_COUNT("Entities", Kind.TEXT, null,
            c -> String.valueOf(c.mc.level.getEntityCount()),
            o -> "87"),
    DAY("Day", Kind.TEXT, null,
            c -> String.valueOf(c.mc.level.getDayTime() / 24000L),
            o -> "42"),
    IP("IP", Kind.TEXT, null,
            c -> {
                ServerData s = c.mc.getCurrentServer();
                return s == null || c.mc.isLocalServer() ? null : s.ip;
            },
            o -> "play.example.net"),
    PLAYERS("Players", Kind.TEXT, null,
            c -> {
                ClientPacketListener conn = c.mc.getConnection();
                return conn == null || c.mc.isLocalServer() ? null : String.valueOf(conn.getOnlinePlayers().size());
            },
            o -> "128"),
    PING("Ping", Kind.TEXT, null,
            c -> {
                ClientPacketListener conn = c.mc.getConnection();
                if (conn == null || c.mc.isLocalServer()) return null;
                PlayerInfo info = conn.getPlayerInfo(c.p.getUUID());
                return info == null ? null : info.getLatency() + "ms";
            },
            o -> "42ms"),
    TIMER("Timer", Kind.TEXT, null,
            c -> {
                long t = Tracker.timer();
                return t < 0 ? null : (Tracker.timerRunning() ? "" : "§7") + clock(t, true);
            },
            o -> "01:23.4"),
    TIME("Time", Kind.TEXT, new String[]{"24H", "12H"},
            c -> time(c.option),
            ItemType::time),
    DATE("Date", Kind.TEXT, new String[]{"ISO", "DMY", "MDY"},
            c -> date(c.option),
            ItemType::date),
    MEMORY("Memory", Kind.TEXT, null,
            c -> memory(),
            o -> memory()),
    CPS("CPS", Kind.TEXT, new String[]{"SUM", "LEFT", "RIGHT", "BOTH"},
            c -> cps(c.option, Tracker.leftCps(), Tracker.rightCps()),
            o -> cps(o, 8, 3)),
    AFK_TIME("AFK", Kind.TEXT, null,
            c -> {
                if (Tracker.afkTime() > Tracker.AFK_AFTER) return clock(Tracker.afkTime(), false);
                if (Tracker.lastAfkTime() != 0) return "§n" + clock(Tracker.lastAfkTime(), false);
                return null;
            },
            o -> "01:00"),
    DEATH_COORDINATES("Death", Kind.TEXT, null,
            c -> {
                int[] d = Tracker.deathPos();
                if (d == null) return null;
                String s = d[0] + "/" + d[1] + "/" + d[2];
                String dim = Tracker.deathDimension();
                String here = c.p.level().dimension().location().getPath();
                return dim != null && !dim.equals(here) ? s + " (" + dim + ")" : s;
            },
            o -> "-37/58/912"),
    ARROWS("Arrows", Kind.TEXT, null,
            c -> {
                if (!holdsRanged(c.p)) return null;
                int n = count(c.p, Items.ARROW) + count(c.p, Items.SPECTRAL_ARROW) + count(c.p, Items.TIPPED_ARROW);
                return String.valueOf(n);
            },
            o -> "64"),
    TOTEMS("Totems", Kind.TEXT, null,
            c -> {
                int n = count(c.p, Items.TOTEM_OF_UNDYING);
                return n == 0 ? null : String.valueOf(n);
            },
            o -> "2"),
    SOUPS("Soups", Kind.TEXT, null,
            c -> {
                int n = count(c.p, Items.MUSHROOM_STEW);
                return n == 0 ? null : String.valueOf(n);
            },
            o -> "18"),
    MAIN_HAND("Main hand", Kind.STACK, Durability.NAMES,
            c -> c.p.getItemBySlot(EquipmentSlot.MAINHAND),
            o -> sample(Items.DIAMOND_SWORD, 0.83f)),
    OFF_HAND("Off hand", Kind.STACK, Durability.NAMES,
            c -> c.p.getItemBySlot(EquipmentSlot.OFFHAND),
            o -> sample(Items.SHIELD, 0.4f)),
    HELMET("Helmet", Kind.STACK, Durability.NAMES,
            c -> c.p.getItemBySlot(EquipmentSlot.HEAD),
            o -> sample(Items.DIAMOND_HELMET, 0.97f)),
    CHESTPLATE("Chestplate", Kind.STACK, Durability.NAMES,
            c -> c.p.getItemBySlot(EquipmentSlot.CHEST),
            o -> sample(Items.DIAMOND_CHESTPLATE, 0.62f)),
    LEGGINGS("Leggings", Kind.STACK, Durability.NAMES,
            c -> c.p.getItemBySlot(EquipmentSlot.LEGS),
            o -> sample(Items.IRON_LEGGINGS, 0.25f)),
    BOOTS("Boots", Kind.STACK, Durability.NAMES,
            c -> c.p.getItemBySlot(EquipmentSlot.FEET),
            o -> sample(Items.NETHERITE_BOOTS, 0.05f)),
    POTIONS("Potions", Kind.POTIONS, new String[]{"PLAIN", "COLORED"}, c -> null, o -> null);

    public enum Kind {TEXT, STACK, POTIONS}

    public enum Durability {
        RELATIVE, ABSOLUTE, TOTAL, DISABLED;
        static final String[] NAMES = {"RELATIVE", "ABSOLUTE", "TOTAL", "DISABLED"};
    }

    /** What an item's value function sees. {@code p} is never null when a real value is requested. */
    public static final class Ctx {
        public final Minecraft mc;
        public final LocalPlayer p;
        public final String option;

        Ctx(Minecraft mc, String option) {
            this.mc = mc;
            this.p = mc.player;
            this.option = option;
        }
    }

    public final String label;
    public final Kind kind;
    private final String[] options;
    private final Function<Ctx, Object> real;
    private final Function<String, Object> sample;

    ItemType(String label, Kind kind, String[] options, Function<Ctx, Object> real, Function<String, Object> sample) {
        this.label = label;
        this.kind = kind;
        this.options = options;
        this.real = real;
        this.sample = sample;
    }

    public static ItemType byName(String name) {
        if (name == null) return null;
        for (ItemType t : values()) if (t.name().equals(name)) return t;
        return null;
    }

    public String defaultOption() {
        return options == null ? null : options[0];
    }

    public boolean hasOptions() {
        return options != null && options.length > 1;
    }

    public String option(String current) {
        if (options == null) return null;
        for (String o : options) if (o.equals(current)) return o;
        return options[0];
    }

    public String nextOption(String current) {
        if (options == null) return null;
        String cur = option(current);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(cur)) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    /**
     * The value to display. In the layout editor ({@code dummy}) a sample is shown whenever there's no live value,
     * so every module stays visible and draggable.
     */
    public Object value(Minecraft mc, String option, boolean dummy) {
        String opt = option(option);
        Object v = null;
        if (mc.player != null && mc.level != null) {
            try {
                v = real.apply(new Ctx(mc, opt));
            } catch (RuntimeException e) {
                v = null; // never let one item take the HUD down
            }
            if (v instanceof ItemStack s && s.isEmpty()) v = null;
        }
        if (v == null && dummy) v = sample.apply(opt);
        return v;
    }

    // ------------------------------------------------------------------ helpers

    static String one(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static Object coords(String option, double x, double y, double z) {
        if ("SIDE_BY_SIDE".equals(option)) return new String[][]{{"X/Y/Z", one(x) + " / " + one(y) + " / " + one(z)}};
        return new String[][]{{"X", one(x)}, {"Y", one(y)}, {"Z", one(z)}};
    }

    /** Port of the original Direction item: Minecraft "F" quadrant, compass name and axis hint. */
    private static String direction(String style, float yaw) {
        if ("DEGREE".equals(style)) {
            float d = yaw % 360f;
            if (d < 0) d += 360f;
            return one(d) + "°";
        }
        float f = (yaw / 360f * 4f) % 4f;
        if (f < 0) f += 4f;
        String name, axis;
        if (f >= 3.75f || f <= 0.25f) { name = "South"; axis = "Z+"; }
        else if (f < 0.75f) { name = "South-West"; axis = "X-, Z+"; }
        else if (f <= 1.25f) { name = "West"; axis = "X-"; }
        else if (f < 1.75f) { name = "North-West"; axis = "X-, Z-"; }
        else if (f <= 2.25f) { name = "North"; axis = "Z-"; }
        else if (f < 2.75f) { name = "North-East"; axis = "X+, Z-"; }
        else if (f <= 3.25f) { name = "East"; axis = "X+"; }
        else { name = "South-East"; axis = "X+, Z+"; }
        String number = one(f);
        if (number.startsWith("4")) number = "0" + number.substring(1);
        return switch (style == null ? "STRING_AXIS" : style) {
            case "STRING" -> name;
            case "NUMBER" -> number;
            case "BOTH" -> name + " (" + number + ")";
            default -> name + " " + Fmt.bracketed(axis);
        };
    }

    private static String biomeName(ResourceLocation id) {
        String key = "biome." + id.getNamespace() + "." + id.getPath();
        if (I18n.exists(key)) return I18n.get(key);
        String s = id.getPath().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BlockPos targetBlock(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hit).getBlockPos();
    }

    static String clock(long millis, boolean tenths) {
        long s = millis / 1000;
        String base = String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
        return tenths ? base + "." + (millis / 100) % 10 : base;
    }

    private static String time(String option) {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("12H".equals(option) ? "hh:mm a" : "HH:mm", Locale.ROOT));
    }

    private static String date(String option) {
        String pattern = switch (option == null ? "ISO" : option) {
            case "DMY" -> "dd.MM.yyyy";
            case "MDY" -> "MM/dd/yyyy";
            default -> "yyyy-MM-dd";
        };
        return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
    }

    private static String memory() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long max = rt.maxMemory() / 1048576L;
        return used + "/" + max + "MB (" + (max == 0 ? 0 : used * 100 / max) + "%)";
    }

    private static String cps(String option, int l, int r) {
        return switch (option == null ? "SUM" : option) {
            case "LEFT" -> String.valueOf(l);
            case "RIGHT" -> String.valueOf(r);
            case "BOTH" -> l + " | " + r;
            default -> String.valueOf(l + r);
        };
    }

    private static boolean holdsRanged(LocalPlayer p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem) return true;
        }
        return false;
    }

    private static int count(LocalPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static ItemStack sample(Item item, float remaining) {
        ItemStack s = new ItemStack(item);
        if (s.isDamageableItem()) s.setDamageValue(Math.round(s.getMaxDamage() * (1f - remaining)));
        return s;
    }
}
