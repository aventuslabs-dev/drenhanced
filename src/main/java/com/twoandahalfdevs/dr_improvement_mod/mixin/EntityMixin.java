package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colour-blind friendly glow colours for DR's item rarities. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void drim$colorBlindGlow(CallbackInfoReturnable<Integer> cir) {
        if (!DrConfig.colorBlind()) return;
        switch (cir.getReturnValueI()) {
            case 0xFFFF55 -> cir.setReturnValue(0xFF45A5); // yellow  -> pink
            case 0x55FFFF -> cir.setReturnValue(0x90D5FF); // aqua (rare) -> light blue
            case 0x55FF55 -> cir.setReturnValue(0x3B632F); // green   -> dark green
            case 0xFFAA00 -> cir.setReturnValue(0xFF0303); // gold    -> red
            default -> { }
        }
    }
}
