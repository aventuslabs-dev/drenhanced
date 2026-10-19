package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Guild-mates (same "[TAG]" prefix as you) get light green nametags; party colour (dark green) is
 * left alone. An @Inject instead of the old @Overwrite, so other mods touching names still work.
 */
@Mixin(PlayerTeam.class)
public abstract class PlayerTeamMixin {
    @Shadow private Component playerPrefix;
    @Shadow private Component playerSuffix;

    @Shadow
    public abstract ChatFormatting getColor();

    @Inject(method = "getFormattedName", at = @At("HEAD"), cancellable = true)
    private void drim$guildColor(Component name, CallbackInfoReturnable<MutableComponent> cir) {
        if (!DrConfig.guildNametags()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PlayerTeam own = mc.level.getScoreboard().getPlayersTeam(mc.player.getScoreboardName());
        if (own == null) return;

        String prefix = this.playerPrefix.getString();
        if (!prefix.startsWith("[") || !prefix.equals(own.getPlayerPrefix().getString())) return;

        TextColor partyColor = TextColor.fromLegacyFormat(ChatFormatting.DARK_GREEN);
        MutableComponent newPrefix = this.playerPrefix.copy();
        List<Component> siblings = newPrefix.getSiblings();
        Style green = Style.EMPTY.withColor(ChatFormatting.GREEN);

        if (!siblings.isEmpty()) {
            Component first = siblings.get(0);
            if (partyColor.equals(first.getStyle().getColor())) return;
            siblings.set(0, first.copy().setStyle(green));
        } else {
            if (partyColor.equals(newPrefix.getStyle().getColor())) return;
            newPrefix.setStyle(green);
        }

        MutableComponent out = Component.empty()
            .append(newPrefix)
            .append(name)
            .append(this.playerSuffix.copy().setStyle(green));
        ChatFormatting color = this.getColor();
        if (color != ChatFormatting.RESET) {
            out.withStyle(color);
        }
        cir.setReturnValue(out);
    }
}
