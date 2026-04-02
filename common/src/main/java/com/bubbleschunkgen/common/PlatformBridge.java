package com.bubbleschunkgen.common;

/**
 * Platform-agnostic interface for services that differ per platform:
 * logging, scheduling, debug state, and Terra detection.
 */
public interface PlatformBridge {

    boolean isDebug();

    void setDebug(boolean debug);

    void log(String message);

    void warn(String message);

    /** Schedule a task to run after the given number of ticks (1 tick = 50ms). */
    void runDelayed(Runnable task, long ticks);

    /** Fill a chest at the given local chunk coordinates with dedication items. */
    void fillDedicationChest(BlockAccess chunk, int localX, int y, int localZ);
}
