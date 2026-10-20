package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.SplitChat;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets the split chat swap opacity values per chat box for one render without triggering option callbacks. */
@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin implements SplitChat.OptionAccess {
    @Shadow
    Object value;

    @Override
    public Object drim$get() {
        return value;
    }

    @Override
    public void drim$set(Object v) {
        value = v;
    }
}
