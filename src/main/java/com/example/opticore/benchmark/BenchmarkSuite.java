package com.example.opticore.benchmark;

import com.example.opticore.profiler.MicroProfiler;
import com.example.opticore.util.LoggerUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Repeatable in-game benchmark. Warm-up is excluded from reported statistics. */
public final class BenchmarkSuite {
    private static final int WARMUP_FRAMES = 600, BENCHMARK_FRAMES = 3600;
    private static boolean running;
    private static int totalFrames, capturedFrames;
    private static double[] frameTimes = new double[BENCHMARK_FRAMES];
    private BenchmarkSuite() {}

    public static void startBenchmark() {
        if (running) return;
        running = true; totalFrames = 0; capturedFrames = 0; frameTimes = new double[BENCHMARK_FRAMES];
        tell("Starting OptiCores benchmark: warm-up 10s, capture 60s.");
        LoggerUtil.info("Starting OptiCores benchmark");
    }

    public static void onFrame(long frameTimeNs) {
        if (!running) return;
        totalFrames++;
        if (totalFrames <= WARMUP_FRAMES) return;
        if (capturedFrames < BENCHMARK_FRAMES) { frameTimes[capturedFrames++] = frameTimeNs / 1_000_000.0; return; }
        finishBenchmark();
    }

    private static void finishBenchmark() {
        running = false;
        java.util.Arrays.sort(frameTimes);
        double sum = 0.0; for (double time : frameTimes) sum += time;
        double avg = sum / BENCHMARK_FRAMES;
        double p99 = frameTimes[Math.min(BENCHMARK_FRAMES - 1, (int)Math.ceil(BENCHMARK_FRAMES * 0.99) - 1)];
        double p999 = frameTimes[Math.min(BENCHMARK_FRAMES - 1, (int)Math.ceil(BENCHMARK_FRAMES * 0.999) - 1)];
        String result = String.format("Benchmark complete | Avg FPS: %.1f | 1%% Low: %.1f | 0.1%% Low: %.1f | Avg Tick: %.2f ms | Avg Meshing: %.2f ms | Heap Growth: %d MB/s",
                1000.0 / Math.max(0.001, avg), 1000.0 / Math.max(0.001, p99), 1000.0 / Math.max(0.001, p999),
                MicroProfiler.getAverageTickTimeMs(), MicroProfiler.getAverageMeshingTimeMs(), MicroProfiler.heapGrowthMbPerSec);
        LoggerUtil.info("================== OPTICORES BENCHMARK ==================");
        LoggerUtil.info(result);
        LoggerUtil.info("=========================================================");
        tell(result);
    }

    private static void tell(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal(message));
    }
}
