package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Locale;

/**
 * Who's who on player plates: your guild and party show green, guilds you mark as allies green, guilds you
 * mark as enemies red, everyone else neutral. Marked with /drguild ally|enemy|remove TAG (saved).
 */
public final class Guilds {
    private Guilds() {}

    public enum Rel {
        OWN(0xE0236B30, 0xFF5BE37A, 0xFFFFFFFF, "your guild"),
        PARTY(0xE0236B30, 0xFF5BE37A, 0xFFFFFFFF, "party"),
        ALLY(0xE0236B30, 0xFF5BE37A, 0xFFFFFFFF, "ally"),
        ENEMY(0xE08A1A1A, 0xFFFF5A4A, 0xFFFFFFFF, "enemy"),
        NEUTRAL(0xD0262A33, 0xFF8A94A6, 0xFFE6E6E6, "neutral");

        final int fill, edge, text;
        final String label;

        Rel(int fill, int edge, int text, String label) {
            this.fill = fill;
            this.edge = edge;
            this.text = text;
            this.label = label;
        }
    }

    /** "[PHAT] " team prefix -> "PHAT", or null when the player has no guild. */
    public static String tagOf(PlayerTeam team) {
        if (team == null) return null;
        String tag = SplitChat.strip(team.getPlayerPrefix().getString()).trim();
        if (!tag.startsWith("[") || !tag.endsWith("]") || tag.length() <= 2) return null;
        return tag.substring(1, tag.length() - 1).trim();
    }

    public static Rel relation(String tag, PlayerTeam team, Minecraft mc) {
        String up = tag.toUpperCase(Locale.ROOT);
        if (DrConfig.data.enemyGuilds.contains(up)) return Rel.ENEMY;
        if (mc.player != null && mc.level != null) {
            String mine = tagOf(mc.level.getScoreboard().getPlayersTeam(mc.player.getScoreboardName()));
            if (mine != null && mine.equalsIgnoreCase(tag)) return Rel.OWN;
        }
        if (isParty(team)) return Rel.PARTY;
        if (DrConfig.data.allyGuilds.contains(up)) return Rel.ALLY;
        return Rel.NEUTRAL;
    }

    /** DR colours a party member's guild prefix dark green. */
    static boolean isParty(PlayerTeam team) {
        if (team == null) return false;
        Component p = team.getPlayerPrefix();
        TextColor c = p.getSiblings().isEmpty() ? p.getStyle().getColor() : p.getSiblings().get(0).getStyle().getColor();
        if (c == null) {
            String raw = p.getString();
            return raw.startsWith("\u00a72");
        }
        return c.equals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GREEN));
    }

    public static Component mark(String tag, Rel rel) {
        String up = tag.replace("[", "").replace("]", "").trim().toUpperCase(Locale.ROOT);
        DrConfig.data.allyGuilds.remove(up);
        DrConfig.data.enemyGuilds.remove(up);
        if (rel == Rel.ALLY) DrConfig.data.allyGuilds.add(up);
        if (rel == Rel.ENEMY) DrConfig.data.enemyGuilds.add(up);
        DrConfig.save();
        String what = rel == Rel.ALLY ? "\u00a7aally" : rel == Rel.ENEMY ? "\u00a7cenemy" : "\u00a77neutral";
        return Component.literal("\u00a76[DR] \u00a7f[" + up + "] is now " + what);
    }

    public static Component list() {
        return Component.literal("\u00a76[DR] \u00a7aAllies: \u00a7f" + (DrConfig.data.allyGuilds.isEmpty() ? "none" : String.join(", ", DrConfig.data.allyGuilds))
            + "  \u00a7cEnemies: \u00a7f" + (DrConfig.data.enemyGuilds.isEmpty() ? "none" : String.join(", ", DrConfig.data.enemyGuilds)));
    }
}
