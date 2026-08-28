package com.example.opticore.chunk;

import com.example.opticore.profiler.MicroProfiler;

/** Conservative bottleneck classifier without forced GPU synchronization. */
public final class BottleneckDetector {
    public enum Bottleneck { NONE, CPU, CHUNK, UNKNOWN }
    private static volatile Bottleneck current = Bottleneck.NONE;
    private static volatile double cpuPressure;
    private static volatile double chunkPressure;
    private BottleneckDetector() {}
    public static void tick() {
        double frameMs = MicroProfiler.getAverageFrameTimeMs();
        double uploadMs = VBOUploadBudget.getAverageUploadTimeMs();
        double uploadPressure = VBOUploadBudget.getRecentUploadPressure();
        cpuPressure = clamp(frameMs / 16.667);
        chunkPressure = clamp((uploadMs / 2.0) + uploadPressure);
        if (chunkPressure > 1.25) current = Bottleneck.CHUNK;
        else if (cpuPressure > 1.15) current = Bottleneck.CPU;
        else current = frameMs > 0.0 ? Bottleneck.UNKNOWN : Bottleneck.NONE;
    }
    private static double clamp(double v) { return Math.max(0.0, Math.min(3.0, v)); }
    public static Bottleneck getCurrent() { return current; }
    public static double getCpuPressure() { return cpuPressure; }
    public static double getChunkPressure() { return chunkPressure; }
}
