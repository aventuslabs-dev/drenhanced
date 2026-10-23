package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Low-ping feel for your hits: the red hurt flash and a few sparks play the instant you swing at a mob, instead
 * of when DR's damage event arrives a ping later. Purely visual; DR's own flash / sound still follow.
 */
public final class HitFeedback {
    private HitFeedback() {}

    static int flashes; // self-test

    public static void onAttack(Player player, Entity target) {
        if (!DrConfig.data.hitPrediction || !(target instanceof LivingEntity mob) || !mob.isAlive()) return;
        if (target instanceof Player) return; // mobs only
        float yaw = (float) (net.minecraft.util.Mth.atan2(mob.getZ() - player.getZ(), mob.getX() - player.getX()) * (180 / Math.PI) - mob.getYRot());
        mob.animateHurt(yaw);
        flashes++;
        // sparks where your weapon meets it
        Vec3 eye = player.getEyePosition();
        Vec3 c = mob.getBoundingBox().getCenter();
        Vec3 at = c.add(eye.subtract(c).normalize().scale(mob.getBbWidth() * 0.5));
        var level = mob.level();
        for (int i = 0; i < 6; i++) {
            double vx = (level.random.nextDouble() - 0.5) * 0.5, vy = level.random.nextDouble() * 0.35, vz = (level.random.nextDouble() - 0.5) * 0.5;
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, vx, vy, vz);
        }
    }
}
