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
        String text = strip(msg.getString());
        if (isOwnRecentMessage(text)) return true;
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
        text = strip(text);
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

    /** Legacy "§x" colour codes left inside plain text (many plugin servers send these) break name matching. */
    static String strip(String s) {
        if (s == null) return "";
        if (s.indexOf('\u00a7') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7') { i++; continue; }
            b.append(c);
        }
        return b.toString();
    }

    // ---- what you typed yourself always stays on the left ----
    private record Sent(String text, long at) {}
    private static final java.util.ArrayDeque<Sent> sent = new java.util.ArrayDeque<>();

    public static void onSent(String message) {
        if (message == null || message.isBlank() || message.startsWith("/")) return;
        synchronized (sent) {
            sent.addLast(new Sent(message.trim(), System.currentTimeMillis()));
            while (sent.size() > 10) sent.pollFirst();
        }
    }

    private static boolean isOwnRecentMessage(String text) {
        long cutoff = System.currentTimeMillis() - 15_000;
        String t = text.trim();
        synchronized (sent) {
            sent.removeIf(m -> m.at < cutoff);
            for (var it = sent.iterator(); it.hasNext(); ) {
                Sent m = it.next();
                if (t.length() > m.text.length() && t.endsWith(m.text)) {
                    char before = t.charAt(t.length() - m.text.length() - 1);
                    if (before == ' ' || before == ':' || before == '>' || before == '\u00bb') {
                        it.remove();
                        return true;
                    }
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
            for (PlayerInfo info : conn.getOnlinePlayers()) {
                names.add(info.getProfile().getName());
                // nicknames / display names shown in the tab list, e.g. "<5R> Name"
                if (info.getTabListDisplayName() != null) {
                    for (String tok : strip(info.getTabListDisplayName().getString()).split("[^A-Za-z0-9_]+")) {
                        if (tok.length() >= 3 && tok.length() <= 16) names.add(tok);
                    }
                }
            }
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

    public static DrConfig.ChatBox box(ChatComponent c) {
        return isRight(c) ? DrConfig.data.rightChat : DrConfig.data.leftChat;
    }

    /** Left edge of the right-hand chat, in GUI pixels. */
    public static int offsetX(int guiWidth) {
        ChatComponent c = right();
        double scale = c.getScale();
        int visual = Mth.ceil((Mth.ceil(c.getWidth() / scale) + 12) * scale);
        return Math.max(0, guiWidth - visual - 2 - DrConfig.data.rightChat.x);
    }

    public static int leftX() {
        return DrConfig.data.leftChat.x;
    }

    /** Custom width, or the vanilla width capped at half the screen so the two chats don't overlap. */
    public static int width(ChatComponent c, int vanilla) {
        DrConfig.ChatBox b = box(c);
        if (b.width > 0) return b.width;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return vanilla;
        int half = mc.getWindow().getGuiScaledWidth() / 2 - 14;
        return Math.max(40, Math.min(vanilla, half));
    }

    public static int height(ChatComponent c) {
        DrConfig.ChatBox b = box(c);
        return Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen ? b.heightFocused : b.heightUnfocused;
    }

    /** Re-wrap both chats after a width / size change. */
    public static void relayout() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.getChat().rescaleChat();
        right().rescaleChat();
    }

    // Opacity is read straight from the options while rendering, so swap in each box's values around its render.
    private static Object savedText, savedBg;

    /** HUD path (chat closed): called around vanilla's own left-chat render. */
    public static void beginLeft(GuiGraphics g) {
        save();
        apply(DrConfig.data.leftChat);
        g.pose().pushPose();
        g.pose().translate(leftX(), -DrConfig.data.leftChat.y, 0);
    }

    public static void endLeftAndRenderRight(GuiGraphics g, int tickCount, int mouseX, int mouseY) {
        g.pose().popPose();
        try {
            // with chat open, the chat screen draws both boxes (renderBoth)
            if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                apply(DrConfig.data.rightChat);
                renderRight(g, tickCount, mouseX, mouseY, false);
            }
        } finally {
            restore();
        }
    }

    /** Chat-screen path (chat open): the screen renders the chat itself, so draw both boxes from there. */
    public static void renderBoth(GuiGraphics g, int tickCount, int mouseX, int mouseY, boolean focused, LeftRender left) {
        save();
        try {
            DrConfig.ChatBox lb = DrConfig.data.leftChat;
            apply(lb);
            g.pose().pushPose();
            g.pose().translate(leftX(), -lb.y, 0);
            left.render(mouseX - leftX(), mouseY + lb.y);
            g.pose().popPose();
            apply(DrConfig.data.rightChat);
            renderRight(g, tickCount, mouseX, mouseY, focused);
        } finally {
            restore();
        }
    }

    @FunctionalInterface
    public interface LeftRender {
        void render(int mouseX, int mouseY);
    }

    private static void save() {
        var o = Minecraft.getInstance().options;
        savedText = ((OptionAccess) (Object) o.chatOpacity()).drim$get();
        savedBg = ((OptionAccess) (Object) o.textBackgroundOpacity()).drim$get();
    }

    private static void restore() {
        var o = Minecraft.getInstance().options;
        if (savedText != null) ((OptionAccess) (Object) o.chatOpacity()).drim$set(savedText);
        if (savedBg != null) ((OptionAccess) (Object) o.textBackgroundOpacity()).drim$set(savedBg);
        savedText = savedBg = null;
    }

    private static void apply(DrConfig.ChatBox b) {
        var o = Minecraft.getInstance().options;
        ((OptionAccess) (Object) o.chatOpacity()).drim$set(b.textOpacity);
        ((OptionAccess) (Object) o.textBackgroundOpacity()).drim$set(b.background);
    }

    /** Implemented by a mixin on OptionInstance: raw value access without firing option callbacks. */
    public interface OptionAccess {
        Object drim$get();

        void drim$set(Object value);
    }

    private static void renderRight(GuiGraphics g, int tickCount, int mouseX, int mouseY, boolean focused) {
        Minecraft mc = Minecraft.getInstance();
        int gw = g.guiWidth();
        if (gw != lastGuiWidth) { // re-wrap both chats for the new width
            lastGuiWidth = gw;
            relayout();
        }
        int x = offsetX(gw);
        int y = DrConfig.data.rightChat.y;
        g.pose().pushPose();
        g.pose().translate(x, -y, 0);
        right().render(g, tickCount, mouseX - x, mouseY + y, focused);
        g.pose().popPose();
    }

    private static String lastRightText;
    private static int lastRightCount;
    private static long lastRightAt;

    /** Adds to the right chat, folding an identical message within 10 s into "message (x3)". */
    public static void addRight(Component message, MessageSignature sig, GuiMessageTag tag) {
        ChatComponent r = right();
        String text = message.getString();
        long now = System.currentTimeMillis();
        if (DrConfig.data.collapseRepeats && text.equals(lastRightText) && now - lastRightAt < 10_000 && !text.isBlank()) {
            var list = ((com.twoandahalfdevs.dr_improvement_mod.mixin.ChatAccessor) r).drim$messages();
            if (!list.isEmpty()) list.remove(0); // newest first
            lastRightCount++;
            lastRightAt = now;
            r.addMessage(Component.empty().append(message)
                .append(Component.literal(" (x" + lastRightCount + ")").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), sig, tag);
            r.rescaleChat(); // rebuild the visible lines from the edited list
            return;
        }
        lastRightText = text;
        lastRightCount = 1;
        lastRightAt = now;
        r.addMessage(message, sig, tag);
    }

    public static void reset() {
        if (right != null) right.clearMessages(false);
    }
}
