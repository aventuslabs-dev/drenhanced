package com.twoandahalfdevs.dr_improvement_mod.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the auto clicker swing through the normal attack path. */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
    @Invoker("startAttack")
    boolean drim$startAttack();
}
