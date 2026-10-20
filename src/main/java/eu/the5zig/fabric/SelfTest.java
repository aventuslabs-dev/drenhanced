package eu.the5zig.fabric;

import eu.the5zig.fabric.gui.EditorScreen;
import eu.the5zig.fabric.gui.ModuleItemsScreen;
import eu.the5zig.fabric.gui.SettingsScreen;
import eu.the5zig.fabric.hud.ItemType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

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
        if (mc.getOverlay() != null || mc.screen == null) return; // still loading
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
                    stage = 5;
                    wait = 0;
                }
                default -> {
                    if (++wait < 5) return;
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
