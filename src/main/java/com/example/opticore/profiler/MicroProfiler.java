package com.example.opticore.profiler;

import java.util.Arrays;

/** Lightweight rolling profiler used by OptiCores' adaptive controllers. */
public final class MicroProfiler {
    private static final int HISTORY_SIZE = 120;
    public static final long[] tickTimes = new long[HISTORY_SIZE];
    public static final long[] frameTimes = new long[HISTORY_SIZE];
    public static final long[] meshingTimes = new long[HISTORY_SIZE];
    private static int tickIndex, tickSamples, frameIndex, frameSamples, meshingSamples;
    private static long currentFrameMeshingTime;
    private static long lastMemoryUsed;
    public static volatile long heapGrowthMbPerSec;
    public static volatile long allocationRateMbPerSec;
    private static long lastAllocCheckTime;
    private static long currentTickStartTime, currentFrameStartTime;

    private MicroProfiler() {}
    public static void startTick(long startTime) { currentTickStartTime = startTime; }
    public static void endTick(long endTime) {
        if (currentTickStartTime == 0L) return;
        tickTimes[tickIndex] = Math.max(0L, endTime - currentTickStartTime);
        tickIndex = (tickIndex + 1) % HISTORY_SIZE;
        tickSamples = Math.min(HISTORY_SIZE, tickSamples + 1);
        currentTickStartTime = 0L;
    }
    public static void startFrame(long startTime) {
        currentFrameStartTime = startTime;
        long now = System.currentTimeMillis();
        if (lastAllocCheckTime == 0L) { lastAllocCheckTime = now; lastMemoryUsed = usedMemory(); }
        else if (now - lastAllocCheckTime >= 1000L) {
            long used = usedMemory();
            heapGrowthMbPerSec = Math.max(0L, used - lastMemoryUsed) / (1024L * 1024L);
            allocationRateMbPerSec = heapGrowthMbPerSec;
            lastMemoryUsed = used;
            lastAllocCheckTime = now;
        }
    }
    public static long endFrame(long endTime) {
        if (currentFrameStartTime == 0L) return 0L;
        long duration = Math.max(0L, endTime - currentFrameStartTime);
        frameTimes[frameIndex] = duration;
        meshingTimes[frameIndex] = currentFrameMeshingTime;
        frameIndex = (frameIndex + 1) % HISTORY_SIZE;
        frameSamples = Math.min(HISTORY_SIZE, frameSamples + 1);
        meshingSamples = Math.min(HISTORY_SIZE, meshingSamples + 1);
        currentFrameMeshingTime = 0L;
        currentFrameStartTime = 0L;
        return duration;
    }
    public static void recordMeshingTime(long timeNs) { currentFrameMeshingTime += Math.max(0L, timeNs); }
    public static double getAverageTickTimeMs() { return average(tickTimes, tickSamples) / 1_000_000.0; }
    public static double getAverageFrameTimeMs() { return average(frameTimes, frameSamples) / 1_000_000.0; }
    public static double getAverageMeshingTimeMs() { return average(meshingTimes, meshingSamples) / 1_000_000.0; }
    public static double getPercentileFrameTimeMs(double percentile) {
        if (frameSamples == 0) return 0.0;
        long[] copy = Arrays.copyOf(frameTimes, frameSamples);
        Arrays.sort(copy);
        double p = Math.max(0.0, Math.min(1.0, percentile));
        int index = Math.min(copy.length - 1, (int) Math.ceil(p * copy.length) - 1);
        return copy[index] / 1_000_000.0;
    }
    public static double getOnePercentLowFps() { double p = getPercentileFrameTimeMs(0.99); return p <= 0.0 ? 0.0 : 1000.0 / p; }
    public static double getPointOnePercentLowFps() { double p = getPercentileFrameTimeMs(0.999); return p <= 0.0 ? 0.0 : 1000.0 / p; }
    private static double average(long[] array, int samples) { if (samples <= 0) return 0.0; long sum = 0L; for (int i=0;i<samples;i++) sum += array[i]; return (double) sum / samples; }
    private static long usedMemory() { Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }
}
