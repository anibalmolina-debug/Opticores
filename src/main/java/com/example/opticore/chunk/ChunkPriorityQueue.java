package com.example.opticore.chunk;

/** Pure scoring functions; callers capture camera state before use. */
public final class ChunkPriorityQueue {
    private static final double BEHIND_PENALTY = 65536.0;
    private static final double FRUSTUM_PENALTY = 10000.0;
    private ChunkPriorityQueue() {}
    public record CameraState(double x, double y, double z, double lookX, double lookY, double lookZ) {}
    public static double calculatePriority(int chunkX, int chunkZ, CameraState camera, boolean inFrustum) {
        double cx=(chunkX<<4)+8.0, cz=(chunkZ<<4)+8.0;
        double dx=cx-camera.x(), dz=cz-camera.z(); double score=dx*dx+dz*dz;
        if (dx*camera.lookX()+dz*camera.lookZ()<0) score+=BEHIND_PENALTY;
        if (!inFrustum) score+=FRUSTUM_PENALTY; return score;
    }
    public static double calculateSectionPriority(int sectionX,int sectionY,int sectionZ,CameraState camera,boolean inFrustum){
        double cx=(sectionX<<4)+8.0,cy=(sectionY<<4)+8.0,cz=(sectionZ<<4)+8.0;
        double dx=cx-camera.x(),dy=cy-camera.y(),dz=cz-camera.z();double score=dx*dx+dy*dy+dz*dz;
        if(dx*camera.lookX()+dy*camera.lookY()+dz*camera.lookZ()<0)score+=BEHIND_PENALTY;
        if(!inFrustum)score+=FRUSTUM_PENALTY;return score;
    }
}
