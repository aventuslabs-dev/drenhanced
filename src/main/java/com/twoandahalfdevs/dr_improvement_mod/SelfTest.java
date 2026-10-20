package com.twoandahalfdevs.dr_improvement_mod;

import com.twoandahalfdevs.dr_improvement_mod.mixin.ChatAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

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
        "net.minecraft.client.gui.components.BossHealthOverlay",
        "net.minecraft.world.entity.player.Player",
        "net.minecraft.client.gui.components.ChatComponent",
        "net.minecraft.client.gui.screens.ChatScreen",
    };

    /** {text, expected player?} for the split-chat classifier, with "Steve", "Alex_99" and "AIbionOnline" online. */
    private static final Object[][] CHAT_CASES = {
        {"[G] Steve: anyone for t5?", true},
        {"<Steve> hello", true},
        {"[Party] Alex_99 \u00bb omw", true},
        {"Steve -> Alex_99: pst", true},
        {"FROM Steve: hi", true},
        {"[Admin] Steve: server restart soon", true},
        {"120 DMG -> Steve [2400 HP]", false},
        {"-35 HP (Steve)", false},
        {"Steve has activated The Fast", false},
        {"You have been added to the party.", false},
        {"Steves: not a name", false},
        {"+48 EXP [1205/9000]", false},
        {"\u00a7b<5R> \u00a7e\u00a7lAIbionOnline\u00a7f: s", true},
        {"\u00a7b<5R> \u00a7e\u00a7lAIbionOnline\u00a7f: \u00a7ftest", true},
        {"\u00a7aGuild Chat: \u00a7cfalse", false},
    };

    private static final List<String> failures = new ArrayList<>();
    private static int stage;
    private static int waitTicks;

    static boolean enabled() {
        return Boolean.getBoolean("drim.selftest");
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SelfTest::onTick);
    }

    private static void onTick(Minecraft mc) {
        if (stage < 3 && (mc.getOverlay() != null || mc.screen == null)) return; // still loading
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
                    java.util.List<String> names = java.util.List.of("Steve", "Alex_99", "AIbionOnline");
                    for (Object[] c : CHAT_CASES) {
                        boolean got = SplitChat.looksLikePlayerChat((String) c[0], names);
                        if (got != (Boolean) c[1]) fail("chat classifier: '" + c[0] + "' -> " + (got ? "player" : "system"));
                    }
                    log("chat classifier ok (" + CHAT_CASES.length + " cases)");
                    mc.setScreen(new DrConfigScreen(mc.screen));
                    stage = 1;
                }
                case 1 -> {
                    if (!(mc.screen instanceof DrConfigScreen)) fail("config screen did not open");
                    else log("config screen ok");
                    DrConfig.data.splitChat = true;
                    LevelSettings settings = new LevelSettings("drim-selftest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("drim-selftest", settings, new WorldOptions(5L, false, false),
                        WorldPresets::createNormalWorldDimensions, mc.screen);
                    stage = 3;
                    waitTicks = 0;
                }
                case 3 -> {
                    if (++waitTicks > 20 * 240) { fail("world did not load"); stage = 99; return; }
                    if (mc.player == null || mc.level == null || mc.screen != null) return;
                    mc.gui.getChat().clearMessages(false);
                    MinecraftServer server = mc.getSingleplayerServer();
                    for (String cmd : new String[]{"time set 6000", "gamerule doDaylightCycle false",
                            "tellraw @a {\"text\":\"120 DMG -> Skeleton [2400 HP]\",\"color\":\"red\"}",
                            "tellraw @a {\"text\":\"+48 EXP [1205/9000]\",\"color\":\"yellow\"}",
                            "tellraw @a {\"text\":\"You have found 12 Gems!\",\"color\":\"green\"}"}) {
                        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
                    }
                    String me = mc.player.getGameProfile().getName();
                    mc.player.connection.sendChat("hello from the left side");
                    mc.gui.getChat().addMessage(Component.literal("[G] " + me + ": guild chat stays left"));
                    mc.gui.getChat().addMessage(Component.literal("<" + me + "> plain player chat"));
                    SplitChat.onSent("typed by me");
                    mc.gui.getChat().addMessage(Component.literal("\u00a7b<5R> \u00a7e\u00a7lSomeNick\u00a7f: typed by me"));
                    stage = 4;
                    waitTicks = 0;
                }
                case 4 -> {
                    if (++waitTicks < 60) return;
                    int l = ((ChatAccessor) mc.gui.getChat()).drim$messages().size();
                    int r = ((ChatAccessor) SplitChat.right()).drim$messages().size();
                    log("left chat: " + l + " messages, right chat: " + r + " messages");
                    if (l < 4) fail("player messages missing from left chat (" + l + ")");
                    if (r < 3) fail("system messages missing from right chat (" + r + ")");
                    Screenshot.grab(mc.gameDirectory, "drim-splitchat.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new ChatScreen(""));
                    stage = 5;
                    waitTicks = 0;
                }
                case 5 -> {
                    if (++waitTicks < 20) return;
                    Screenshot.grab(mc.gameDirectory, "drim-splitchat-open.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    stage = 99;
                    waitTicks = 0;
                }
                default -> {
                    if (++waitTicks < 20) return;
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
