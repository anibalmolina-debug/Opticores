package com.example.opticore.culling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reuses stable visibility for a short interval. */
public final class TemporalVisibilityCache {
    public record Entry(long cameraCell, long entityCell, long timestampNanos, boolean visible) {}
    private final Map<Integer, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;
    public TemporalVisibilityCache(long ttlNanos) { this.ttlNanos = ttlNanos; }
    public Entry get(int id) { return entries.get(id); }
    public void put(int id, Entry entry) { entries.put(id, entry); }
    public boolean valid(Entry e, long cameraCell, long entityCell, long now) {
        return e != null && e.cameraCell() == cameraCell && e.entityCell() == entityCell && now - e.timestampNanos() <= ttlNanos;
    }
    public void clear() { entries.clear(); }
}
