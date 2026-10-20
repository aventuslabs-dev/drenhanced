package eu.the5zig.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;

/** Client-side state the HUD items read: clicks per second, AFK time, timer, death point, speed. */
public final class Tracker {
    private Tracker() {}

    // ---- CPS ----
    private static final ArrayDeque<Long> left = new ArrayDeque<>();
    private static final ArrayDeque<Long> right = new ArrayDeque<>();

    public static void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) left.addLast(now);
        else if (button == 1) right.addLast(now);
    }

    public static int leftCps() {
        return count(left);
    }

    public static int rightCps() {
        return count(right);
    }

    private static int count(ArrayDeque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
        return q.size();
    }

    // ---- AFK (original: counter starts after 30 s of no input; last AFK period shown underlined) ----
    public static final long AFK_AFTER = 30_000;
    private static long lastInput = System.currentTimeMillis();
    private static long lastAfk;

    public static void input() {
        long now = System.currentTimeMillis();
        long idle = now - lastInput;
        if (idle > AFK_AFTER) lastAfk = idle;
        lastInput = now;
    }

    public static long afkTime() {
        return System.currentTimeMillis() - lastInput;
    }

    public static long lastAfkTime() {
        return lastAfk;
    }

    // ---- Timer ----
    private static long timerStart;
    private static long timerStopped;
    private static boolean timerRunning;

    public static void toggleTimer() {
        long now = System.currentTimeMillis();
        if (timerRunning) {
            timerStopped = now - timerStart;
            timerRunning = false;
        } else {
            timerStart = now - timerStopped;
            timerRunning = true;
        }
    }

    public static void resetTimer() {
        timerRunning = false;
        timerStopped = 0;
    }

    /** -1 if the timer was never started. */
    public static long timer() {
        if (timerRunning) return System.currentTimeMillis() - timerStart;
        return timerStopped == 0 ? -1 : timerStopped;
    }

    public static boolean timerRunning() {
        return timerRunning;
    }

    // ---- per tick: speed, death point, rotation-based AFK reset ----
    private static double px, pz;
    private static boolean havePos;
    private static double speed;
    private static float lastYaw, lastPitch;
    private static boolean wasDead;
    private static int[] deathPos;
    private static String deathDim;

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            havePos = false;
            return;
        }
        if (havePos) {
            double dx = p.getX() - px, dz = p.getZ() - pz;
            double inst = Math.sqrt(dx * dx + dz * dz) * 20.0;
            speed = speed * 0.6 + inst * 0.4; // smooth it a little
            if (inst > 1e-3) input();
        }
        px = p.getX();
        pz = p.getZ();
        havePos = true;

        if (p.getYRot() != lastYaw || p.getXRot() != lastPitch) input();
        lastYaw = p.getYRot();
        lastPitch = p.getXRot();

        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead) {
            deathPos = new int[]{p.getBlockX(), p.getBlockY(), p.getBlockZ()};
            deathDim = p.level().dimension().location().getPath();
        }
        wasDead = dead;
    }

    /** Horizontal speed in blocks per second. */
    public static double speed() {
        return speed;
    }

    public static int[] deathPos() {
        return deathPos;
    }

    public static String deathDimension() {
        return deathDim;
    }
}
