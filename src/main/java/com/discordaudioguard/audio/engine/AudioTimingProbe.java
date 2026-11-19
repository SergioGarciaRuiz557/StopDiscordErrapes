package com.discordaudioguard.audio.engine;

/**
 * Single audio-thread writer, diagnostic-thread reader. No allocation, locks or I/O
 * on begin/end. A sequence stamp prevents mixing cumulative counters from two updates.
 */
final class AudioTimingProbe {
    private volatile long sequence;
    private volatile long startedNanos;
    private volatile long count, totalNanos, maximumNanos, units;

    void begin() { startedNanos = System.nanoTime(); }

    void end(long completedUnits) {
        long elapsed = System.nanoTime() - startedNanos;
        sequence++;
        count++;
        totalNanos += elapsed;
        maximumNanos = Math.max(maximumNanos, elapsed);
        units += Math.max(0, completedUnits);
        startedNanos = 0;
        sequence++;
    }

    /** Returns null on contention; the monitor retains its previous stable counters. */
    Snapshot snapshot() {
        for (int attempt = 0; attempt < 3; attempt++) {
            long before = sequence;
            if ((before & 1) != 0) continue;
            long c = count, total = totalNanos, max = maximumNanos, u = units;
            long start = startedNanos;
            if (before == sequence) {
                return new Snapshot(c, total, max, u,
                        start == 0 ? 0 : Math.max(0, System.nanoTime() - start));
            }
        }
        return null;
    }

    record Snapshot(long count, long totalNanos, long maximumNanos, long units, long activeNanos) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0);
    }
}
