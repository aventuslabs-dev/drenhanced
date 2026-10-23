package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.twoandahalfdevs.dr_improvement_mod.AutoSkill;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Auto skills: the hotbar keeps highlighting your weapon during the one-tick artifact switch. */
@Mixin(Gui.class)
public abstract class GuiHotbarMixin {
    @WrapOperation(method = "renderItemHotbar",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I"))
    private int drim$shownSlot(Inventory inv, Operation<Integer> original) {
        return AutoSkill.shownSlot(original.call(inv));
    }
}
