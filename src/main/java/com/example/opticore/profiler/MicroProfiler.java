package com.example.opticore.profiler;

public class MicroProfiler {
    private static final int HISTORY_SIZE=120;
    public static final long[] tickTimes=new long[HISTORY_SIZE]; private static int tickIndex,tickSamples;
    public static final long[] frameTimes=new long[HISTORY_SIZE]; private static int frameIndex,frameSamples;
    public static final long[] meshingTimes=new long[HISTORY_SIZE]; private static long currentFrameMeshingTime; private static int meshingSamples;
    public static long heapGrowthMbPerSec; private static long lastMemoryUsed,lastHeapCheckTime;
    private static long currentTickStartTime,currentFrameStartTime;
    public static void startTick(long t){currentTickStartTime=t;}
    public static void endTick(long t){if(currentTickStartTime==0)return;tickTimes[tickIndex]=t-currentTickStartTime;tickIndex=(tickIndex+1)%HISTORY_SIZE;tickSamples=Math.min(HISTORY_SIZE,tickSamples+1);currentTickStartTime=0;}
    public static void startFrame(long t){currentFrameStartTime=t;long now=System.currentTimeMillis();if(now-lastHeapCheckTime>=1000){long used=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();heapGrowthMbPerSec=Math.max(0,(used-lastMemoryUsed)/(1024*1024));lastMemoryUsed=used;lastHeapCheckTime=now;}}
    public static long endFrame(long t){if(currentFrameStartTime==0)return 0;long d=t-currentFrameStartTime;frameTimes[frameIndex]=d;meshingTimes[frameIndex]=currentFrameMeshingTime;frameIndex=(frameIndex+1)%HISTORY_SIZE;frameSamples=Math.min(HISTORY_SIZE,frameSamples+1);meshingSamples=Math.min(HISTORY_SIZE,meshingSamples+1);currentFrameMeshingTime=0;currentFrameStartTime=0;return d;}
    public static void recordMeshingTime(long ns){currentFrameMeshingTime+=ns;}
    public static double getAverageTickTimeMs(){return avg(tickTimes,tickSamples)/1e6;}
    public static double getAverageFrameTimeMs(){return avg(frameTimes,frameSamples)/1e6;}
    public static double getAverageMeshingTimeMs(){return avg(meshingTimes,meshingSamples)/1e6;}
    private static double avg(long[] a,int n){if(n<=0)return 0;long sum=0;for(int i=0;i<n;i++)sum+=a[i];return(double)sum/n;}
}
