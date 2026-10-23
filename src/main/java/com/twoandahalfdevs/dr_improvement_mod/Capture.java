package com.twoandahalfdevs.dr_improvement_mod;

import com.twoandahalfdevs.dr_improvement_mod.mixin.GuiAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Message capture mode: writes everything DungeonRealms shows as text (chat, action bar, titles, boss bars,
 * sidebar, nameplates / holograms, held item name + lore) to .minecraft/dr_capture/, so the mod's parsers can
 * be matched to DR's exact wording. Session-only: it never switches itself on at startup.
 */
public final class Capture {
    private Capture() {}

    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static ExecutorService io;
    private static BufferedWriter out;
    private static Path file;
    private static long bytes;
    private static int lines;

    // change detection so polled sources only log when something changes
    private static String lastActionBar = "", lastTitle = "", lastSubtitle = "", lastSidebar = "", lastHeld = "";
    private static final Set<String> lastBoss = new HashSet<>();
    private static final Set<String> bossThisFrame = new HashSet<>();
    private static final Map<Integer, String> names = new HashMap<>();
    private static int tickCounter;

    public static boolean active() {
        return out != null;
    }

    public static Path file() {
        return file;
    }

    /** Toggle; returns a message for the player. */
    public static synchronized Component toggle() {
        if (active()) {
            stop("stopped by player");
            return Component.literal("§6[DR] §fCapture §cstopped§f: " + lines + " lines in §e" + relative());
        }
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("dr_capture");
            Files.createDirectories(dir);
            file = dir.resolve("capture-" + LocalDateTime.now().format(FILE) + ".log");
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DR capture writer");
                t.setDaemon(true);
                return t;
            });
            bytes = 0;
            lines = 0;
            lastActionBar = lastTitle = lastSubtitle = lastSidebar = lastHeld = "";
            lastBoss.clear();
            names.clear();
            write("INFO", "DR Improvement Mod capture started. Format: time | type | plain text | raw JSON", null);
            var mc = Minecraft.getInstance();
            if (mc.getCurrentServer() != null) write("INFO", "server " + mc.getCurrentServer().ip, null);
            return Component.literal("§6[DR] §fCapture §astarted§f, writing to §e" + relative()
                + "§f. Play normally, then run §e/drcapture§f again to stop.");
        } catch (Exception e) {
            DrImprovementMod.LOGGER.warn("Couldn't start capture", e);
            out = null;
            return Component.literal("§6[DR] §cCouldn't start capture: " + e.getMessage());
        }
    }

    private static String relative() {
        return file == null ? "?" : "dr_capture/" + file.getFileName();
    }

    public static synchronized void stop(String why) {
        if (out == null) return;
        write("INFO", "capture stopped (" + why + ")", null);
        BufferedWriter w = out;
        out = null;
        ExecutorService ex = io;
        ex.submit(() -> {
            try {
                w.close();
            } catch (Exception ignored) {
            }
        });
        ex.shutdown();
    }

    public static void mark(String note) {
        write("MARK", note, null);
    }

    // ------------------------------------------------------------------ sources

    public static void chat(String type, Component c) {
        if (!active() || c == null) return;
        write(type, c.getString(), c);
    }

    public static void route(Component c, boolean left) {
        if (!active() || c == null) return;
        write("ROUTE", (left ? "LEFT  " : "RIGHT ") + c.getString(), null);
    }

    /** Called by the boss bar mixin for each bar drawn this frame. */
    public static void boss(Component name) {
        if (!active() || name == null) return;
        String s = name.getString();
        bossThisFrame.add(s);
        if (lastBoss.add(s)) write("BOSSBAR", s, name);
    }

    /** Every client tick: poll the sources that have no single packet hook. */
    public static void tick(Minecraft mc) {
        if (!active()) return;
        if (bytes > MAX_BYTES) {
            stop("size limit reached");
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("§6[DR] §fCapture stopped at 25 MB: §e" + relative()), false);
            return;
        }
        lastBoss.retainAll(bossThisFrame.isEmpty() ? lastBoss : bossThisFrame);
        bossThisFrame.clear();
        if (mc.player == null || mc.level == null) return;
        GuiAccessor gui = (GuiAccessor) mc.gui;

        Component ab = gui.drim$getOverlayMessage();
        String abs = ab == null ? "" : ab.getString();
        if (!abs.equals(lastActionBar)) {
            lastActionBar = abs;
            if (!abs.isEmpty()) write("ACTIONBAR", abs, ab);
        }
        Component title = gui.drim$getTitle(), sub = gui.drim$getSubtitle();
        String ts = title == null ? "" : title.getString(), ss = sub == null ? "" : sub.getString();
        if (!ts.equals(lastTitle)) {
            lastTitle = ts;
            if (!ts.isEmpty()) write("TITLE", ts, title);
        }
        if (!ss.equals(lastSubtitle)) {
            lastSubtitle = ss;
            if (!ss.isEmpty()) write("SUBTITLE", ss, sub);
        }

        if (++tickCounter % 5 == 0) pollNames(mc);
        if (tickCounter % 20 == 0) {
            pollSidebar(mc);
            pollHeld(mc);
        }
    }

    private static String lastXp = "";

    /** DR's XP packet (energy / mana live here), logged when it changes. */
    public static void xp(float progress, int level, int total) {
        if (!active()) return;
        String t = String.format(java.util.Locale.ROOT, "progress=%.3f level=%d total=%d", progress, level, total);
        if (t.equals(lastXp)) return;
        lastXp = t;
        write("XP", t, null);
    }

    private static void pollNames(Minecraft mc) {
        Set<Integer> alive = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            Component n = e instanceof Player ? e.getDisplayName() : e.getCustomName();
            if (n == null) n = FloatingText.displayText(e); // DR's floating text (text displays)
            if (n == null) continue;
            alive.add(e.getId());
            String s = n.getString();
            if (s.equals(names.get(e.getId()))) continue;
            names.put(e.getId(), s);
            double d = Math.sqrt(e.distanceToSqr(mc.player));
            String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            write("NAME", String.format(java.util.Locale.ROOT, "%s#%d d=%.1f | %s", type, e.getId(), d, s), n);
        }
        names.keySet().retainAll(alive);
    }

    private static void pollSidebar(Minecraft mc) {
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return;
        List<String> rows = new ArrayList<>();
        rows.add("[" + obj.getDisplayName().getString() + "]");
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            if (e.isHidden()) continue;
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            rows.add(PlayerTeam.formatNameForTeam(team, e.ownerName()).getString() + " = " + e.value());
        }
        String all = String.join(" || ", rows);
        if (!all.equals(lastSidebar)) {
            lastSidebar = all;
            write("SIDEBAR", all, null);
        }
    }

    private static void pollHeld(Minecraft mc) {
        ItemStack s = mc.player.getMainHandItem();
        if (s.isEmpty()) return;
        StringBuilder b = new StringBuilder(s.getHoverName().getString());
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore != null) for (Component l : lore.lines()) b.append(" || ").append(l.getString());
        String txt = b.toString();
        if (!txt.equals(lastHeld)) {
            lastHeld = txt;
            write("ITEM", txt, null);
        }
    }

    // ------------------------------------------------------------------ output

    private static void write(String type, String plain, Component raw) {
        if (out == null) return;
        String json = "";
        if (raw != null) {
            try {
                var mc = Minecraft.getInstance();
                if (mc.level != null) json = Component.Serializer.toJson(raw, mc.level.registryAccess());
            } catch (Exception ignored) {
            }
        }
        String line = LocalTime.now().format(TIME) + " | " + type + " | " + plain.replace('\n', ' ')
            + (json.isEmpty() ? "" : " | " + json) + "\n";
        bytes += line.length();
        lines++;
        BufferedWriter w = out;
        io.submit(() -> {
            try {
                w.write(line);
                w.flush();
            } catch (Exception ignored) {
            }
        });
    }

    /** Small "REC" marker so it's never left on by accident. */
    public static void renderIndicator(GuiGraphics g) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
        g.drawString(mc.font, (blink ? "§c●" : "§4●") + " §fREC §7" + lines, 4, 4, 0xFFFFFFFF, true);
    }
}
