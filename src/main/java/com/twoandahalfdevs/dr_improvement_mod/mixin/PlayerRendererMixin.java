package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.twoandahalfdevs.dr_improvement_mod.MobHp;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players draw their below-name score ("HP") and name through two super calls; draw our plate once instead,
 * which also replaces the score line (the plate shows the HP itself).
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true)
    private void drim$playerPlate(PlayerRenderState state, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        MobHp.Display d = ((MobHp.Holder) state).drim$hp();
        if (d == null) return;
        MobHp.render(d, state.nameTagAttachment, pose, buffers, light);
        ci.cancel();
    }
}
