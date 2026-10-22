package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Visual-only dual wield: your offhand is drawn holding a copy of your main-hand weapon, and your attack swings
 * alternate right / left. Nothing changes on the server: your real offhand is untouched and the server still
 * gets a normal main-hand swing, so other players see your usual one-handed swing.
 */
public final class DualWield {
    private DualWield() {}

    /** The arm the next swing uses. */
    private static boolean nextOff;

    public static boolean enabled() {
        return DrConfig.data.dualWield;
    }

    /** On, and the main hand holds something worth mirroring (not a bow / crossbow, not an empty fist). */
    public static boolean active(Player p) {
        if (!enabled() || p == null) return false;
        ItemStack main = p.getMainHandItem();
        return !main.isEmpty() && !(main.getItem() instanceof ProjectileWeaponItem);
    }

    /** Is this you (the only player whose hands we redraw)? */
    public static boolean isSelf(LivingEntity e) {
        return e != null && e == Minecraft.getInstance().player;
    }

    /** What your offhand is drawn holding. */
    public static ItemStack offhandShown(Player p) {
        return active(p) ? p.getMainHandItem() : p.getOffhandItem();
    }

    /** Arm for the next swing animation. */
    public static InteractionHand nextHand() {
        return nextOff ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** A swing really started on {@link #nextHand()}: the one after goes to the other arm. */
    public static void swung() {
        nextOff = !nextOff;
    }

    public static void toggle() {
        DrConfig.data.dualWield = !DrConfig.data.dualWield;
        DrConfig.save();
        nextOff = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6Dual wield " + (enabled() ? "§aON" : "§cOFF")), true);
        }
    }
}
