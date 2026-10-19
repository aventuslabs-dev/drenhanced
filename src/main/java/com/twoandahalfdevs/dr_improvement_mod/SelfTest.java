package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only smoke test, enabled with -Ddrim.selftest=true (./gradlew runClient -Pselftest).
 * Loads every mixin target (so broken injections fail loudly), compiles the overlay shader and
 * opens the config screen, then exits with 0 on success or 1 on failure.
 */
final class SelfTest {
    private SelfTest() {}

    private static final String[] TARGETS = {
        "net.minecraft.client.gui.Gui",
        "net.minecraft.world.entity.LivingEntity",
        "net.minecraft.client.multiplayer.ClientPacketListener",
        "net.minecraft.network.Connection",
        "net.minecraft.world.entity.Entity",
        "net.minecraft.client.renderer.ItemInHandRenderer",
        "net.minecraft.client.player.LocalPlayer",
        "net.minecraft.client.multiplayer.MultiPlayerGameMode",
        "net.minecraft.world.scores.PlayerTeam",
    };

    private static final List<String> failures = new ArrayList<>();
    private static int stage;
    private static int waitTicks;

    static boolean enabled() {
        return Boolean.getBoolean("drim.selftest");
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(SelfTest::onTick);
    }

    private static void onTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
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
                    var program = mc.getShaderManager().getProgram(OverlayRenderer.PROGRAM);
                    if (program == null) fail("overlay shader did not compile/link");
                    else log("overlay shader ok");
                    mc.setScreen(new DrConfigScreen(mc.screen));
                    stage = 1;
                }
                case 1 -> {
                    if (!(mc.screen instanceof DrConfigScreen)) fail("config screen did not open");
                    else log("config screen ok");
                    stage = 2;
                }
                default -> {
                    if (++waitTicks < 5) return;
                    if (failures.isEmpty()) {
                        log("DRIM SELFTEST PASS");
                        Runtime.getRuntime().halt(0);
                    } else {
                        failures.forEach(f -> log("DRIM SELFTEST FAIL: " + f));
                        Runtime.getRuntime().halt(1);
                    }
                }
            }
        } catch (Throwable e) {
            fail("unexpected: " + e);
            failures.forEach(f -> log("DRIM SELFTEST FAIL: " + f));
            Runtime.getRuntime().halt(1);
        }
    }

    private static void fail(String msg) {
        failures.add(msg);
    }

    private static void log(String msg) {
        DrImprovementMod.LOGGER.info(msg);
        System.out.println(msg);
    }
}
