package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import com.twoandahalfdevs.dr_improvement_mod.FullBright;
import com.twoandahalfdevs.dr_improvement_mod.SplitChat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
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

    /** Full bright: the gamma option reports a very high value while it's on (never while options are saved). */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void drim$fullbright(CallbackInfoReturnable<Object> cir) {
        if (!DrConfig.data.fullbright || FullBright.saving) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && (Object) this == mc.options.gamma()) cir.setReturnValue(FullBright.GAMMA);
    }
}
