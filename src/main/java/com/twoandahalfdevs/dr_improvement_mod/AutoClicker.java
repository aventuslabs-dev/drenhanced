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
    private static float costEstimate = 0.06f; // fraction of the bar; DR swings cost a few %, refined in play
    private static float preSwing = -1;
    private static long swingAt;
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
        if (!DrConfig.data.energyGuard || !DrState.onDr() || mining(mc)) return true;
        float energy = DrState.energy();
        if (!shouldBlock(energy, costEstimate, DrConfig.data.energyFloor / 100f)) {
            preSwing = energy;
            swingAt = System.currentTimeMillis();
            return true;
        }
        long now = System.currentTimeMillis();
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

    /** DR energy update: if it dropped right after a swing we let through, that drop is the swing's cost. */
    public static void onEnergy(float before, float after) {
        if (preSwing < 0 || System.currentTimeMillis() - swingAt > 400) return;
        float drop = preSwing - after;
        preSwing = -1;
        if (drop > 0.005f && drop < 0.5f) costEstimate = costEstimate * 0.7f + drop * 0.3f;
    }

    static float costEstimate() {
        return costEstimate;
    }
}
