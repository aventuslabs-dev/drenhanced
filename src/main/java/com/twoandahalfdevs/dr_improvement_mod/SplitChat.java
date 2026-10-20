package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Two chat boxes: player messages stay in the vanilla chat on the left, everything the server/game says
 * (damage debug, loot, system notices, command output...) goes to a second chat on the right.
 */
public final class SplitChat {
    private SplitChat() {}

    private static ChatComponent right;
    private static int lastGuiWidth = -1;
    private static List<Pattern> playerPatterns = List.of();
    private static List<Pattern> systemPatterns = List.of();
    private static String compiledFrom = "";

    public static boolean enabled() {
        return DrConfig.data.splitChat;
    }

    /** The right-hand (system) chat. */
    public static ChatComponent right() {
        if (right == null) right = new ChatComponent(Minecraft.getInstance());
        return right;
    }

    public static boolean isRight(ChatComponent c) {
        return c != null && c == right;
    }

    /** True if the message came from a player (stays on the left). */
    public static boolean isPlayerMessage(Component msg, MessageSignature sig, GuiMessageTag tag) {
        if (sig != null) return true; // signed player chat
        if (tag != null && !tag.equals(GuiMessageTag.system()) && !tag.equals(GuiMessageTag.systemSinglePlayer())) {
            return true; // unsigned / modified player chat
        }
        String text = msg.getString();
        compilePatterns();
        for (Pattern p : systemPatterns) if (p.matcher(text).find()) return false;
        for (Pattern p : playerPatterns) if (p.matcher(text).find()) return true;
        return looksLikePlayerChat(text, onlineNames());
    }

    /**
     * Plugin servers (DungeonRealms included) send player chat as plain system text, e.g.
     * "[G] Name: hi", "<Name> hi", "Name » hi", "FROM Name: hi". Treat it as player chat when an online
     * player's name appears near the start and is followed by a chat separator.
     */
    static boolean looksLikePlayerChat(String text, Collection<String> names) {
        if (text.isEmpty()) return false;
        for (String name : names) {
            if (name == null || name.length() < 3) continue;
            int from = 0;
            while (true) {
                int i = text.indexOf(name, from);
                if (i < 0 || i > 60) break;
                from = i + 1;
                if (i > 0 && isNameChar(text.charAt(i - 1))) continue;
                int j = i + name.length();
                if (j < text.length() && isNameChar(text.charAt(j))) continue;
                while (j < text.length() && (text.charAt(j) == ']' || text.charAt(j) == ')')) j++;
                if (j < text.length() && text.charAt(j) == '>') return true; // "<Name> msg" / "Name> msg"
                while (j < text.length() && text.charAt(j) == ' ') j++;
                if (j >= text.length()) continue;
                char c = text.charAt(j);
                if (c == ':' || c == '»') return true;
                if (c == '>' || (c == '-' && j + 1 < text.length() && text.charAt(j + 1) == '>')) {
                    // "Name -> Other: msg" (private message), but not damage lines like "20 DMG -> Name [100 HP]"
                    return text.indexOf(':', j) > 0;
                }
            }
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) names.add(mc.player.getGameProfile().getName());
        ClientPacketListener conn = mc.getConnection();
        if (conn != null) {
            for (PlayerInfo info : conn.getOnlinePlayers()) names.add(info.getProfile().getName());
        }
        return names;
    }

    private static void compilePatterns() {
        String key = DrConfig.data.chatPlayerPatterns + "|" + DrConfig.data.chatSystemPatterns;
        if (key.equals(compiledFrom)) return;
        compiledFrom = key;
        playerPatterns = compile(DrConfig.data.chatPlayerPatterns);
        systemPatterns = compile(DrConfig.data.chatSystemPatterns);
    }

    private static List<Pattern> compile(List<String> src) {
        List<Pattern> out = new ArrayList<>();
        if (src == null) return out;
        for (String s : src) {
            try {
                out.add(Pattern.compile(s));
            } catch (PatternSyntaxException e) {
                DrImprovementMod.LOGGER.warn("Bad chat pattern '{}': {}", s, e.getDescription());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ layout

    /** Left edge of the right-hand chat, in GUI pixels. */
    public static int offsetX(int guiWidth) {
        ChatComponent c = right();
        double scale = c.getScale();
        int visual = Mth.ceil((Mth.ceil(c.getWidth() / scale) + 12) * scale);
        return Math.max(guiWidth / 2, guiWidth - visual - 2);
    }

    /** Both chats share the screen, so neither may be wider than half of it. */
    public static int capWidth(int width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return width;
        int half = mc.getWindow().getGuiScaledWidth() / 2 - 14;
        return Math.max(40, Math.min(width, half));
    }

    public static void render(GuiGraphics g, int tickCount, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int gw = g.guiWidth();
        if (gw != lastGuiWidth) { // re-wrap both chats for the new width
            lastGuiWidth = gw;
            mc.gui.getChat().rescaleChat();
            right().rescaleChat();
        }
        int x = offsetX(gw);
        g.pose().pushPose();
        g.pose().translate(x, 0, 0);
        right().render(g, tickCount, mouseX - x, mouseY, false);
        g.pose().popPose();
    }

    public static void reset() {
        if (right != null) right.clearMessages(false);
    }
}
