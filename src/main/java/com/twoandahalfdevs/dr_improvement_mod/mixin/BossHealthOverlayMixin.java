package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import com.twoandahalfdevs.dr_improvement_mod.DrState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The "<Class> Lv X - HP a/b" boss bar: record the class and hide the bar (its text stays, since
 * vanilla draws the name outside drawBar). Every bar is pushed down 14px per position to make
 * room for the shader HP bar. Uses per-call offsets instead of local-variable indices so it
 * doesn't depend on how javac laid out render().
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Unique private static final int EXTRA_SPACING = 14;
    @Unique private int drim$barIndex;

    @Unique
    private int drim$offset() {
        return DrConfig.customBars() ? drim$barIndex * EXTRA_SPACING : 0;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void drim$resetIndex(GuiGraphics guiGraphics, CallbackInfo ci) {
        drim$barIndex = 0;
    }

    @ModifyArg(method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V"),
        index = 2)
    private int drim$shiftBar(int y) {
        return y + drim$offset();
    }

    @ModifyArg(method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"),
        index = 3)
    private int drim$shiftName(int y) {
        int shifted = y + drim$offset();
        drim$barIndex++;
        return shifted;
    }

    @Inject(method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",
        at = @At("HEAD"), cancellable = true)
    private void drim$hideHpBar(GuiGraphics guiGraphics, int x, int y, BossEvent event, CallbackInfo ci) {
        com.twoandahalfdevs.dr_improvement_mod.Capture.boss(event.getName());
        String plain = event.getName().getString();
        if (plain.contains("Lv ") && plain.contains(" - HP ")) {
            int dash = plain.indexOf('-');
            if (dash > 0) {
                DrState.setPlayerClass(plain.substring(0, dash).trim());
            }
            DrState.onClassBar(plain);
            if (DrConfig.customBars()) {
                ci.cancel();
            }
        }
    }
}
