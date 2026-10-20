package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.ClientEvents;
import com.twoandahalfdevs.dr_improvement_mod.SplitChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 54.x has no RenderGuiEvent any more, so hook the HUD render directly. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void drim$hudBelow(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientEvents.renderHudBelow();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void drim$hudAbove(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientEvents.renderHudAbove(guiGraphics);
    }

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void drim$leftChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (SplitChat.enabled()) SplitChat.beginLeft(guiGraphics);
    }

    /** Draw the right-hand system chat right after the vanilla one, with the same visibility rules. */
    @Inject(method = "renderChat", at = @At("TAIL"))
    private void drim$rightChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!SplitChat.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        var w = mc.getWindow();
        int mx = (int) (mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight());
        SplitChat.endLeftAndRenderRight(guiGraphics, ((Gui) (Object) this).getGuiTicks(), mx, my);
    }
}
