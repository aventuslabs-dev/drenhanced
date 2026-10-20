package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import com.twoandahalfdevs.dr_improvement_mod.SplitChat;
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
}
