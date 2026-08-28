package com.example.opticore.chunk;

import java.util.concurrent.atomic.AtomicInteger;

/** Central advisory scheduler for OptiCores-owned background work. */
public final class AdaptiveWorkScheduler {
    private static final HardwareProfile HARDWARE = HardwareProfile.detect();
    private static final AtomicInteger tasksThisFrame = new AtomicInteger();
    private static volatile int maxTasksPerFrame = 16;
    private static volatile long frameBudgetNs = 2_000_000L;
    private static volatile long taskBudgetNs;
    private static long frameStartNs;

    private AdaptiveWorkScheduler() {}

    public static void beginFrame() {
        frameStartNs = System.nanoTime();
        tasksThisFrame.set(0);
        int base = switch (HARDWARE.tier()) {
            case VERY_LOW -> 8;
            case LOW -> 16;
            case MID -> 32;
            case HIGH -> 48;
        };
        switch (BottleneckDetector.getCurrent()) {
            case CHUNK, CPU -> base = Math.max(4, base / 2);
            case GPU_UNKNOWN -> base = Math.min(48, base + 8);
            default -> {}
        }
        if (VBOUploadBudget.getRecentUploadPressure() > 1.1) base = Math.max(4, base / 2);
        maxTasksPerFrame = base;
        frameBudgetNs = switch (HARDWARE.tier()) {
            case VERY_LOW -> 1_000_000L;
            case LOW -> 1_500_000L;
            case MID -> 2_500_000L;
            case HIGH -> 3_500_000L;
        };
        taskBudgetNs = Math.max(250_000L, frameBudgetNs / Math.max(1, Math.min(8, base)));
    }

    public static boolean tryAcquire() {
        if (tasksThisFrame.get() >= maxTasksPerFrame) return false;
        if (System.nanoTime() - frameStartNs >= frameBudgetNs) return false;
        tasksThisFrame.incrementAndGet();
        return true;
    }

    public static boolean withinTaskBudget(long startNs) {
        return System.nanoTime() - startNs < taskBudgetNs;
    }

    public static int getMaxTasksPerFrame() { return maxTasksPerFrame; }
    public static int getTasksThisFrame() { return tasksThisFrame.get(); }
    public static long getFrameBudgetNs() { return frameBudgetNs; }
    public static int getWorkerThreads() { return HARDWARE.workerThreads(); }
}
