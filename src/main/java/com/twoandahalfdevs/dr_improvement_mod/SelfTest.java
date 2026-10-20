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
        "net.minecraft.client.OptionInstance",
        "net.minecraft.client.renderer.entity.EntityRenderer",
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
    private static net.minecraft.world.entity.Entity hpTarget;
    private static java.nio.file.Path captureFile;

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
                    else if (program.getUniform("energyScale") == null) fail("overlay shader missing placement uniforms");
                    else log("overlay shader ok");
                    java.util.List<String> names = java.util.List.of("Steve", "Alex_99", "AIbionOnline");
                    for (Object[] c : CHAT_CASES) {
                        boolean got = SplitChat.looksLikePlayerChat((String) c[0], names);
                        if (got != (Boolean) c[1]) fail("chat classifier: '" + c[0] + "' -> " + (got ? "player" : "system"));
                    }
                    log("chat classifier ok (" + CHAT_CASES.length + " cases)");
                    // enemy HP bar parser: 12 green + 8 gray pipes = 60%
                    var bar = Component.empty()
                        .append(Component.literal("||||||||||||").withStyle(net.minecraft.ChatFormatting.GREEN))
                        .append(Component.literal("||||||||").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(Component.literal(" [Lv 90]"));
                    var parsed = EnemyHp.parse(bar);
                    if (parsed == null || parsed.filled() != 12 || parsed.total() != 20 || !"[Lv 90]".equals(parsed.after()))
                        fail("enemy hp parse (components): " + (parsed == null ? "null" : EnemyHp.describe(parsed)));
                    var legacy = EnemyHp.parse(Component.literal("\u00a7a|||||\u00a77|||||||||||||||\u00a7f [Lv 12]"));
                    if (legacy == null || legacy.filled() != 5 || legacy.total() != 20)
                        fail("enemy hp parse (legacy): " + (legacy == null ? "null" : EnemyHp.describe(legacy)));
                    if (EnemyHp.parse(Component.literal("Rotten Zombie [Lv 91]")) != null) fail("plain name parsed as hp bar");
                    var parts = MobHp.split(Component.empty()
                        .append(Component.literal("Spooky Skeleton").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD))
                        .append(Component.literal(" [Lv 90]")));
                    if (parts == null || !"Spooky Skeleton".equals(parts.name().getString()) || !"90".equals(parts.level()) || !parts.elite())
                        fail("nameplate split: " + (parts == null ? "null" : parts.name().getString() + "|" + parts.level() + "|" + parts.elite()));
                    var legacyParts = MobHp.split(Component.literal("\u00a7aGoblin Scout \u00a77[Lv 12]"));
                    if (legacyParts == null || legacyParts.elite() || !"Goblin Scout".equals(legacyParts.name().getString()))
                        fail("nameplate split (legacy)");
                    if (MobHp.split(Component.literal("Shop Keeper")) != null) fail("NPC name treated as mob");
                    DrState.setPlayerLevel(90);
                    log("enemy hp parser ok -> " + EnemyHp.render(parsed, EnemyHp.Style.BAR_PERCENT, 12, true).getString());
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
                    log("capture: " + Capture.toggle().getString());
                    MinecraftServer server = mc.getSingleplayerServer();
                    for (String cmd : new String[]{"time set 6000", "gamerule doDaylightCycle false",
                            "tellraw @a {\"text\":\"120 DMG -> Skeleton [2400 HP]\",\"color\":\"red\"}",
                            "tellraw @a {\"text\":\"+48 EXP [1205/9000]\",\"color\":\"yellow\"}",
                            "tellraw @a {\"text\":\"You have found 12 Gems!\",\"color\":\"green\"}",
                            "execute at @a run summon villager ~-2 ~ ~5 {Tags:[\"drimA\"],NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"Spooky Skeleton\",\"color\":\"red\",\"bold\":true},{\"text\":\" [Lv 90]\",\"color\":\"white\"}]'}",
                            "execute at @a run summon villager ~0.3 ~ ~8 {NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"Rotten Zombie\",\"color\":\"white\"},{\"text\":\" [Lv 95]\",\"color\":\"gray\"}]'}",
                            "execute at @a run summon villager ~2 ~ ~5 {Tags:[\"drimB\"],NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"||||||||||||||\",\"color\":\"green\"},{\"text\":\"||||||\",\"color\":\"gray\"},{\"text\":\" [Lv 91]\",\"color\":\"white\"}]'}"}) {
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
                    if (waitTicks == 20) {
                        // DR action bar: ultimate + potion cooldowns, and a vanilla item cooldown on a hotbar item
                        mc.gui.setOverlayMessage(Component.literal("\u00a7cThe Fast: [32s]   \u00a7f[2/5] Potions: [5s]"), false);
                        mc.player.getInventory().items.set(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 8));
                        mc.player.getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), 200);
                    }
                    if (++waitTicks == 30) {
                        // hit the full-health "Spooky Skeleton": DR then shows the bar and a damage line
                        for (var e : mc.level.entitiesForRendering()) {
                            if (e.getCustomName() != null && e.getCustomName().getString().contains("Spooky Skeleton")) hpTarget = e;
                        }
                        if (hpTarget == null) fail("test mob not found");
                        else {
                            MobHp.onAttack(hpTarget);
                            MobHp.onChat("\u00a7c120 DMG -> Spooky Skeleton [2280 HP]");
                            MinecraftServer srv = mc.getSingleplayerServer();
                            String cmd = "data merge entity @e[tag=drimA,limit=1] {CustomName:'[{\"text\":\"|||||||||||||||||||\",\"color\":\"green\"},{\"text\":\"|\",\"color\":\"gray\"},{\"text\":\" [Lv 90]\",\"color\":\"white\"}]'}";
                            srv.execute(() -> srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd));
                        }
                    }
                    if (waitTicks == 50) {
                        // DR-style damage holograms: one by the mob, a heal above the player
                        MinecraftServer srv = mc.getSingleplayerServer();
                        for (String cmd : new String[]{
                                "execute at @a run summon armor_stand ~-2 ~2.3 ~5 {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:'{\"text\":\"-1074 \u2764\",\"color\":\"red\"}'}",
                                "execute at @a run summon armor_stand ~0.5 ~2.2 ~1 {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:'{\"text\":\"+125 \u2764\",\"color\":\"green\"}'}"}) {
                            srv.execute(() -> srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd));
                        }
                        Capture.mark("selftest: holograms summoned");
                    }
                    if (waitTicks < 60) return;
                    int fct = FloatingText.count();
                    boolean holoHidden = false;
                    for (var e : mc.level.entitiesForRendering()) {
                        if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && e.getCustomName() != null
                            && e.getCustomName().getString().startsWith("-1074")) holoHidden = FloatingText.isHidden(e);
                    }
                    log("floating text: " + fct + " numbers on screen, DR hologram hidden=" + holoHidden);
                    if (fct < 2) fail("floating damage numbers not spawned (" + fct + ")");
                    if (!holoHidden) fail("DR damage hologram not taken over");
                    if (hpTarget != null) {
                        MobHp.Track tr = MobHp.trackOf(hpTarget);
                        if (tr == null) fail("no hp track for test mob");
                        else {
                            log("mob hp track: " + MobHp.hpOf(tr) + " / " + MobHp.maxOf(tr) + (MobHp.maxExact(tr) ? " (exact)" : " (estimate)"));
                            if (MobHp.hpOf(tr) != 2280 || MobHp.maxOf(tr) != 2400 || !MobHp.maxExact(tr)) fail("mob hp tracking wrong");
                        }
                    }
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
                    // custom layout: smaller left text moved up, narrow right chat pulled in from the edge
                    DrConfig.data.leftChat.scale = 0.75;
                    DrConfig.data.leftChat.y = 40;
                    DrConfig.data.rightChat.width = 180;
                    DrConfig.data.rightChat.x = 20;
                    DrConfig.data.rightChat.background = 0.9;
                    SplitChat.relayout();
                    if (SplitChat.right().getWidth() != 180) fail("right chat width not applied");
                    if (Math.abs(mc.gui.getChat().getScale() - 0.75) > 1e-6) fail("left chat scale not applied");
                    stage = 6;
                    waitTicks = 0;
                }
                case 6 -> {
                    if (++waitTicks < 10) return;
                    Screenshot.grab(mc.gameDirectory, "drim-chat-custom-open.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new ChatLayoutScreen(null));
                    stage = 7;
                    waitTicks = 0;
                }
                case 7 -> {
                    if (++waitTicks < 15) return;
                    if (!(mc.screen instanceof ChatLayoutScreen)) fail("chat layout screen did not open");
                    else log("chat layout screen ok");
                    Screenshot.grab(mc.gameDirectory, "drim-chat-layout-screen.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new EnemyHpScreen(null));
                    stage = 8;
                    waitTicks = 0;
                }
                case 8 -> {
                    if (++waitTicks < 15) return;
                    if (!(mc.screen instanceof EnemyHpScreen)) fail("enemy hp screen did not open");
                    Screenshot.grab(mc.gameDirectory, "drim-enemyhp-screen.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new CooldownScreen(null));
                    stage = 9;
                    waitTicks = 0;
                }
                case 9 -> {
                    if (++waitTicks < 15) return;
                    if (!(mc.screen instanceof CooldownScreen)) fail("cooldown screen did not open");
                    else log("cooldown screen ok");
                    Screenshot.grab(mc.gameDirectory, "drim-cooldown-screen.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    // moved / resized bars: energy bigger and left, mana up, HP hidden
                    DrConfig.data.energyBar.scale = 1.6;
                    DrConfig.data.energyBar.x = -20;
                    DrConfig.data.manaBar.y = 12;
                    DrConfig.data.manaBar.scale = 2.0;
                    DrConfig.data.healthBar.enabled = false;
                    mc.setScreen(new BarsScreen(null));
                    stage = 10;
                    waitTicks = 0;
                }
                case 10 -> {
                    if (++waitTicks < 15) return;
                    if (!(mc.screen instanceof BarsScreen)) fail("bars screen did not open");
                    else log("bars screen ok");
                    Screenshot.grab(mc.gameDirectory, "drim-bars-screen.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(null);
                    stage = 11;
                    waitTicks = 0;
                }
                case 11 -> {
                    if (++waitTicks < 10) return;
                    Screenshot.grab(mc.gameDirectory, "drim-bars-moved.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    captureFile = Capture.file();
                    log("capture: " + Capture.toggle().getString());
                    stage = 12;
                    waitTicks = 0;
                }
                case 12 -> {
                    if (++waitTicks < 10) return;
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(captureFile);
                        log("capture file: " + lines.size() + " lines");
                        for (String type : new String[]{"| CHAT |", "| ROUTE |", "| NAME |", "| ACTIONBAR |", "| MARK |"}) {
                            if (lines.stream().noneMatch(l -> l.contains(type))) fail("capture has no " + type + " lines");
                        }
                        lines.stream().limit(60).forEach(l -> log("CAPTURE> " + l));
                    } catch (Exception e) {
                        fail("capture file unreadable: " + e);
                    }
                    DrConfig.data.energyBar = new DrConfig.Bar();
                    DrConfig.data.manaBar = new DrConfig.Bar();
                    DrConfig.data.healthBar = new DrConfig.Bar();
                    DrConfig.data.leftChat = new DrConfig.ChatBox();
                    DrConfig.data.rightChat = new DrConfig.ChatBox();
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
