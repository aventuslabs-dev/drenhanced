package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.MobHp;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the mob health bar from extractRenderState to renderNameTag. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements MobHp.Holder {
    @Unique
    private MobHp.Display drim$hp;

    @Override
    public MobHp.Display drim$hp() {
        return drim$hp;
    }

    @Override
    public void drim$setHp(MobHp.Display d) {
        drim$hp = d;
    }
}
