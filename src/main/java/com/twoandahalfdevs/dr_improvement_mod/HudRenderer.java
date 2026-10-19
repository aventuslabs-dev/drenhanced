package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** "Useful info" text around the crosshair: ultimate cooldown, potions, combat + bonus timers. */
public final class HudRenderer {
    private HudRenderer() {}

    private static final int X_OFFSET = 8;
    private static final int X_OFFSET_BOTTOM = 12;
    private static final int Y_ABILITY_POTS = -34;
    private static final int Y_COMBAT = 27;
    private static final int TEXT_COLOR = 0xB4FFFFFF;

    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;

        // GUI-scale aware centre (the original assumed GUI scale 2).
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        long now = System.currentTimeMillis();

        // Ultimate cooldown
        String cdStr = "§aReady";
        if (DrState.ultCd != null) {
            long left = DrState.ultCd - (now - DrState.ultCdStamp) / 1000;
            if (left > 0 || now - DrState.ultCdStamp < 1000) {
                cdStr = (DrState.ultActive ? "§a" : "§c") + Math.max(0, left) + "s";
            }
        }
        g.drawString(font, cdStr, cx - font.width(cdStr) - X_OFFSET, cy + Y_ABILITY_POTS, TEXT_COLOR);

        // Potions: usable now / carried
        int carried = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(Items.POTION) || s.is(Items.SPLASH_POTION)) carried++;
        }
        int usable = Math.min(DrState.pots, carried);
        String color = usable >= 3 ? "§a" : usable >= 1 ? "§e" : "§c";
        StringBuilder pots = new StringBuilder(color).append(usable).append(" / ").append(carried);
        if (DrState.potCd != null) {
            long left = Math.max(0, DrState.potCd - (now - DrState.potCdStamp) / 1000);
            pots.append(" §7(").append(left).append("s)");
        }
        g.drawString(font, pots.toString(), cx + X_OFFSET, cy + Y_ABILITY_POTS, TEXT_COLOR);

        // Combat timer (+ Rogue bonus window)
        double combat = DrState.combatRemaining();
        boolean rogue = DrState.isRogue();
        String combatStr = combat > 0 ? "§c" + oneDecimalUp(combat) + "s" : "§a:)";
        int combatW = font.width(combatStr);
        g.drawString(font, combatStr, cx - (rogue ? combatW + X_OFFSET_BOTTOM : combatW / 2), cy + Y_COMBAT, TEXT_COLOR);

        if (rogue) {
            double bonus = DrState.bonusRemaining();
            String bonusStr = combat <= 0 ? "§a(:" : bonus > 0 ? "§a" + oneDecimalUp(bonus) + "s" : "§c):";
            g.drawString(font, bonusStr, cx + X_OFFSET_BOTTOM, cy + Y_COMBAT, TEXT_COLOR);
        }
    }

    private static double oneDecimalUp(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.UP).doubleValue();
    }
}
