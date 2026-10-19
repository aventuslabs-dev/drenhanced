package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No client-side crit sound/particles (the server decides crits). Singleplayer's server side is untouched. */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @WrapWithCondition(method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private boolean drim$noCritSound(Level level, Player except, double x, double y, double z,
                                     SoundEvent sound, SoundSource source, float volume, float pitch) {
        return !(level.isClientSide() && DrConfig.legacyCombat() && sound == SoundEvents.PLAYER_ATTACK_CRIT);
    }

    @WrapWithCondition(method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;crit(Lnet/minecraft/world/entity/Entity;)V"))
    private boolean drim$noCritParticles(Player self, Entity target) {
        return !(self.level().isClientSide() && DrConfig.legacyCombat());
    }
}
