package com.twoandahalfdevs.dr_improvement_mod.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    /** Avoids hard-coding the synched-data slot (it was a magic "9" in the Fabric version). */
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> drim$healthId() {
        throw new AssertionError();
    }
}
