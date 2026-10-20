package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Full bright via a gamma override: no potion effect, works with Sodium, never written to options.txt. */
public final class FullBright {
    private FullBright() {}

    /** Vanilla's slider tops out at 1.0 ("Bright"); 16 makes every block fully lit. */
    public static final double GAMMA = 16.0;
    /** True while Options#save runs, so the real gamma is what gets saved. */
    public static boolean saving;

    public static void toggle() {
        DrConfig.data.fullbright = !DrConfig.data.fullbright;
        DrConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("\u00a76Full bright " + (DrConfig.data.fullbright ? "\u00a7aON" : "\u00a7cOFF")), true);
        }
    }
}
