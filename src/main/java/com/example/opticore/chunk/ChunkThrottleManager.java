package com.example.opticore.chunk;

import com.example.opticore.config.FastAccessConfig;
import com.example.opticore.profiler.MicroProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Adaptive chunk compilation policy. It changes only an advisory budget. */
public final class ChunkThrottleManager {
    public static int compilationsThisFrame;
    private static int maxCompilationsPerFrame = 10;
    private static double lastVelocitySq;

    private ChunkThrottleManager() {}
    public static void resetFrameCounter() { compilationsThisFrame = 0; }

    public static void updateBudget() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        int base;
        if (player == null) {
            base = FastAccessConfig.CHUNK_UPDATES_RESTING;
            lastVelocitySq = 0.0;
        } else {
            double vx = player.getVelocity().x, vy = player.getVelocity().y, vz = player.getVelocity().z;
            lastVelocitySq = vx * vx + vy * vy + vz * vz;
            if (player.isFallFlying()) base = FastAccessConfig.CHUNK_UPDATES_ELYTRA;
            else if (lastVelocitySq > 0.04) base = FastAccessConfig.CHUNK_UPDATES_SPRINTING;
            else base = FastAccessConfig.CHUNK_UPDATES_RESTING;
        }
        double frameMs = MicroProfiler.getAverageFrameTimeMs();
        if (frameMs > 20.0 || VBOUploadBudget.getRecentUploadPressure() > 1.25) base = Math.max(1, base / 2);
        else if (frameMs > 17.5 || VBOUploadBudget.getRecentUploadPressure() > 0.9) base = Math.max(1, (int) Math.ceil(base * 0.75));
        if (FastAccessConfig.chunkLoadingBusy) base = Math.max(1, base / 2);
        maxCompilationsPerFrame = Math.max(1, base);
    }

    public static boolean canCompile() { return compilationsThisFrame < maxCompilationsPerFrame; }
    public static void recordCompilation() { compilationsThisFrame++; }
    public static int getMaxCompilationsPerFrame() { return maxCompilationsPerFrame; }
    public static double getLastVelocitySq() { return lastVelocitySq; }
}
