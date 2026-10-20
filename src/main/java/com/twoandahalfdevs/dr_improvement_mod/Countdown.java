package com.twoandahalfdevs.dr_improvement_mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Sub-second timers from DR's whole-second action bar.
 *
 * DR rounds down: "Dash [3s]" means 3.0–3.99 s are left, it shows "[0s]" for the last second and drops the
 * entry on the next tick (seen in every capture). Its regular update is a 1 s ticker, but it also re-sends
 * the bar on events (using an ability, drinking a potion) at other moments. Each message therefore says
 * "this ends somewhere in [t + N, t + N + 1)". Intersecting those windows across messages pins the real end
 * down to a fraction of a second, so the countdown runs smoothly to the moment the ability is usable
 * instead of hitting 0 a second early and jumping back up every time a new message arrives.
 */
public final class Countdown {
    private Countdown() {}

    /** Network/tick jitter allowed on each observation, in ms. */
    private static final double SLACK = 120;

    private static final Map<String, double[]> windows = new HashMap<>(); // key -> {earliest end, latest end} (epoch ms)

    /**
     * DR showed {@code seconds} (rounded down to {@code granularity} seconds, e.g. 60 for "[2m]") at time {@code t}.
     */
    public static void observe(String key, int seconds, int granularity, long t) {
        double lo = t + seconds * 1000.0 - SLACK;
        double hi = t + (seconds + granularity) * 1000.0 + SLACK;
        double[] w = windows.get(key);
        if (w != null && lo <= w[1] && hi >= w[0]) {
            double nlo = Math.max(w[0], lo), nhi = Math.min(w[1], hi);
            w[0] = nlo;
            w[1] = nhi;
        } else {
            windows.put(key, new double[]{lo, hi}); // first sight, or restarted (used again / new combat)
        }
    }

    /** Best estimate of the seconds left (middle of the window), or -1 if unknown. */
    public static double remaining(String key, long now) {
        double[] w = windows.get(key);
        if (w == null) return -1;
        return Math.max(0, ((w[0] + w[1]) / 2 - now) / 1000.0);
    }

    /** Width of the uncertainty window in seconds (for tests / debugging). */
    public static double uncertainty(String key) {
        double[] w = windows.get(key);
        return w == null ? -1 : (w[1] - w[0]) / 1000.0;
    }

    public static void forget(String key) {
        windows.remove(key);
    }

    public static void reset() {
        windows.clear();
    }
}
