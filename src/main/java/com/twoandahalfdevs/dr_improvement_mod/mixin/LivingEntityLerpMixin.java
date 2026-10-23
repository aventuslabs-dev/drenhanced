package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Less mob smoothing: vanilla eases a mob toward each new server position over 3 ticks, so on top of your ping
 * you see it ~100 ms behind where the server has it. With this on, it gets there in 1 tick instead.
 * Mobs only; players move as usual.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLerpMixin {
    @ModifyVariable(method = "lerpTo", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int drim$snappyMobs(int steps) {
        if (!DrConfig.data.snappyMobs || steps <= 1 || (Object) this instanceof Player) return steps;
        return 1;
    }
}
