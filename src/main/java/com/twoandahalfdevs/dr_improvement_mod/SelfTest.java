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

    /** {DR mining line, expected popup kind or null}. */
    private static final Object[][] ORE_CASES = {
        {"+48 EXP [1205/9000 EXP]", OreText.Kind.EXP},
        {"\u00a7e\u00a7l          DOUBLE ORE DROP\u00a7e (2x)", OreText.Kind.DOUBLE},
        {"          TRIPLE ORE DROP (3x)", OreText.Kind.TRIPLE},
        {"FOUND 3 GEM(S)", OreText.Kind.GEM},
        {"You have found 12 Gems!", OreText.Kind.GEM},
        {"Ore respawned!", OreText.Kind.RESPAWN},
        {"Your pickaxe has leveled up to level 24!", OreText.Kind.LEVEL},
        {"Durability saved!", OreText.Kind.DURABILITY},
        {"TREASURE FIND!", OreText.Kind.TREASURE},
        {"You failed to mine this ore.", OreText.Kind.FAIL},
        {"120 DMG -> Skeleton [2400 HP]", null},
        {"Large Gem Pouch 30g", null},
        {"-35 HP (Steve)", null},
    };

    private static final List<String> failures = new ArrayList<>();
    private static int stage;
    private static int waitTicks;
    private static net.minecraft.world.entity.Entity hpTarget;
    private static net.minecraft.world.entity.Entity barMob;
    private static int maxCrits, maxThorns, holdTicks, holdTicks2;
    private static java.nio.file.Path captureFile;
    private static net.minecraft.world.entity.Entity rivalPlayer;

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
                    // full bright: gamma reads high while on, the real value is what gets saved
                    double realGamma = mc.options.gamma().get();
                    DrConfig.data.fullbright = true;
                    if (mc.options.gamma().get() != FullBright.GAMMA) fail("full bright gamma not applied");
                    mc.options.save();
                    String saved = java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("options.txt"));
                    if (saved.contains("gamma:16")) fail("full bright gamma leaked into options.txt");
                    DrConfig.data.fullbright = false;
                    if (mc.options.gamma().get() != realGamma) fail("gamma not restored");
                    log("full bright ok (real gamma " + realGamma + ")");
                    java.util.List<String> names = java.util.List.of("Steve", "Alex_99", "AIbionOnline");
                    for (Object[] c : CHAT_CASES) {
                        boolean got = SplitChat.looksLikePlayerChat((String) c[0], names);
                        if (got != (Boolean) c[1]) fail("chat classifier: '" + c[0] + "' -> " + (got ? "player" : "system"));
                    }
                    log("chat classifier ok (" + CHAT_CASES.length + " cases)");
                    for (Object[] c : ORE_CASES) {
                        OreText.Event e = OreText.classify((String) c[0]);
                        Object got = e == null ? null : e.kind();
                        if (got != c[1]) fail("ore parser: '" + c[0] + "' -> " + got + " (want " + c[1] + ")");
                    }
                    var xp = OreText.classify("+48 EXP [1205/9000 EXP]");
                    if (xp == null || xp.amount() != 48 || xp.cur() != 1205 || xp.max() != 9000) fail("ore parser: EXP numbers " + xp);
                    log("ore parser ok (" + ORE_CASES.length + " cases)");
                    for (String id : new String[]{"sodium", "immediatelyfast", "lithium", "entityculling"})
                        log(id + " loaded: " + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id));
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
                    // DR affix icon is bold but the mob isn't elite; a bold name is
                    var affix = MobHp.split(Component.empty()
                        .append(Component.literal("\u2744 ").withStyle(net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.BOLD))
                        .append(Component.literal("Dwarven War Golem").withStyle(net.minecraft.ChatFormatting.YELLOW))
                        .append(Component.literal(" [").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(Component.literal("Lv 91").withStyle(net.minecraft.ChatFormatting.GREEN))
                        .append(Component.literal("]").withStyle(net.minecraft.ChatFormatting.GRAY)));
                    if (affix == null || affix.elite() || !"91".equals(affix.level())) fail("affix mob flagged elite / level wrong");
                    var drBar = EnemyHp.parse(Component.empty()
                        .append(Component.literal("\u258c").withStyle(net.minecraft.ChatFormatting.GREEN))
                        .append(Component.literal("||||||||||||||||||||||||||||").withStyle(net.minecraft.ChatFormatting.GREEN))
                        .append(Component.literal("||").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(Component.literal("\u258c").withStyle(net.minecraft.ChatFormatting.GREEN))
                        .append(Component.literal(" [Lv 92]").withStyle(net.minecraft.ChatFormatting.GRAY)));
                    if (drBar == null || drBar.filled() != 28 || drBar.total() != 30 || !"[Lv 92]".equals(drBar.after()))
                        fail("DR 30-pipe bar: " + (drBar == null ? "null" : EnemyHp.describe(drBar)));
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
                            // a clear stage so screenshots don't depend on where the random world spawned us
                            "execute at @a run fill ~-9 ~ ~-4 ~9 ~7 ~14 air",
                            "execute at @a run fill ~-9 ~-1 ~-4 ~9 ~-1 ~14 smooth_stone",
                            // face the test mobs (+z) so they're on screen and their plates get tracked
                            "execute as @a at @s run tp @s ~ ~ ~ 0 5",
                            "tellraw @a {\"text\":\"120 DMG -> Skeleton [2400 HP]\",\"color\":\"red\"}",
                            "tellraw @a {\"text\":\"+48 EXP [1205/9000]\",\"color\":\"yellow\"}",
                            "tellraw @a {\"text\":\"You have found 12 Gems!\",\"color\":\"green\"}",
                            "execute at @a run summon villager ~-2 ~ ~5 {UUID:[I;0,0,0,1001],Tags:[\"drimA\"],NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"Spooky Skeleton\",\"color\":\"red\",\"bold\":true},{\"text\":\" [Lv 90]\",\"color\":\"white\"}]'}",
                            "execute at @a run summon villager ~-3.5 ~ ~6 {Tags:[\"drimC\"],NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"Spooky Skeleton\",\"color\":\"red\",\"bold\":true},{\"text\":\" [Lv 90]\",\"color\":\"white\"}]'}",
                            "execute at @a run summon villager ~-5 ~ ~7 {Tags:[\"drimD\"],NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"\u2668 \",\"color\":\"red\",\"bold\":true},{\"text\":\"Spooky Skeleton\",\"color\":\"yellow\"},{\"text\":\" [Lv 90]\",\"color\":\"white\"}]'}",
                            "execute at @a run summon villager ~0.3 ~ ~8 {NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"Rotten Zombie\",\"color\":\"white\"},{\"text\":\" [Lv 95]\",\"color\":\"gray\"}]'}",
                            "execute at @a run summon villager ~2 ~ ~5 {Tags:[\"drimB\"],NoAI:1b,Silent:1b,CustomNameVisible:1b,CustomName:'[{\"text\":\"||||||||||||||\",\"color\":\"green\"},{\"text\":\"||||||\",\"color\":\"gray\"},{\"text\":\" [Lv 91]\",\"color\":\"white\"}]'}"}) {
                        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
                    }
                    String me = mc.player.getGameProfile().getName();
                    mc.player.connection.sendChat("hello from the left side");
                    mc.gui.getChat().addMessage(Component.literal("[G] " + me + ": guild chat stays left"));
                    mc.gui.getChat().addMessage(Component.literal("<" + me + "> plain player chat"));
                    for (int i = 0; i < 3; i++) mc.gui.getChat().addMessage(Component.literal("You must wait before consuming another potion."));
                    SplitChat.onSent("typed by me");
                    mc.gui.getChat().addMessage(Component.literal("\u00a7b<5R> \u00a7e\u00a7lSomeNick\u00a7f: typed by me"));
                    stage = 4;
                    waitTicks = 0;
                }
                case 4 -> {
                    if (waitTicks == 18) {
                        // a "real" enemy player (client-side stand-in): chaotic red, guild [5R], real HP 1,500 / 2,500
                        var sb = mc.level.getScoreboard();
                        var team = sb.addPlayerTeam("drimtest");
                        team.setPlayerPrefix(Component.literal("[5R] ").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                        team.setColor(net.minecraft.ChatFormatting.RED);
                        team.setPlayerSuffix(Component.literal(" [Lv 103 ROG]").withStyle(net.minecraft.ChatFormatting.AQUA));
                        sb.addPlayerToTeam("Rival", team);
                        var obj = sb.addObjective("health", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, Component.literal("HP"),
                            net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
                        sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.BELOW_NAME, obj);
                        sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("Rival"), obj).set(1500);
                        var rival = new net.minecraft.client.player.RemotePlayer(mc.level,
                            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rival"));
                        var pp = mc.player.position();
                        rival.setPos(pp.x + 5.5, pp.y, pp.z + 9);
                        rival.setHealth(12f);
                        mc.level.addEntity(rival);
                        MobHp.testPlayers.add(rival.getId());
                        HealthSync.setMaxForTest("Rival", 2500);
                        rivalPlayer = rival;
                    }
                    if (waitTicks == 20) {
                        // DR action bar: ultimate + potion cooldowns, and a vanilla item cooldown on a hotbar item
                        // DR's current action bar: coloured parts joined by "▪" (from a real capture)
                        mc.gui.setOverlayMessage(Component.empty()
                            .append(Component.literal("The Fast: [7s]").withStyle(net.minecraft.ChatFormatting.GREEN))
                            .append(Component.literal(" \u25aa ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                            .append(Component.literal("Dash [4s]").withStyle(net.minecraft.ChatFormatting.RED))
                            .append(Component.literal(" \u25aa ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                            .append(Component.literal("MONSTER SLAYER [1m 59s]").withStyle(net.minecraft.ChatFormatting.GOLD))
                            .append(Component.literal(" \u25aa ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                            .append(Component.literal("[4/5] Potions: [59s]").withStyle(net.minecraft.ChatFormatting.RED))
                            .append(Component.literal(" \u25aa ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                            .append(Component.literal("PvM Combat: [3s]").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)), false);
                        mc.player.getInventory().items.set(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 8));
                        mc.player.getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), 200);
                    }
                    // wait (up to 10 s) until the test mob's plate has been drawn once, so its track exists
                    if (waitTicks == 29 && holdTicks < 200) {
                        boolean drawn = false;
                        for (var e : mc.level.entitiesForRendering()) {
                            if (e.getUUID().equals(new java.util.UUID(0L, 1001L)) && MobHp.trackOf(e) != null) drawn = true;
                        }
                        if (!drawn) {
                            holdTicks++;
                            return;
                        }
                    }
                    if (++waitTicks == 30) {
                        // hit the full-health "Spooky Skeleton": DR then shows the bar and a damage line
                        for (var e : mc.level.entitiesForRendering()) {
                            // drimA: the plain "Spooky Skeleton" summoned at ~-2 ~ ~5
                            if (e.getUUID().equals(new java.util.UUID(0L, 1001L))) hpTarget = e;
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
                                "execute at @a run summon text_display ~-2 ~2.3 ~5 {billboard:\"center\",text:'{\"text\":\"-1074 \u2764\",\"color\":\"red\"}'}",
                                "execute at @a run summon armor_stand ~0.5 ~2.2 ~1 {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:'{\"text\":\"+125 \u2764\",\"color\":\"green\"}'}"}) {
                            srv.execute(() -> srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd));
                        }
                        Capture.mark("selftest: holograms summoned");
                    }
                    if (waitTicks == 40) {
                        // DR combat-log lines exactly as captured, for the WoW-style feed
                        for (String line : new String[]{
                                "                                * CRIT *",
                                "                           * SHATTERED *",
                                "1105 DMG -> Fiendish Skeleton [6238 HP]",
                                "523 DMG -> Fiendish Skeleton [5715 HP]",
                                "36 DMG (Thorns) -> Fiendish Skeleton [5679 HP]",
                                "+96 HP (Leeching Poison) [620 Overhealth]",
                                "+130 HP (Life Steal) [750 Overhealth]",
                                "+11 HP (Healing) [761 Overhealth]",
                                "-45 HP (Fiendish Skeleton's Thorns) [22937 HP]",
                                "   + 48 EXP (EXP BUFF) [1205/9000]",
                                "                                   99G",
                                "\u2576\u2576\u2576\u2576\u25c3\u25c3\u25ad\u25ad\u2b4b\u2662\u25b9\u25b9\u2576\u2576\u2576\u2576",
                                "Beacon Buff: +17% vs. MONSTERS (2m)"}) {
                            mc.gui.getChat().addMessage(Component.literal(line));
                        }
                        for (String heal : new String[]{"+96 HP (Leeching Poison) [620 Overhealth]", "+130 HP (Life Steal) [750 Overhealth]"})
                            DrState.onChat(heal); // server chat reaches the mod through DrState.onChat
                        mc.gui.getChat().addMessage(Component.empty().append(Component.literal("Fire Imp").withStyle(net.minecraft.ChatFormatting.YELLOW))
                            .append(Component.literal(" dropped: ").withStyle(net.minecraft.ChatFormatting.GREEN))
                            .append(Component.literal("SHOW").withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD)));
                    }
                    if (waitTicks < 60) return;
                    int fct = FloatingText.count();
                    boolean holoHidden = false;
                    for (var e : mc.level.entitiesForRendering()) {
                        Component dt = FloatingText.displayText(e);
                        if (dt != null && dt.getString().startsWith("-1074")) holoHidden = FloatingText.isHidden(e);
                    }
                    if (rivalPlayer != null) {
                        if (MobHp.trackOf(rivalPlayer) == null) fail("no plate for the test player");
                        else log("player plate ok");
                    }
                    // action bar abilities + DR's own combat timer
                    var fast = DrState.abilities.get("The Fast");
                    var dash = DrState.abilities.get("Dash");
                    double combat = DrState.combatRemaining();
                    log("abilities: " + DrState.abilities.keySet() + " combat=" + combat);
                    if (fast == null || !fast.active() || dash == null || dash.active()) fail("action bar abilities not parsed: " + DrState.abilities);
                    if (combat <= 0 || combat > 3.01) fail("server combat timer not used: " + combat);
                    if (!DrState.buffs.containsKey("MONSTER SLAYER") || DrState.knownAbilities.contains("MONSTER SLAYER"))
                        fail("buff not separated from abilities: " + DrState.buffs.keySet() + " / " + DrState.knownAbilities);
                    if (DrState.pots != 4 || DrState.potsMax != 5 || DrState.potCd == null || DrState.potCd != 59)
                        fail("potions not parsed: " + DrState.pots + "/" + DrState.potsMax + " cd " + DrState.potCd);
                    // sub-second timers: DR rounds down, so "[3s]" = 3.0-3.99 s left, and an off-tick message narrows it
                    Countdown.observe("t:dash", 3, 1, 10_000);     // regular tick
                    double r1 = Countdown.remaining("t:dash", 10_000);
                    Countdown.observe("t:dash", 2, 1, 10_700);     // event message 0.7 s later still says 2
                    double r2 = Countdown.remaining("t:dash", 10_700), w2 = Countdown.uncertainty("t:dash");
                    Countdown.observe("t:dash", 7, 1, 20_000);     // used again: new window
                    double r3 = Countdown.remaining("t:dash", 20_000);
                    log(String.format(java.util.Locale.ROOT, "countdown: %.2f, narrowed %.2f (window %.2fs), restarted %.2f", r1, r2, w2, r3));
                    if (r1 < 3.0 || r1 > 4.0 || r2 < 2.0 || r2 > 2.8 || w2 > 1.0 || r3 < 7.0 || r3 > 8.0) fail("countdown estimate off");
                    Countdown.forget("t:dash");
                    // class switch: Rogue abilities must not follow you onto a Warrior, and come back on Rogue
                    var sep = Component.literal(" \u25aa ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
                    DrState.setPlayerClass("Lv 101 Rogue");
                    DrState.onActionBar(Component.empty().append(Component.literal("The Fast: [7s]").withStyle(net.minecraft.ChatFormatting.GREEN))
                        .append(sep).append(Component.literal("Dash [4s]").withStyle(net.minecraft.ChatFormatting.RED)), 60);
                    DrState.setPlayerClass("Lv 101 Warrior");
                    boolean clearedOnSwitch = DrState.knownAbilities.isEmpty();
                    DrState.onActionBar(Component.literal("Berserk: [15s]").withStyle(net.minecraft.ChatFormatting.RED), 60);
                    String warrior = String.valueOf(DrState.knownAbilities);
                    DrState.setPlayerClass("Lv 101 Rogue");
                    log("class switch: warrior " + warrior + ", back to rogue " + DrState.knownAbilities);
                    if (!clearedOnSwitch || !warrior.equals("[Berserk]")) fail("abilities not switched with the class: " + warrior);
                    if (!DrConfig.abilityHidden("Smite") || !DrConfig.abilityHidden("smite")) fail("Smite not hidden by default");
                    if (!DrState.knownAbilities.contains("The Fast") || DrState.knownAbilities.contains("Berserk"))
                        fail("rogue abilities not restored: " + DrState.knownAbilities);
                    if (rivalPlayer != null) {
                        var pd = MobHp.update(rivalPlayer, rivalPlayer.getDisplayName());
                        if (pd == null || !"103".equals(pd.level()) || pd.sub() == null || !pd.sub().getString().contains("Rogue"))
                            fail("player level/class not read: " + (pd == null ? "null" : pd.level() + " " + pd.sub()));
                        else log("player plate: Lv " + pd.level() + " " + pd.sub().getString());
                        if (pd != null && pd.barColor() != MobHp.PLAYER_GREEN) fail("player bar not health green");
                        // guild badge: Rival is in [5R]; neutral until marked, then enemy/ally
                        String g0 = pd == null ? null : pd.guild() + "/" + pd.guildRel();
                        Guilds.mark("5R", Guilds.Rel.ENEMY);
                        var pe = MobHp.update(rivalPlayer, rivalPlayer.getDisplayName());
                        Guilds.mark("5r", Guilds.Rel.ALLY);
                        var pa = MobHp.update(rivalPlayer, rivalPlayer.getDisplayName());
                        log("guild badge: " + g0 + ", marked enemy " + (pe == null ? null : pe.guildRel()) + ", ally " + (pa == null ? null : pa.guildRel()));
                        if (!"5R/NEUTRAL".equals(g0) || pe == null || pe.guildRel() != Guilds.Rel.ENEMY || pa == null || pa.guildRel() != Guilds.Rel.ALLY)
                            fail("guild relation wrong");
                        Guilds.mark("5R", Guilds.Rel.ENEMY); // leave it red for the screenshot
                        // Rival's team is RED: chaotic name; alignment mapping for DR's three colours
                        if (pd != null && (pd.label().getStyle().getColor() == null || pd.label().getStyle().getColor().getValue() != MobHp.CHAOTIC))
                            fail("chaotic player name not red: " + pd.label().getStyle().getColor());
                        if (MobHp.alignment(0xFFAA00) != MobHp.NEUTRAL || MobHp.alignment(0xFFFF55) != MobHp.NEUTRAL
                                || MobHp.alignment(0xAA0000) != MobHp.CHAOTIC || MobHp.alignment(0xFFFFFF) != MobHp.LAWFUL
                                || MobHp.alignment(0xAAAAAA) != MobHp.LAWFUL || MobHp.alignment(null) != MobHp.LAWFUL)
                            fail("alignment colours wrong");
                    }
                    boolean folded = ((ChatAccessor) SplitChat.right()).drim$messages().stream()
                        .anyMatch(m -> m.content().getString().endsWith("(x3)"));
                    if (!folded) fail("repeated system message not folded");
                    log("feed: " + FeedLog.size() + " entries; last hit = " + FeedLog.lastText(FeedLog.Cat.HIT)
                        + "; heal = " + FeedLog.lastText(FeedLog.Cat.HEAL));
                    if (FeedLog.count(FeedLog.Cat.HEAL) != 1) fail("heals not grouped: " + FeedLog.count(FeedLog.Cat.HEAL));
                    if (FeedLog.count(FeedLog.Cat.EXP) < 1 || FeedLog.count(FeedLog.Cat.GEMS) < 1 || FeedLog.count(FeedLog.Cat.LOOT) < 1)
                        fail("feed categories missing");
                    if (FeedLog.count(FeedLog.Cat.TAKEN) < 1) fail("damage taken missing from feed");
                    log("floating text: " + fct + " numbers on screen, DR hologram hidden=" + holoHidden);
                    if (fct < 2) fail("floating damage numbers not spawned (" + fct + ")");
                    if (!holoHidden) fail("DR damage hologram not taken over");
                    // the "-1074" hologram has no chat line of ours: another player's hit, hidden and not drawn
                    log("other players' numbers drawn: " + FloatingText.countKind(FloatingText.Kind.OTHER));
                    if (FloatingText.countKind(FloatingText.Kind.OTHER) > 0) fail("other players' damage shown");
                    log("heal numbers (from your chat only): " + FloatingText.countKind(FloatingText.Kind.HEAL));
                    if (FloatingText.countKind(FloatingText.Kind.HEAL) != 1) fail("heals should come from your chat lines only (one burst)");
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
                    if (waitTicks == 0) {
                        // a real crit: DR's "* CRIT *" line right before the damage line
                        if (hpTarget != null) MobHp.onAttack(hpTarget); // as if we just swung at it
                        DrState.onChat("                                    * CRIT *");
                        DrState.onChat("1105 DMG -> Spooky Skeleton [1175 HP]");
                        DrState.onChat("36 DMG (Thorns) -> Spooky Skeleton [1139 HP]");
                    }
                    // pending chat hits spawn after ~250 ms; CI ticks unevenly, so take the most seen at any point
                    maxCrits = Math.max(maxCrits, FloatingText.countKind(FloatingText.Kind.CRIT));
                    maxThorns = Math.max(maxThorns, FloatingText.countKind(FloatingText.Kind.THORNS));
                    if (waitTicks == 19) {
                        log("crit numbers: " + maxCrits + ", thorns numbers: " + maxThorns);
                        if (maxCrits < 1) fail("crit not shown as crit");
                        if (maxThorns < 1) fail("thorns damage not shown");
                    }
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
                    if (waitTicks == 0 && hpTarget != null) {
                        // DR swaps back to the plain name ~8 s after the last hit, while the mob is still hurt
                        MinecraftServer srv = mc.getSingleplayerServer();
                        String cmd = "data merge entity @e[tag=drimA,limit=1] {CustomName:'[{\"text\":\"Spooky Skeleton\",\"color\":\"red\",\"bold\":true},{\"text\":\" [Lv 90]\",\"color\":\"white\"}]'}";
                        srv.execute(() -> srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd));
                    }
                    if (waitTicks == 0) {
                        // a mob first seen already hurt (drimB: 14/20 pipes): max comes from the bar, not a guess
                        for (var e : mc.level.entitiesForRendering()) {
                            if (e.getCustomName() != null && e.getCustomName().getString().startsWith("|")) barMob = e;
                        }
                        if ((barMob == null || MobHp.trackOf(barMob) == null) && holdTicks2++ < 200) return; // wait until its plate was drawn
                        if (barMob != null) {
                            MobHp.onAttack(barMob);
                            DrState.onChat("50 DMG -> Rotting Thing [1400 HP]");
                        }
                    }
                    if (waitTicks == 9) {
                        MobHp.Track bt = barMob == null ? null : MobHp.trackOf(barMob);
                        log("max from bar: " + (bt == null ? "no track" : MobHp.hpOf(bt) + " / " + MobHp.maxOf(bt) + (MobHp.maxExact(bt) ? " exact" : " est")));
                        if (bt == null || MobHp.hpOf(bt) != 1400 || MobHp.maxOf(bt) < 1866 || MobHp.maxOf(bt) > 2154 || MobHp.maxExact(bt))
                            fail("max HP not learned from the bar");
                    }
                    if (waitTicks == 9 && hpTarget != null) {
                        MobHp.Track a = MobHp.trackOf(hpTarget);
                        log("after name revert: " + MobHp.hpOf(a) + " / " + MobHp.maxOf(a));
                        if (MobHp.hpOf(a) != 1139 || MobHp.maxOf(a) != 2400) fail("HP forgotten when DR reverted the name");
                        // next swing: a cleave hits the clicked mob and a second, untouched "Spooky Skeleton"
                        net.minecraft.world.entity.Entity other = null;
                        for (var e : mc.level.entitiesForRendering()) {
                            if (e != hpTarget && e.getCustomName() != null && e.getCustomName().getString().startsWith("Spooky Skeleton")) other = e;
                        }
                        MobHp.onAttack(hpTarget);
                        DrState.onChat("200 DMG -> Spooky Skeleton [939 HP]");
                        DrState.onChat("90 DMG -> Spooky Skeleton [2310 HP]");
                        log("after cleave: clicked " + MobHp.hpOf(a) + " / " + MobHp.maxOf(a)
                            + (other == null || MobHp.trackOf(other) == null ? ", other untracked"
                                : ", other " + MobHp.hpOf(MobHp.trackOf(other)) + " / " + MobHp.maxOf(MobHp.trackOf(other))));
                        if (MobHp.hpOf(a) != 939 || MobHp.maxOf(a) != 2400) fail("clicked mob wrong after cleave / max changed");
                        if (other == null || MobHp.trackOf(other) == null) fail("second mob not tracked");
                        else if (MobHp.hpOf(MobHp.trackOf(other)) != 2310 || MobHp.maxOf(MobHp.trackOf(other)) != 2400)
                            fail("cleave hit not given to the second mob");
                        // from a real capture: the axe cleave prints the neighbour's line FIRST, an affixed
                        // "♨" mob with the same base name is nearby, and Thorns arrives after the mob regenerated
                        net.minecraft.world.entity.Entity affix = null;
                        for (var e : mc.level.entitiesForRendering()) {
                            if (e.getCustomName() != null && e.getCustomName().getString().startsWith("\u2668")) affix = e;
                        }
                        MobHp.onAttack(hpTarget);
                        DrState.onChat("85 DMG -> Spooky Skeleton [2225 HP]");
                        DrState.onChat("40 DMG -> \u2668 Spooky Skeleton [2360 HP]");
                        DrState.onChat("200 DMG -> Spooky Skeleton [739 HP]");
                        DrState.onChat("30 DMG (Thorns) -> Spooky Skeleton [745 HP]"); // +36 regen since the hit
                        MobHp.Track ot = other == null ? null : MobHp.trackOf(other), ft = affix == null ? null : MobHp.trackOf(affix);
                        log("after reordered cleave: clicked " + MobHp.hpOf(a) + " / " + MobHp.maxOf(a)
                            + ", other " + (ot == null ? "?" : MobHp.hpOf(ot) + " / " + MobHp.maxOf(ot))
                            + ", affix " + (ft == null ? "?" : MobHp.hpOf(ft) + " / " + MobHp.maxOf(ft)));
                        if (MobHp.hpOf(a) != 745 || MobHp.maxOf(a) != 2400) fail("clicked mob took another mob's line (bar jumps to full)");
                        if (ot == null || MobHp.hpOf(ot) != 2225) fail("reordered cleave line not given to the neighbour");
                        if (ft == null || MobHp.hpOf(ft) != 2360 || MobHp.maxOf(ft) != 2400) fail("affixed mob line misrouted");
                    }
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
                    // DR's gem pouch (an ink sac with a custom name) gets the gold bag model
                    var pouch = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.INK_SAC);
                    pouch.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Large Gem Pouch 30g"));
                    if (ItemSkins.modelFor(pouch, net.minecraft.core.component.DataComponents.ITEM_MODEL) != ItemSkins.GEM_POUCH) fail("gem pouch not reskinned");
                    if (mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dr_improvement_mod", "textures/item/gem_pouch.png")).isEmpty())
                        fail("gem pouch texture missing");
                    mc.player.getInventory().items.set(2, pouch);
                    for (String n : new String[]{"dash", "the_fast", "berserk", "divine_protection", "deaths_grasp", "potion", "combat",
                            "bonus", "monster_slayer", "buff", "heal", "shadow", "fire", "shield", "rune"}) {
                        if (mc.getResourceManager().getResource(CooldownBar.icon(n)).isEmpty()) fail("missing cooldown icon " + n);
                    }
                    if (!CooldownBar.abilityIcon("Berserk").getPath().endsWith("berserk.png") || !CooldownBar.abilityIcon("Dash").getPath().endsWith("dash.png"))
                        fail("ability icon mapping wrong");
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
                    stage = 13;
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
                case 13 -> { // mining popups on an ore in front of us
                    ++waitTicks;
                    var feet = mc.player.blockPosition();
                    var ore = feet.offset(0, 1, 3);
                    if (waitTicks == 1) {
                        MinecraftServer server = mc.getSingleplayerServer();
                        String cmd = "setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:coal_ore";
                        // a wall between us and the ore: the cards must still show (through walls)
                        var w = ore.offset(0, 0, -1);
                        String wall = "fill " + (w.getX() - 2) + " " + w.getY() + " " + w.getZ() + " " + (w.getX() + 2) + " " + (w.getY() + 2) + " " + w.getZ() + " minecraft:stone";
                        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
                        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), wall));
                        // EXP from killing a mob (no mining going on) must not pop up
                        if (OreText.onMessage("+10 EXP [10/9000 EXP]", false) || OreText.count() != 0) fail("ore popup without mining");
                        return;
                    }
                    if (waitTicks == 8) {
                        OreText.onDig(ore, net.minecraft.core.Direction.NORTH);
                        if (!OreText.onMessage("+48 EXP [1205/9000 EXP]", false)) fail("EXP line not taken while mining");
                        OreText.onMessage("FOUND 3 GEM(S)", false);
                        OreText.onMessage("+52 EXP [1257/9000 EXP]", false); // its own card, no merging
                        // through the real packet path (mixin), including the hide-from-chat cancel
                        MinecraftServer server = mc.getSingleplayerServer();
                        for (String cmd : new String[]{
                            "tellraw @a [{\"text\":\"          DOUBLE ORE DROP\",\"color\":\"yellow\",\"bold\":true},{\"text\":\" (2x)\",\"color\":\"yellow\"}]",
                            "tellraw @a {\"text\":\"Ore respawned!\",\"color\":\"aqua\"}"}) {
                            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
                        }
                        return;
                    }
                    if (waitTicks < 22) return;
                    log("ore popups: " + OreText.count() + " (exp " + OreText.countKind(OreText.Kind.EXP) + ", gem " + OreText.countKind(OreText.Kind.GEM)
                        + ", double " + OreText.countKind(OreText.Kind.DOUBLE) + ", respawn " + OreText.countKind(OreText.Kind.RESPAWN) + ")");
                    if (OreText.countKind(OreText.Kind.EXP) != 2) fail("each EXP line should get its own card");
                    if (OreText.countKind(OreText.Kind.GEM) != 1) fail("gem popup missing");
                    if (OreText.countKind(OreText.Kind.DOUBLE) != 1) fail("DOUBLE ORE from server chat not shown (packet path)");
                    if (OreText.countKind(OreText.Kind.RESPAWN) != 1) fail("ore respawn popup missing");
                    // the popup is an addon: DR's debug line must still be in chat (left or right box)
                    java.util.function.Predicate<net.minecraft.client.GuiMessage> isDouble = g -> g.content().getString().contains("DOUBLE ORE DROP");
                    boolean inChat = ((ChatAccessor) mc.gui.getChat()).drim$messages().stream().anyMatch(isDouble)
                        || (SplitChat.right() != null && ((ChatAccessor) SplitChat.right()).drim$messages().stream().anyMatch(isDouble));
                    if (!inChat) fail("mining debug line was removed from chat (should stay by default)");
                    Screenshot.grab(mc.gameDirectory, "drim-ore-popups.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(new OrePopupScreen(null));
                    stage = 14;
                    waitTicks = 0;
                }
                case 14 -> {
                    if (++waitTicks < 12) return;
                    if (!(mc.screen instanceof OrePopupScreen)) fail("mining popup screen did not open");
                    else log("mining popup screen ok");
                    Screenshot.grab(mc.gameDirectory, "drim-ore-screen.png", mc.getMainRenderTarget(), c -> log("screenshot: " + c.getString()));
                    mc.setScreen(null);
                    stage = 12;
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
