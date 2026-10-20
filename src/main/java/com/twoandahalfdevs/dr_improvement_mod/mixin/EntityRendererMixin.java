package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.twoandahalfdevs.dr_improvement_mod.EnemyHp;
import com.twoandahalfdevs.dr_improvement_mod.MobHp;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DungeonRealms mob health bars: text restyles, or the WoW-style nameplate drawn by MobHp. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void drim$enemyHp(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component c = cir.getReturnValue();
        Component t = EnemyHp.transform(c);
        if (t != c) cir.setReturnValue(t);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void drim$extractHp(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        ((MobHp.Holder) state).drim$setHp(MobHp.update(entity, state.nameTag));
    }

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void drim$renderHp(EntityRenderState state, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        MobHp.Display d = ((MobHp.Holder) state).drim$hp();
        if (d == null) return;
        MobHp.render(d, state.nameTagAttachment, pose, buffers, light);
        ci.cancel();
    }
}
