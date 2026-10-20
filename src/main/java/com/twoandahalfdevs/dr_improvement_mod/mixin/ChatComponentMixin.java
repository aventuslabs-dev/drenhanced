package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.SplitChat;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes non-player messages from the vanilla chat into the right-hand system chat. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), cancellable = true)
    private void drim$route(Component message, @Nullable MessageSignature sig, @Nullable GuiMessageTag tag, CallbackInfo ci) {
        ChatComponent self = (ChatComponent) (Object) this;
        if (!SplitChat.enabled() || SplitChat.isRight(self) || self != Minecraft.getInstance().gui.getChat()) return;
        if (SplitChat.isPlayerMessage(message, sig, tag)) return;
        SplitChat.right().addMessage(message, sig, tag);
        ci.cancel();
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void drim$clear(boolean clearSentHistory, CallbackInfo ci) {
        ChatComponent self = (ChatComponent) (Object) this;
        if (!SplitChat.isRight(self) && self == Minecraft.getInstance().gui.getChat()) SplitChat.reset();
    }

    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void drim$width(CallbackInfoReturnable<Integer> cir) {
        if (SplitChat.enabled()) cir.setReturnValue(SplitChat.width((ChatComponent) (Object) this, cir.getReturnValueI()));
    }

    @Inject(method = "getHeight()I", at = @At("RETURN"), cancellable = true)
    private void drim$height(CallbackInfoReturnable<Integer> cir) {
        if (SplitChat.enabled()) cir.setReturnValue(SplitChat.height((ChatComponent) (Object) this));
    }

    @Inject(method = "getScale()D", at = @At("RETURN"), cancellable = true)
    private void drim$scale(CallbackInfoReturnable<Double> cir) {
        if (SplitChat.enabled()) cir.setReturnValue(SplitChat.box((ChatComponent) (Object) this).scale);
    }
}
