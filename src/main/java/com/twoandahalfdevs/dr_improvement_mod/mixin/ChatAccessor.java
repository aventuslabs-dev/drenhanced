package com.twoandahalfdevs.dr_improvement_mod.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Self-test access to a chat's stored messages. */
@Mixin(ChatComponent.class)
public interface ChatAccessor {
    @Accessor("allMessages")
    List<GuiMessage> drim$messages();
}
