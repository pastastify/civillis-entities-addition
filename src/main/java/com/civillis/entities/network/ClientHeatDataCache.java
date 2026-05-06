package com.civillis.entities.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for chunk heat data received from server
 * This class is ONLY loaded on the client side
 */
@OnlyIn(Dist.CLIENT)
public class ClientHeatDataCache {
    
    private static volatile ChunkHeatInfo currentChunkInfo = new ChunkHeatInfo(0, 0, "", 0, 0, false, 0);
    
    /**
     * Update current chunk heat data (called when receiving packet)
     */
    public static void updateCurrentChunk(int chunkX, int chunkZ, String dimension, 
                                          int heatValue, int markerLevel, 
                                          boolean hasMarker, int ticksUntilSample) {
        currentChunkInfo = new ChunkHeatInfo(chunkX, chunkZ, dimension, heatValue, markerLevel, hasMarker, ticksUntilSample);
    }
    
    /**
     * Get current chunk heat info
     */
    public static ChunkHeatInfo getCurrentChunkInfo() {
        return currentChunkInfo;
    }
    
    /**
     * Clear cache
     */
    public static void clear() {
        currentChunkInfo = new ChunkHeatInfo(0, 0, "", 0, 0, false, 0);
    }
    
    /**
     * Simple data holder for chunk heat information
     */
    public static class ChunkHeatInfo {
        public final int chunkX;
        public final int chunkZ;
        public final String dimension;
        public final int heatValue;
        public final int markerLevel;
        public final boolean hasMarker;
        public final int ticksUntilSample;
        
        public ChunkHeatInfo(int chunkX, int chunkZ, String dimension, 
                           int heatValue, int markerLevel, boolean hasMarker, int ticksUntilSample) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.heatValue = heatValue;
            this.markerLevel = markerLevel;
            this.hasMarker = hasMarker;
            this.ticksUntilSample = ticksUntilSample;
        }
    }
}
