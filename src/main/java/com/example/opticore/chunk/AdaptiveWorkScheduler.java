package com.example.opticore.chunk;

/** Central frame-work budget. No task may assume the frame has unlimited CPU time. */
public final class AdaptiveWorkScheduler {
    private static final HardwareProfile HARDWARE = HardwareProfile.detect();
    private static volatile int maxTasksPerFrame = 32;
    private static volatile int tasksThisFrame;
    private AdaptiveWorkScheduler() {}
    public static void beginFrame() {
        tasksThisFrame = 0;
        int base = switch (HARDWARE.tier()) {
            case VERY_LOW -> 8; case LOW -> 16; case MID -> 32; case HIGH -> 48;
        };
        BottleneckDetector.Bottleneck b = BottleneckDetector.getCurrent();
        if (b == BottleneckDetector.Bottleneck.CHUNK) base /= 2;
        else if (b == BottleneckDetector.Bottleneck.CPU) base = Math.max(4, base / 2);
        maxTasksPerFrame = Math.max(4, base);
    }
    public static boolean tryAcquire() {
        if (tasksThisFrame >= maxTasksPerFrame) return false;
        tasksThisFrame++;
        return true;
    }
    public static int getMaxTasksPerFrame() { return maxTasksPerFrame; }
    public static int getTasksThisFrame() { return tasksThisFrame; }
    public static int getWorkerThreads() { return HARDWARE.workerThreads(); }
}
