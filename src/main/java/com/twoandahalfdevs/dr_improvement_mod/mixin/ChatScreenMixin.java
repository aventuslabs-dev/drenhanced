package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import com.twoandahalfdevs.dr_improvement_mod.SplitChat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With chat open: scrolling, hovering and clicking on the right half act on the system chat. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void drim$scroll(double mx, double my, double sx, double sy, CallbackInfoReturnable<Boolean> cir) {
        if (!SplitChat.enabled() || mx < SplitChat.offsetX(this.width)) return;
        double d = Mth.clamp(sy, -1.0, 1.0);
        if (!hasShiftDown()) d *= 7.0;
        SplitChat.right().scrollChat((int) d);
        cir.setReturnValue(true);
    }

    @Inject(method = "getComponentStyleAt", at = @At("HEAD"), cancellable = true)
    private void drim$style(double mx, double my, CallbackInfoReturnable<Style> cir) {
        if (!SplitChat.enabled()) return;
        int x = SplitChat.offsetX(this.width);
        if (mx >= x) {
            cir.setReturnValue(SplitChat.right().getClickedComponentStyleAt(mx - x, my + DrConfig.data.rightChat.y));
        } else {
            cir.setReturnValue(this.minecraft.gui.getChat().getClickedComponentStyleAt(mx - SplitChat.leftX(), my + DrConfig.data.leftChat.y));
        }
    }

    /** The open chat is drawn by this screen: position/opacity the left box and draw the right one here too. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V"))
    private void drim$renderChats(ChatComponent chat, GuiGraphics g, int tick, int mx, int my, boolean focused, Operation<Void> original) {
        if (!SplitChat.enabled() || SplitChat.isRight(chat)) {
            original.call(chat, g, tick, mx, my, focused);
            return;
        }
        SplitChat.renderBoth(g, tick, mx, my, focused, (lx, ly) -> original.call(chat, g, tick, lx, ly, focused));
    }
}
