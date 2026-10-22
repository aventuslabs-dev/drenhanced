package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Which DR items get a nicer client-side model. */
public final class ItemSkins {
    private ItemSkins() {}

    public static final ResourceLocation GEM_POUCH = ResourceLocation.fromNamespaceAndPath("dr_improvement_mod", "gem_pouch");

    /**
     * Your off-hand shield follows the tier of the weapon in your main hand (wood T1 ... gold T5), when the
     * resource pack provides "defender_t1".."defender_t5" item models. Without them the shield is left alone.
     */
    static Object offhandDefender(ItemStack stack) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || stack != mc.player.getOffhandItem() || !stack.is(net.minecraft.world.item.Items.SHIELD)) return null;
        int tier = tierOf(mc.player.getMainHandItem());
        if (tier == 0 || !packHasTieredDefenders(mc)) return null;
        return DEFENDERS[tier - 1];
    }

    private static final ResourceLocation[] DEFENDERS = new ResourceLocation[5];
    static {
        for (int i = 0; i < 5; i++) DEFENDERS[i] = ResourceLocation.withDefaultNamespace("defender_t" + (i + 1));
    }

    /** DR weapon tier from the item's material: wood 1, stone 2, iron 3, diamond 4, gold 5 (0 = not a tiered weapon). */
    public static int tierOf(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (id.startsWith("wooden_")) return 1;
        if (id.startsWith("stone_")) return 2;
        if (id.startsWith("iron_")) return 3;
        if (id.startsWith("diamond_")) return 4;
        if (id.startsWith("golden_") || id.startsWith("netherite_")) return 5;
        return 0;
    }

    private static long checkedAt;
    private static boolean cachedHas;

    /** Re-checked every few seconds so turning the pack on/off takes effect without a restart. */
    static boolean packHasTieredDefenders(net.minecraft.client.Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - checkedAt > 3000) {
            checkedAt = now;
            cachedHas = mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("items/defender_t1.json")).isPresent();
        }
        return cachedHas;
    }

    /** Replacement item-model id for this stack, or null to keep DR's. */
    public static Object modelFor(ItemStack stack, DataComponentType<?> type) {
        if (type != DataComponents.ITEM_MODEL || !DrConfig.data.reskinItems || stack.isEmpty()) return null;
        Object def = offhandDefender(stack);
        if (def != null) return def;
        if (!stack.has(DataComponents.CUSTOM_NAME)) return null;
        String name = stack.getHoverName().getString();
        if (name.contains("Gem Pouch")) return GEM_POUCH;
        return null;
    }
}
