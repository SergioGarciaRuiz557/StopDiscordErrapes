package com.discordaudioguard.util;

public final class ThreadUtils {
    private ThreadUtils() {}

    public static Thread daemonThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        return thread;
    }
}
