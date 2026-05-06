package com.civillis.entities.cea;

import com.civillis.entities.CivillisEntitiesAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles saving and loading settlement heatmap data to/from files
 * This ensures persistence across server restarts
 */
public class HeatmapDataPersistence {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "civillis_entities_heatmap.json";
    
    /**
     * Save current heatmap data to file
     */
    public static void saveHeatmap(Path gameDir) {
        try {
            Map<String, HeatmapEntry> dataToSave = new HashMap<>();
            
            for (Map.Entry<String, CEAHeatmap.ChunkHeatData> entry : CEAHeatmap.getHeatmapData().entrySet()) {
                String key = entry.getKey();
                CEAHeatmap.ChunkHeatData heatData = entry.getValue();
                
                HeatmapEntry heatmapEntry = new HeatmapEntry();
                heatmapEntry.chunkX = heatData.chunkX;
                heatmapEntry.chunkZ = heatData.chunkZ;
                heatmapEntry.heat = heatData.getHeat();
                
                // Calculate level from heat value (don't rely on marker entity which may be removed)
                // This ensures level is always consistent with heat
                // HEAT_THRESHOLD = 5 (from CEAHeatmap)
                if (heatData.getHeat() >= 5) {
                    heatmapEntry.level = Math.min(10, heatData.getHeat() / 5);
                } else {
                    heatmapEntry.level = 0;
                }
                
                dataToSave.put(key, heatmapEntry);
            }
            
            Path dataFile = gameDir.resolve(DATA_FILE);
            String json = GSON.toJson(dataToSave);
            Files.writeString(dataFile, json);
            
            CivillisEntitiesAddon.LOGGER.info("Saved heatmap data to {} ({} entries)", dataFile.toAbsolutePath(), dataToSave.size());
        } catch (Exception e) {
            CivillisEntitiesAddon.LOGGER.error("Failed to save heatmap data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load heatmap data from file
     */
    public static void loadHeatmap(Path gameDir) {
        try {
            Path dataFile = gameDir.resolve(DATA_FILE);
            
            if (!Files.exists(dataFile)) {
                CivillisEntitiesAddon.LOGGER.info("No saved heatmap data found at {}", dataFile.toAbsolutePath());
                return;
            }
            
            String json = Files.readString(dataFile);
            Type type = new TypeToken<Map<String, HeatmapEntry>>(){}.getType();
            Map<String, HeatmapEntry> loadedData = GSON.fromJson(json, type);
            
            if (loadedData == null || loadedData.isEmpty()) {
                CivillisEntitiesAddon.LOGGER.info("Loaded heatmap data is empty");
                return;
            }
            
            // Restore heatmap data
            for (Map.Entry<String, HeatmapEntry> entry : loadedData.entrySet()) {
                String key = entry.getKey();
                HeatmapEntry heatmapEntry = entry.getValue();
                
                CEAHeatmap.ChunkHeatData heatData = new CEAHeatmap.ChunkHeatData(
                    heatmapEntry.chunkX, 
                    heatmapEntry.chunkZ
                );
                heatData.setHeat(heatmapEntry.heat);
                
                // Store the level for later marker recreation
                heatData.markerEntity = null; // Will be recreated when processing
                
                CEAHeatmap.restoreHeatmapEntry(key, heatData, heatmapEntry.level);
            }
            
            CivillisEntitiesAddon.LOGGER.info("Loaded heatmap data from {} ({} entries)", dataFile.toAbsolutePath(), loadedData.size());
        } catch (Exception e) {
            CivillisEntitiesAddon.LOGGER.error("Failed to load heatmap data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Data structure for JSON serialization
     */
    private static class HeatmapEntry {
        public int chunkX;
        public int chunkZ;
        public int heat;
        public int level;
    }
}
