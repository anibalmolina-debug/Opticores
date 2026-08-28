package com.example.opticore.chunk;

import com.example.opticore.profiler.MicroProfiler;

/** Conservative bottleneck classifier without forcing GPU synchronization. */
public final class BottleneckDetector {
    public enum Bottleneck { NONE, CPU, CHUNK, GPU_UNKNOWN, UNKNOWN }
    private static volatile Bottleneck current = Bottleneck.NONE;
    private static volatile double cpuPressure;
    private static volatile double chunkPressure;
    private static volatile double uploadPressure;

    private BottleneckDetector() {}

    public static void tick() {
        double frameMs = MicroProfiler.getAverageFrameTimeMs();
        double tickMs = MicroProfiler.getAverageTickTimeMs();
        double uploadMs = VBOUploadBudget.getAverageUploadTimeMs();
        uploadPressure = clamp((uploadMs / 2.0) + VBOUploadBudget.getRecentUploadPressure() * 0.5);
        cpuPressure = clamp(Math.max(frameMs / 16.667, tickMs / 12.0));
        chunkPressure = clamp(uploadPressure + MicroProfiler.getAverageMeshingTimeMs() / 4.0);

        if (chunkPressure > 1.2) current = Bottleneck.CHUNK;
        else if (cpuPressure > 1.15) current = Bottleneck.CPU;
        else if (frameMs > 18.5 && cpuPressure < 1.05) current = Bottleneck.GPU_UNKNOWN;
        else current = frameMs > 0.0 ? Bottleneck.UNKNOWN : Bottleneck.NONE;
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(3.0, v)); }
    public static Bottleneck getCurrent() { return current; }
    public static double getCpuPressure() { return cpuPressure; }
    public static double getChunkPressure() { return chunkPressure; }
    public static double getUploadPressure() { return uploadPressure; }
}
