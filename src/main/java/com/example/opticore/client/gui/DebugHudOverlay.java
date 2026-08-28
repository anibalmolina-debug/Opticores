package com.example.opticore.client.gui;

import com.example.opticore.chunk.BottleneckDetector;
import com.example.opticore.chunk.VBOUploadBudget;
import com.example.opticore.config.ConfigManager;
import com.example.opticore.profiler.MicroProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class DebugHudOverlay {
    private DebugHudOverlay() {}
    public static void render(DrawContext context, float tickDelta) {
        if (!ConfigManager.CONFIG.debugMode) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud()) return;
        int x = 5, y = 5, color = 0xFFFFFF;
        double frameMs = MicroProfiler.getAverageFrameTimeMs();
        double fps = frameMs <= 0.0 ? 0.0 : 1000.0 / frameMs;
        String[] lines = {
                String.format("OptiCores FPS: %.1f", fps),
                String.format("Frame: %.2f ms | 1%% low: %.1f", frameMs, MicroProfiler.getOnePercentLowFps()),
                String.format("Tick: %.2f ms | Meshing: %.2f ms", MicroProfiler.getAverageTickTimeMs(), MicroProfiler.getAverageMeshingTimeMs()),
                String.format("VBO: %.2f ms avg | %.2f ms frame", VBOUploadBudget.getAverageUploadTimeMs(), VBOUploadBudget.getUploadTimeThisFrameNs() / 1_000_000.0),
                String.format("Bottleneck: %s | CPU %.2f | Chunk %.2f", BottleneckDetector.getCurrent(), BottleneckDetector.getCpuPressure(), BottleneckDetector.getChunkPressure())
        };
        for (String line : lines) { context.drawText(client.textRenderer, line, x, y, color, true); y += 10; }
    }
}
