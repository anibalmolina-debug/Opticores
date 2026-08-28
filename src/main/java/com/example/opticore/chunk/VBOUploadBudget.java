package com.example.opticore.chunk;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Frame-local telemetry for chunk uploads. Advisory only: required uploads are never cancelled. */
public final class VBOUploadBudget {
    private static volatile long maxUploadTimeNs = 4_000_000L;
    private static volatile int maxUploadsPerFrame = 16;
    private static final AtomicInteger uploadsThisFrame = new AtomicInteger();
    private static final AtomicLong uploadTimeThisFrameNs = new AtomicLong();
    private static final ThreadLocal<Long> uploadStartNs = new ThreadLocal<>();
    private static volatile boolean budgetExhausted;
    private static volatile double averageUploadMs;
    private static volatile double recentPressure;

    private VBOUploadBudget() {}
    public static void resetFrame() { uploadsThisFrame.set(0); uploadTimeThisFrameNs.set(0); budgetExhausted = false; recentPressure *= 0.92; }
    public static boolean canUpload() { return !budgetExhausted; }
    public static void beginUpload() { uploadStartNs.set(System.nanoTime()); }
    public static void completeUpload(boolean success) {
        Long start = uploadStartNs.get();
        uploadStartNs.remove();
        if (!success || start == null) return;
        long elapsed = Math.max(0L, System.nanoTime() - start);
        uploadTimeThisFrameNs.addAndGet(elapsed);
        int count = uploadsThisFrame.incrementAndGet();
        double elapsedMs = elapsed / 1_000_000.0;
        averageUploadMs = averageUploadMs == 0.0 ? elapsedMs : averageUploadMs * 0.9 + elapsedMs * 0.1;
        recentPressure = Math.min(3.0, recentPressure * 0.9 + (elapsed / (double) Math.max(1L, maxUploadTimeNs)) * 0.1);
        if (uploadTimeThisFrameNs.get() >= maxUploadTimeNs || count >= maxUploadsPerFrame) budgetExhausted = true;
    }
    public static int getUploadsThisFrame() { return uploadsThisFrame.get(); }
    public static boolean isBudgetExhausted() { return budgetExhausted; }
    public static int getMaxUploadsPerFrame() { return maxUploadsPerFrame; }
    public static double getMaxUploadTimeMs() { return maxUploadTimeNs / 1_000_000.0; }
    public static double getAverageUploadTimeMs() { return averageUploadMs; }
    public static double getRecentUploadPressure() { return recentPressure; }
    public static long getUploadTimeThisFrameNs() { return uploadTimeThisFrameNs.get(); }
    public static void setMaxUploadTimeMs(double ms) { maxUploadTimeNs = Math.max(250_000L, (long) (ms * 1_000_000.0)); }
    public static void setMaxUploadsPerFrame(int count) { maxUploadsPerFrame = Math.max(1, count); }
}
