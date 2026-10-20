package com.twoandahalfdevs.dr_improvement_mod.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the action bar (DR shows ability/potion cooldowns there). */
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("overlayMessageString")
    Component drim$getOverlayMessage();

    @Accessor("overlayMessageTime")
    int drim$getOverlayTime();

    @Accessor("title")
    Component drim$getTitle();

    @Accessor("subtitle")
    Component drim$getSubtitle();
}
