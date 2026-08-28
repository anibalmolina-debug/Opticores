package com.example.opticore.chunk;

/** Lightweight hardware profile used to choose conservative worker budgets. */
public record HardwareProfile(int logicalProcessors, int workerThreads, Tier tier) {
    public enum Tier { VERY_LOW, LOW, MID, HIGH }
    public static HardwareProfile detect() {
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());
        int workers;
        Tier tier;
        if (cpus <= 2) { workers = 1; tier = Tier.VERY_LOW; }
        else if (cpus <= 4) { workers = 1; tier = Tier.LOW; }
        else if (cpus <= 8) { workers = Math.min(2, cpus - 1); tier = Tier.MID; }
        else { workers = Math.min(4, cpus / 2); tier = Tier.HIGH; }
        return new HardwareProfile(cpus, Math.max(1, workers), tier);
    }
}
