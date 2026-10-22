package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Which DR items get a nicer client-side model. */
public final class ItemSkins {
    private ItemSkins() {}

    public static final ResourceLocation GEM_POUCH = ResourceLocation.fromNamespaceAndPath("dr_improvement_mod", "gem_pouch");

    /** Replacement item-model id for this stack, or null to keep DR's. */
    public static Object modelFor(ItemStack stack, DataComponentType<?> type) {
        if (type != DataComponents.ITEM_MODEL || !DrConfig.data.reskinItems || stack.isEmpty()) return null;
        if (!stack.has(DataComponents.CUSTOM_NAME)) return null;
        String name = stack.getHoverName().getString();
        if (name.contains("Gem Pouch")) return GEM_POUCH;
        return null;
    }
}
