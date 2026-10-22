package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.twoandahalfdevs.dr_improvement_mod.DualWield;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dual wield (visual): in third person your offhand also holds your main-hand weapon (only on your screen). */
@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {
    @WrapOperation(method = "extractArmedEntityRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemHeldByArm(Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack drim$dualThirdPerson(LivingEntity entity, HumanoidArm arm, Operation<ItemStack> original) {
        if (arm != entity.getMainArm() && DualWield.isSelf(entity) && DualWield.active((Player) entity)) return entity.getMainHandItem();
        return original.call(entity, arm);
    }
}
