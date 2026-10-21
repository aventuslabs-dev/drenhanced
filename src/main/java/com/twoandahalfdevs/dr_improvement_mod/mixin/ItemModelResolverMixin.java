package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.twoandahalfdevs.dr_improvement_mod.ItemSkins;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Client-side reskins: DR items drawn with our own model (e.g. gem pouches as a gold bag instead of an ink sac). */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @WrapOperation(method = "appendItemLayers",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object drim$reskin(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
        Object value = original.call(stack, type);
        Object swapped = ItemSkins.modelFor(stack, type);
        return swapped != null ? swapped : value;
    }
}
