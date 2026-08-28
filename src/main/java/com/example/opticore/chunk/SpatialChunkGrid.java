package com.example.opticore.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Small spatial hash for local culling queries. */
public final class SpatialChunkGrid<T> {
    private final int cellSize;
    private final HashMap<Long, ArrayList<T>> cells = new HashMap<>();
    public SpatialChunkGrid(int cellSize) { this.cellSize=Math.max(1,cellSize); }
    public void clear(){cells.clear();}
    public void add(double x,double z,T value){long key=key((int)Math.floor(x/cellSize),(int)Math.floor(z/cellSize));cells.computeIfAbsent(key,k->new ArrayList<>()).add(value);}
    public List<T> query(double cx,double cz,int radiusCells){
        ArrayList<T> out=new ArrayList<>();int gx=(int)Math.floor(cx/cellSize),gz=(int)Math.floor(cz/cellSize);
        for(int x=gx-radiusCells;x<=gx+radiusCells;x++)for(int z=gz-radiusCells;z<=gz+radiusCells;z++){List<T> cell=cells.get(key(x,z));if(cell!=null)out.addAll(cell);}return out;
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
