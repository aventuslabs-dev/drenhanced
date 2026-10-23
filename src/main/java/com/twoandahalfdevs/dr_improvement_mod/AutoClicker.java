package com.twoandahalfdevs.dr_improvement_mod;

import com.twoandahalfdevs.dr_improvement_mod.mixin.MinecraftInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.HitResult;

/**
 * Auto clicker (hold attack: swings at the set CPS) and energy guard (refuses a swing that would take your
 * DR energy under a floor, so you never hit 0 and get stuck exhausted).
 */
public final class AutoClicker {
    private AutoClicker() {}

    private static double owed;          // swings accumulated by the CPS clock
    private static boolean wasDown;

    // energy guard: learn what one swing costs from the energy drop right after it
    // From a real capture (axe, Warrior): swings cost 10.4% or 15% of the bar. Start safe, learn the real cost.
    private static float costEstimate = 0.15f;
    private static final float[] recentCosts = new float[8];
    private static int costCount;
    /** Swings let through that DR hasn't charged for yet (with ~250 ms ping, 2-3 are always in flight at 12 CPS). */
    private static final java.util.ArrayDeque<Long> inFlight = new java.util.ArrayDeque<>();
    private static long lastBlockedMsg;
    static int clicks; // swings the clicker fired (self-test)

    public static boolean enabled() {
        return DrConfig.data.autoClicker;
    }

    public static void toggle() {
        DrConfig.data.autoClicker = !DrConfig.data.autoClicker;
        DrConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6Auto clicker " + (enabled()
                ? "§aON §7(" + DrConfig.data.autoCps + " CPS, hold attack)" : "§cOFF")), true);
        }
    }

    /** Every client tick: while attack is held, swing at the configured rate (max one per tick = 20 CPS). */
    public static void tick(Minecraft mc) {
        if (AutoSkill.busy()) return;
        boolean down = mc.options.keyAttack.isDown() && mc.screen == null && mc.player != null && mc.player.isAlive();
        if (!enabled() || !down || mining(mc)) {
            owed = 0;
            wasDown = down;
            return;
        }
        if (!wasDown) owed = 0; // the press itself is your own click; the clicker takes over after it
        wasDown = true;
        owed = Math.min(1.0, owed + DrConfig.data.autoCps / 20.0);
        if (owed >= 1.0 - 1e-9) {
            owed -= 1.0;
            clicks++;
            ((MinecraftInvoker) mc).drim$startAttack();
        }
    }

    /** Holding a pickaxe on a block: that's mining, leave it alone. */
    static boolean mining(Minecraft mc) {
        return mc.player != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
            && mc.player.getMainHandItem().is(ItemTags.PICKAXES);
    }

    // ------------------------------------------------------------------ energy guard

    /** Minecraft#startAttack HEAD: false = refuse this swing. */
    public static boolean allowSwing(Minecraft mc) {
        if (AutoSkill.busy()) return false; // mid auto-skill switch: the artifact is in hand for this one tick
        if (!DrConfig.data.energyGuard || !DrState.onDr() || mining(mc)) return true;
        long now = System.currentTimeMillis();
        long lag = lagMillis(mc);
        while (!inFlight.isEmpty() && now - inFlight.peekFirst() > lag) inFlight.pollFirst();
        // what energy will really be once DR has charged the swings still on their way, and this one
        float energy = DrState.energy() - costEstimate * inFlight.size();
        if (!shouldBlock(energy, costEstimate, DrConfig.data.energyFloor / 100f)) {
            inFlight.addLast(now);
            return true;
        }
        if (mc.player != null && now - lastBlockedMsg > 1500 && !enabled()) {
            lastBlockedMsg = now;
            mc.player.displayClientMessage(Component.literal("§eLow energy §7- swing held back"), true);
        }
        return false;
    }

    /** Refuse when this swing would leave you under the floor. */
    static boolean shouldBlock(float energy, float cost, float floor) {
        return energy - cost < floor;
    }

    /** Round trip to the server plus a tick: how long a swing takes to show up in DR's energy. */
    static long lagMillis(Minecraft mc) {
        int ping = 150;
        try {
            var info = mc.getConnection() == null || mc.player == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null && info.getLatency() > 0) ping = info.getLatency();
        } catch (RuntimeException ignored) {
        }
        return Math.min(1000, ping + 100);
    }

    /**
     * DR energy update. A real drop (not regen: those come every ~50 ms in between) is one swing being charged:
     * learn its cost and take it off the in-flight list. The cost is the highest of the last 8, so heavier swings
     * (DR's 15% ones) are always covered.
     */
    public static void onEnergy(float before, float after) {
        float drop = before - after;
        if (drop < 0.03f || drop > 0.5f) return;
        if (!inFlight.isEmpty()) inFlight.pollFirst();
        recentCosts[costCount++ % recentCosts.length] = drop;
        float max = 0;
        for (int i = 0; i < Math.min(costCount, recentCosts.length); i++) max = Math.max(max, recentCosts[i]);
        costEstimate = max;
    }

    static float costEstimate() {
        return costEstimate;
    }
}
