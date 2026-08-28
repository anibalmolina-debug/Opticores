package com.example.opticore.chunk;

import com.example.opticore.profiler.MicroProfiler;

/** Stable quality controller with separate responses for CPU/chunk pressure. */
public final class DynamicQualityScaler {
    private static final long DANGER_FRAME_TIME_NS = 20_000_000L;
    private static final long RECOVER_FRAME_TIME_NS = 14_500_000L;
    private static final float MIN_SHADOW_DISTANCE = 0.25f;
    private static final float MIN_SHADOW_RESOLUTION = 0.5f;
    private static volatile float shadowDistanceMultiplier = 1.0f;
    private static volatile float shadowResolutionMultiplier = 1.0f;
    private static volatile float entityLodBias;
    private static volatile boolean scalingActive;
    private static long smoothedFrameTimeNs = 16_600_000L;
    private static int recoveryFrames;

    private DynamicQualityScaler() {}

    public static void tick() {
        BottleneckDetector.tick();
        long avgFrameNs = (long) (MicroProfiler.getAverageFrameTimeMs() * 1_000_000.0);
        smoothedFrameTimeNs = (long) (0.1 * avgFrameNs + 0.9 * smoothedFrameTimeNs);
        BottleneckDetector.Bottleneck b = BottleneckDetector.getCurrent();
        if (smoothedFrameTimeNs > DANGER_FRAME_TIME_NS) {
            shadowDistanceMultiplier = Math.max(MIN_SHADOW_DISTANCE, shadowDistanceMultiplier - 0.02f);
            if (b != BottleneckDetector.Bottleneck.GPU_UNKNOWN) entityLodBias = Math.min(1.0f, entityLodBias + 0.03f);
            shadowResolutionMultiplier = Math.max(MIN_SHADOW_RESOLUTION,
                    shadowResolutionMultiplier - (b == BottleneckDetector.Bottleneck.CHUNK ? 0.01f : 0.02f));
            scalingActive = true;
            recoveryFrames = 0;
        } else if (smoothedFrameTimeNs < RECOVER_FRAME_TIME_NS && scalingActive) {
            if (++recoveryFrames >= 30) {
                shadowDistanceMultiplier = Math.min(1.0f, shadowDistanceMultiplier + 0.005f);
                shadowResolutionMultiplier = Math.min(1.0f, shadowResolutionMultiplier + 0.005f);
                entityLodBias = Math.max(0.0f, entityLodBias - 0.01f);
                if (shadowDistanceMultiplier >= 1.0f && shadowResolutionMultiplier >= 1.0f && entityLodBias <= 0.0f) scalingActive = false;
            }
        } else recoveryFrames = 0;
    }

    public static float getShadowDistanceMultiplier() { return shadowDistanceMultiplier; }
    public static float getShadowResolutionMultiplier() { return shadowResolutionMultiplier; }
    public static float getEntityLodBias() { return entityLodBias; }
    public static boolean isScalingActive() { return scalingActive; }
    public static double getSmoothedFrameTimeMs() { return smoothedFrameTimeNs / 1_000_000.0; }
    public static void reset() { shadowDistanceMultiplier = 1.0f; shadowResolutionMultiplier = 1.0f; entityLodBias = 0.0f; scalingActive = false; smoothedFrameTimeNs = 16_600_000L; recoveryFrames = 0; }
}
