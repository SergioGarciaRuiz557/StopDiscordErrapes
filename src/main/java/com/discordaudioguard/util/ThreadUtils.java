package com.discordaudioguard.util;

/** Minimal factory for background audio threads. */
public final class ThreadUtils {
    /** Prevents instantiation of this purely static class. */
    private ThreadUtils() {}

    /**
     * Builds, but does not start, a maximum-priority daemon thread.
     *
     * <p>Daemon status prevents the process from remaining alive after JavaFX exits.
     * The elevated priority helps reduce scheduling discontinuities, although the
     * operating system retains final control over the effective priority.</p>
     *
     * @param name descriptive name visible in logs and thread dumps
     * @param task work to be executed by the thread
     * @return configured thread that has not yet been started
     */
    public static Thread daemonThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        return thread;
    }
}
