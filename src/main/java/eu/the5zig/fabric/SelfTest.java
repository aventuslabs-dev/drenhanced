package eu.the5zig.fabric;

import eu.the5zig.fabric.gui.EditorScreen;
import eu.the5zig.fabric.gui.ModuleItemsScreen;
import eu.the5zig.fabric.gui.SettingsScreen;
import eu.the5zig.fabric.hud.ItemType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only smoke test, enabled with -Dthe5zig.selftest=true (./gradlew runClient -Pselftest).
 * Loads the mixin targets, opens every screen, renders the layout editor (which draws every module with
 * sample values: text, item icons, potion sprites) for a few dozen frames, exercises drag/scroll and the
 * config round-trip, then exits 0 on success or 1 on failure.
 */
final class SelfTest {
    private SelfTest() {}

    private static final String[] TARGETS = {
        "net.minecraft.client.MouseHandler",
        "net.minecraft.client.KeyboardHandler",
        "net.minecraft.client.gui.Gui",
    };

    private static final List<String> failures = new ArrayList<>();
    private static int stage;
    private static int wait;

    static boolean enabled() {
        return Boolean.getBoolean("the5zig.selftest");
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SelfTest::onTick);
    }

    private static void onTick(Minecraft mc) {
        if (stage < 5 && (mc.getOverlay() != null || mc.screen == null)) return; // still loading
        try {
            switch (stage) {
                case 0 -> {
                    for (String t : TARGETS) {
                        try {
                            Class.forName(t, true, SelfTest.class.getClassLoader());
                            log("mixin target ok: " + t);
                        } catch (Throwable e) {
                            fail("mixin target " + t + ": " + e);
                        }
                    }
                    // every module that ships by default must reference known items
                    for (Config.Module m : Config.get().modules) {
                        for (Config.Item i : m.items) {
                            if (ItemType.byName(i.type) == null) fail("unknown item " + i.type);
                        }
                    }
                    // add one module containing every item so the editor renders all of them
                    Config.Module all = new Config.Module();
                    all.id = "selftest";
                    all.name = "Everything";
                    all.location = Config.Location.CUSTOM;
                    for (ItemType t : ItemType.values()) all.items.add(new Config.Item(t));
                    Config.get().modules.add(all);
                    mc.setScreen(new SettingsScreen(mc.screen));
                    stage = 1;
                }
                case 1 -> {
                    if (!(mc.screen instanceof SettingsScreen)) fail("settings screen did not open");
                    else log("settings screen ok");
                    mc.setScreen(new ModuleItemsScreen(mc.screen, Config.get().modules.get(0)));
                    stage = 2;
                }
                case 2 -> {
                    if (!(mc.screen instanceof ModuleItemsScreen)) fail("items screen did not open");
                    else log("items screen ok");
                    mc.screen.mouseScrolled(10, 100, 0, -1);
                    mc.setScreen(new EditorScreen(mc.screen));
                    stage = 3;
                    wait = 0;
                }
                case 3 -> {
                    if (!(mc.screen instanceof EditorScreen ed)) {
                        fail("editor screen did not open");
                        stage = 4;
                        return;
                    }
                    if (++wait == 10) {
                        // grab the custom "Everything" module and drag it, then resize it
                        int cx = Math.round(0.1f * ed.width) + 5, cy = Math.round(0.1f * ed.height) + 5;
                        ed.mouseClicked(cx, cy, 0);
                        ed.mouseDragged(cx + 40, cy + 30, 0, 40, 30);
                        ed.mouseReleased(cx + 40, cy + 30, 0);
                        ed.mouseScrolled(cx + 40, cy + 30, 0, 1);
                        ed.mouseClicked(4, 4, 1); // right-click the top-left module: cycles its position
                    }
                    if (wait >= 40) {
                        log("editor rendered " + wait + " ticks with all " + ItemType.values().length + " items ok");
                        stage = 4;
                    }
                }
                case 4 -> {
                    Config.get().modules.removeIf(m -> "selftest".equals(m.id));
                    Config.save();
                    Config.load();
                    if (Config.get().modules.size() < 3) fail("config round-trip lost modules");
                    else log("config round-trip ok (" + Config.get().modules.size() + " modules)");
                    Config.reset();
                    // load a real world so the live HUD path (HudRenderCallback + real values) runs too
                    LevelSettings settings = new LevelSettings("the5zig-selftest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("the5zig-selftest", settings, new WorldOptions(5L, false, false),
                            WorldPresets::createNormalWorldDimensions, mc.screen);
                    log("creating world");
                    stage = 5;
                    wait = 0;
                }
                case 5 -> {
                    if (++wait > 20 * 240) {
                        fail("world did not load");
                        stage = 99;
                        return;
                    }
                    if (mc.player == null || mc.level == null || mc.screen != null) return;
                    MinecraftServer server = mc.getSingleplayerServer();
                    for (String cmd : new String[]{
                            "effect give @a speed 95 1", "effect give @a strength 25 0", "effect give @a night_vision 600 0",
                            "item replace entity @a weapon.mainhand with diamond_sword[damage=300]",
                            "item replace entity @a weapon.offhand with shield[damage=200]",
                            "item replace entity @a armor.head with iron_helmet[damage=20]",
                            "item replace entity @a armor.chest with diamond_chestplate",
                            "item replace entity @a armor.legs with golden_leggings[damage=90]",
                            "item replace entity @a armor.feet with netherite_boots[damage=400]",
                            "give @a totem_of_undying 2", "time set 6000"}) {
                        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
                    }
                    Tracker.toggleTimer();
                    for (int i = 0; i < 6; i++) Tracker.click(0);
                    Tracker.click(1);
                    log("in world, gear applied");
                    stage = 6;
                    wait = 0;
                }
                case 6 -> {
                    if (++wait < 200) return; // let chunks render
                    for (ItemType t : ItemType.values()) {
                        if (t.kind == ItemType.Kind.POTIONS) continue;
                        Object v = t.value(mc, null, false);
                        String shown = v instanceof String[][] a ? java.util.Arrays.deepToString(a) : String.valueOf(v);
                        log("live " + t.name() + " = " + shown);
                    }
                    for (ItemType t : new ItemType[]{ItemType.FPS, ItemType.COORDINATES, ItemType.DIRECTION, ItemType.BIOME,
                            ItemType.MAIN_HAND, ItemType.CHESTPLATE, ItemType.TIMER, ItemType.MEMORY, ItemType.TOTEMS}) {
                        if (t.value(mc, null, false) == null) fail("no live value for " + t);
                    }
                    if (mc.player.getActiveEffects().size() < 3) fail("potion effects not applied");
                    mc.gui.getChat().clearMessages(false);
                    stage = 61;
                    wait = 0;
                }
                case 61 -> {
                    if (++wait < 3) return;
                    Screenshot.grab(mc.gameDirectory, "the5zig-hud.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new EditorScreen(null));
                    stage = 7;
                    wait = 0;
                }
                case 7 -> {
                    if (++wait < 20) return;
                    Screenshot.grab(mc.gameDirectory, "the5zig-editor.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new SettingsScreen(null));
                    stage = 8;
                    wait = 0;
                }
                case 8 -> {
                    if (++wait < 10) return;
                    Screenshot.grab(mc.gameDirectory, "the5zig-settings.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    log("live HUD ok");
                    stage = 99;
                    wait = 0;
                }
                default -> {
                    if (++wait < 20) return; // give screenshot writers time to finish
                    if (failures.isEmpty()) {
                        log("5ZIG SELFTEST PASS");
                        Runtime.getRuntime().halt(0);
                    } else {
                        failures.forEach(f -> log("5ZIG SELFTEST FAIL: " + f));
                        Runtime.getRuntime().halt(1);
                    }
                }
            }
        } catch (Throwable e) {
            fail("unexpected: " + e);
            e.printStackTrace();
            failures.forEach(f -> log("5ZIG SELFTEST FAIL: " + f));
            Runtime.getRuntime().halt(1);
        }
    }

    private static void fail(String msg) {
        failures.add(msg);
    }

    private static void log(String msg) {
        The5zigMod.LOGGER.info(msg);
        System.out.println(msg);
    }
}
