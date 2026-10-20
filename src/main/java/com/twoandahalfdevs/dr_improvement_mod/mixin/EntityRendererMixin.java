package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.EnemyHp;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restyles DungeonRealms' "|||||| [Lv 90]" mob health bars in nametags. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void drim$enemyHp(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component c = cir.getReturnValue();
        Component t = EnemyHp.transform(c);
        if (t != c) cir.setReturnValue(t);
    }
}
