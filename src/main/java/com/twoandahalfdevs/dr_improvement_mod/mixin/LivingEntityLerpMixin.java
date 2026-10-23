package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Less mob smoothing: vanilla eases a mob toward each new server position over 3 ticks, so it's drawn behind
 * where the server has it. Snapping in 1 tick removed that lag but stuttered: DR sends a mob's position only
 * every 1-3 ticks, so it moved, then stood still until the next update. Now each mob is eased over the time
 * until its next update is due (measured from its own updates), never more than vanilla's 3 ticks: as little
 * lag as possible while staying in motion between updates.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLerpMixin {
    @Unique
    private long drim$lastLerp;
    @Unique
    private float drim$gapTicks = 2f; // smoothed time between this mob's position updates

    @ModifyVariable(method = "lerpTo", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int drim$snappyMobs(int steps) {
        long now = System.currentTimeMillis();
        long last = drim$lastLerp;
        drim$lastLerp = now;
        if (!DrConfig.data.snappyMobs || steps <= 1 || (Object) this instanceof Player) return steps;
        if (last > 0) {
            float gap = Math.max(1f, Math.min(3f, (now - last) / 50f));
            drim$gapTicks += (gap - drim$gapTicks) * 0.3f;
        }
        return Math.max(1, Math.min(steps, Math.round(drim$gapTicks)));
    }
}
