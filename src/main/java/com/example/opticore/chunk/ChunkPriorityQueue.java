package com.example.opticore.chunk;

/** Pure, allocation-free priority functions for chunk/section scheduling. */
public final class ChunkPriorityQueue {
    private static final double BEHIND_PENALTY = 65536.0;
    private static final double OUTSIDE_FRUSTUM_PENALTY = 10000.0;
    private static final double VELOCITY_BIAS = 4096.0;
    private ChunkPriorityQueue() {}

    public record CameraState(double x, double y, double z,
                              double lookX, double lookY, double lookZ,
                              double velocityX, double velocityY, double velocityZ) {
        public static CameraState stationary(double x, double y, double z,
                                             double lookX, double lookY, double lookZ) {
            return new CameraState(x, y, z, lookX, lookY, lookZ, 0.0, 0.0, 0.0);
        }
    }

    public static double calculatePriority(int chunkX, int chunkZ, CameraState camera, boolean inFrustum) {
        double cx = (chunkX << 4) + 8.0, cz = (chunkZ << 4) + 8.0;
        return score(cx, camera.y(), cz, camera, inFrustum, false);
    }

    public static double calculateSectionPriority(int sectionX, int sectionY, int sectionZ,
                                                  CameraState camera, boolean inFrustum) {
        double cx = (sectionX << 4) + 8.0, cy = (sectionY << 4) + 8.0, cz = (sectionZ << 4) + 8.0;
        return score(cx, cy, cz, camera, inFrustum, true);
    }

    private static double score(double cx, double cy, double cz, CameraState camera,
                                boolean inFrustum, boolean threeDimensional) {
        double dx = cx - camera.x(), dy = cy - camera.y(), dz = cz - camera.z();
        double score = dx * dx + dz * dz + (threeDimensional ? dy * dy : 0.0);
        double facing = dx * camera.lookX() + dy * camera.lookY() + dz * camera.lookZ();
        if (facing < 0.0) score += BEHIND_PENALTY;
        if (!inFrustum) score += OUTSIDE_FRUSTUM_PENALTY;
        double velocityFacing = dx * camera.velocityX() + dz * camera.velocityZ();
        if (velocityFacing > 0.0) score -= Math.min(VELOCITY_BIAS, velocityFacing * 32.0);
        return Math.max(0.0, score);
    }
}
