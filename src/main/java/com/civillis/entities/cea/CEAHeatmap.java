package com.civillis.entities.cea;

import com.civillis.entities.CEALogger;
import com.civillis.entities.CivillisEntitiesAddon;
import com.civillis.entities.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heatmap-based settlement system with periodic sampling
 * - Samples entity positions every N ticks instead of tracking continuously
 * - Accumulates "heat" values at sampled locations
 * - Creates invisible markers only in high-heat areas (frequently visited)
 * - Much better performance than per-entity tracking
 */
public class CEAHeatmap {
    
    /** Key: dimension|chunkX|chunkZ, Value: heat value (accumulated visits) */
    private static final Map<String, ChunkHeatData> heatmap = new ConcurrentHashMap<>();
    
    // Configuration
    private static final long SAMPLING_INTERVAL_MS = 60000L;  // Sample every 1 minute (60000 milliseconds) - uses real time to avoid BetterDays interference
    private static final long DAY_TICKS = 24000L;        // Minecraft day length
    private static final int HEAT_THRESHOLD = 5;         // Minimum heat to create NEW marker
    private static final int MAX_HEAT_VALUE = 110;       // Cap heat to prevent overflow
    
    // Level calculation: level = heat / HEAT_THRESHOLD
    // For 10 villagers over 7 days (spread across ~5 chunks):
    // - Per chunk: ~2 villagers average
    // - Daily samples: 20
    // - Daily heat gain: 2 * 20 = 40
    // - With 15% decay, net daily gain: ~25-30
    // - 7 days total: ~175-210 heat
    // - Level 5 requires: 5 * 5 = 25 heat  
    private static long lastSampleTime = 0;  // Track last sample time in milliseconds (system time)
    private static int tickCounter = 0;  // Still needed for particle rendering intervals
    private static long lastProcessedDay = -1;  // Track last processed day for BetterDays compatibility
    
    /**
     * Calculate marker level from heat value
     * Supports both linear and curve-based calculation modes
     * 
     * Linear mode: level = heat / threshold (capped at 10)
     * Curve mode: level = 1 + 9 × ((heat - threshold) / (max_heat - threshold))^(1/exponent)
     *   - At threshold: level = 1
     *   - At max_heat (100): level = 10
     *   - Curve makes higher levels require exponentially more heat
     * 
     * @param heat Current heat value
     * @return Calculated level (1-10), or 0 if below threshold
     */
    public static int calculateLevelFromHeat(int heat) {
        boolean useLinear = Config.USE_LINEAR_LEVEL_CALCULATION.getAsBoolean();
        double exponent = Config.LEVEL_CURVE_EXPONENT.get();
        
        if (useLinear) {
            // Linear calculation: level = heat / threshold, capped at 10
            return Math.min(10, Math.max(1, heat / HEAT_THRESHOLD));
        } else {
            // Curve-based calculation with proper scaling
            // Formula: level = 1 + 9 × ((heat - threshold) / (max_heat - threshold))^(1/exponent)
            // This ensures:
            // - heat = threshold → level = 1
            // - heat = max_heat → level = 10
            // - Higher exponent = slower progression at high levels
            
            if (heat < HEAT_THRESHOLD) {
                return 0; // Below threshold, no marker
            }
            
            double progress = (double)(heat - HEAT_THRESHOLD) / (MAX_HEAT_VALUE - HEAT_THRESHOLD);
            progress = Math.max(0.0, Math.min(1.0, progress)); // Clamp to [0, 1]
            
            double curvedProgress = Math.pow(progress, 1.0 / exponent);
            int level = 1 + (int)Math.round(curvedProgress * 9.0);
            
            return Math.min(10, Math.max(1, level));
        }
    }
    
    /**
     * Get ticks until next sampling (for HUD display)
     * Converts real-time milliseconds to approximate tick count
     */
    public static int getTicksUntilNextSample() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastSampleTime;
        long remaining = SAMPLING_INTERVAL_MS - elapsed;
        return (int)(remaining / 50); // Convert ms to ticks (50ms per tick)
    }
    
    /**
     * Manually trigger sampling (for debug command)
     * Samples entities, adds heat, but does NOT process settlements immediately
     * This allows heat to accumulate over multiple samples
     * 
     * Note: Manual sampling does NOT apply decay - this is for testing purposes only
     */
    public static void triggerManualSampling(net.minecraft.server.level.ServerLevel level) {
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manual sampling triggered by command (no decay)");
        // Show particles only in debug mode
        sampleCivilizationEntities(level, Config.ENABLE_DEBUG_MODE, false); // false = skip decay
        
        // DO NOT process settlements here - let heat accumulate
        // Settlements are processed separately via /ce_process or daily tick
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manual sampling complete - heat accumulated (use /ce_process to create markers)");
    }
    
    /**
     * Manually trigger settlement processing only (without sampling)
     * Clears all existing markers before recreating to prevent overlaps
     */
    public static void triggerManualSettlementProcessing(net.minecraft.server.level.ServerLevel level) {
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manual settlement processing triggered by command");
        
        // Step 1: Clear all existing markers to prevent overlaps
        clearAllMarkers(level);
        
        // Step 2: Process settlements and recreate markers based on current heat
        processDailyCE(level);
        
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manual settlement processing complete");
    }
    

    /**
     * Clear all existing settlement markers
     * This prevents duplicate/overlapping markers when manually processing
     */
    private static void clearAllMarkers(net.minecraft.server.level.ServerLevel level) {
        int removedMarkers = 0;
        
        // Find and remove ALL settlement marker entities in the world
        // Use a list to avoid concurrent modification
        java.util.List<Entity> markersToRemove = new java.util.ArrayList<>();
        
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains("civillis_settlement_marker")) {
                markersToRemove.add(entity);
            }
        }
        
        // Remove all marker entities
        for (Entity entity : markersToRemove) {
            entity.discard();
            removedMarkers++;
        }
        
        if (removedMarkers > 0) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Cleared {} settlement markers before reprocessing", removedMarkers);
        }
        
        // Also clear references in heatmap data
        heatmap.values().forEach(heatData -> {
            heatData.markerEntity = null;
        });
    }
    
    /**
     * Get heatmap data (for status command)
     * Returns unmodifiable map to prevent external modification
     */
    public static Map<String, ChunkHeatData> getHeatmapData() {
        return java.util.Collections.unmodifiableMap(heatmap);
    }
    
    /**
     * Initialize day tracking for BetterDays compatibility
     * Should be called when world is loaded
     */
    public static void initializeDayTracking(Level level) {
        if (!level.isClientSide()) {
            lastProcessedDay = level.getDayTime() / DAY_TICKS;
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Initialized day tracking: current day = {}", lastProcessedDay);
        }
    }
    
    /**
     * Manually create a marker at specified chunk coordinates (for admin command)
     * First removes any existing marker in that chunk, then creates new one
     * 
     * @param serverLevel The server level
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param level Marker level (1-10)
     * @param heat Heat value to store
     * @return true if successful
     */
    public static boolean createManualMarker(net.minecraft.server.level.ServerLevel serverLevel, int chunkX, int chunkZ, int level, int heat) {
        String dim = serverLevel.dimension().location().toString();
        String chunkKey = dim + "|" + chunkX + "|" + chunkZ;
        
        // Step 1: Remove existing marker if any
        removeManualMarker(serverLevel, chunkX, chunkZ);
        
        // Step 2: Create new heatmap data and marker
        ChunkHeatData heatData = new ChunkHeatData(chunkX, chunkZ);
        heatData.setHeat(heat);
        heatmap.put(chunkKey, heatData);
        
        // Step 3: Create the marker entity
        return createOrUpdateMarker(serverLevel, heatData, level);
    }
    
    /**
     * Manually remove a marker at specified chunk coordinates (for admin command)
     * 
     * @param serverLevel The server level
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return true if marker was removed, false if no marker existed
     */
    public static boolean removeManualMarker(net.minecraft.server.level.ServerLevel serverLevel, int chunkX, int chunkZ) {
        String dim = serverLevel.dimension().location().toString();
        String chunkKey = dim + "|" + chunkX + "|" + chunkZ;
        
        ChunkHeatData heatData = heatmap.get(chunkKey);
        if (heatData == null) {
            return false; // No data for this chunk
        }
        
        boolean removed = false;
        
        // Remove marker entity if exists
        if (heatData.markerEntity != null && !heatData.markerEntity.isRemoved()) {
            heatData.markerEntity.discard();
            heatData.markerEntity = null;
            removed = true;
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manually removed marker at chunk [{},{}]", chunkX, chunkZ);
        }
        
        // Remove from heatmap
        heatmap.remove(chunkKey);
        
        return removed;
    }
    
    /**
     * Main tick handler - called every server tick
     */
    public static void tick(Level level) {
        if (level.isClientSide()) {
            return;
        }
        
        // Check master switch - if CEA is disabled, skip all processing
        // Marker entities remain but Mixin won't inject their weights into Civillis
        if (!Config.ENABLE_CEA.getAsBoolean()) {
            return; // Skip all processing
        }
        
        tickCounter++;
        
        // Periodic tick counter logging for debugging
        if (tickCounter % 1200 == 0) { // Every 60 seconds
            CEALogger.debug(CivillisEntitiesAddon.LOGGER, "[TICK] tickCounter={}, dimension={}", 
                tickCounter, level.dimension().location());
        }
        
        // Render particle digits for all markers in debug mode (every tick for smooth display)
        if (Config.ENABLE_DEBUG_MODE && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            renderParticleDigits(serverLevel);
        }
        
        // Check if a new day has started (compatible with BetterDays)
        // Uses world time instead of tick counter to handle time acceleration
        // IMPORTANT: This must run EVERY tick to catch day changes, regardless of sampling interval
        long currentDay = level.getDayTime() / DAY_TICKS;
        if (currentDay != lastProcessedDay) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "New day detected! Day {} -> {} (world time: {})", 
                lastProcessedDay, currentDay, level.getDayTime());
            
            // Process settlements - create/update/remove markers based on current heat
            // This runs even if sampling is disabled, to maintain existing markers
            processDailyCE(level);
            
            // Update last processed day
            lastProcessedDay = currentDay;
        }
        
        // Only sample at intervals to reduce performance impact
        long now = System.currentTimeMillis();
        if (now - lastSampleTime < SAMPLING_INTERVAL_MS) {
            return;
        }
        
        // Check if manual placement mode is enabled
        if (Config.MANUAL_PLACEMENT_MODE.getAsBoolean()) {
            // Manual mode: skip sampling and decay
            lastSampleTime = now;
        } else {
            // Automatic mode: perform sampling
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Performing settlement heatmap sampling... (real time interval)");
            lastSampleTime = now;
            
            // Use the same logic as /ce_sample command for consistency
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                triggerManualSampling(serverLevel);
            }
        }
    }
    
    /**
     * Render particle digits for all settlement markers in debug mode
     * Called every tick to maintain visible digit display
     */
    private static void renderParticleDigits(net.minecraft.server.level.ServerLevel serverLevel) {
        if (heatmap.isEmpty()) return;
        
        // Render digits for each marker (every 10 ticks to reduce performance impact)
        if (tickCounter % 10 != 0) return;
        
        int aliveCount = 0;
        int removedCount = 0;
        
        for (ChunkHeatData heatData : heatmap.values()) {
            if (heatData.markerEntity == null) {
                continue;
            }
            
            if (heatData.markerEntity.isRemoved()) {
                removedCount++;
                CivillisEntitiesAddon.LOGGER.warn("[TICK CHECK] Marker in chunk [{},{}] is REMOVED! entityId={}, level={}",
                    heatData.chunkX, heatData.chunkZ,
                    heatData.markerEntity.getId(),
                    heatData.markerEntity.getPersistentData().getInt("settlement_level"));
                continue;
            }
            
            aliveCount++;
            int level = heatData.markerEntity.getPersistentData().getInt("settlement_level");
            if (level <= 0) continue;
            
            // Get marker position
            double x = heatData.markerEntity.getX();
            double y = heatData.markerEntity.getY() + 2.0; // 2 blocks above marker
            double z = heatData.markerEntity.getZ();
            
            // Render particle digit
            ParticleDigitRenderer.renderLevel(level, x, y, z, serverLevel);
        }
        
        if (removedCount > 0 && tickCounter % 100 == 0) {
            CivillisEntitiesAddon.LOGGER.warn("[TICK SUMMARY] Alive markers: {}, Removed markers: {}", aliveCount, removedCount);
        }
    }
    
    /**
     * Immediately trigger particle digit rendering for all markers (for /ce_status command)
     * This bypasses the tick counter check and renders particles immediately
     */
    public static void triggerParticleRender(net.minecraft.server.level.ServerLevel serverLevel) {
        if (heatmap.isEmpty()) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "No markers found to render particles");
            return;
        }
        
        int renderedCount = 0;
        
        for (ChunkHeatData heatData : heatmap.values()) {
            if (heatData.markerEntity == null || heatData.markerEntity.isRemoved()) {
                continue;
            }
            
            int level = heatData.markerEntity.getPersistentData().getInt("settlement_level");
            if (level <= 0) continue;
            
            // Get marker position
            double x = heatData.markerEntity.getX();
            double y = heatData.markerEntity.getY() + 2.0; // 2 blocks above marker
            double z = heatData.markerEntity.getZ();
            
            // Render particle digit immediately
            ParticleDigitRenderer.renderLevel(level, x, y, z, serverLevel);
            renderedCount++;
        }
        
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Triggered particle rendering for {} markers", renderedCount);
    }
    
    /**
     * Sample all civilization entities and add heat to their current chunks
     * 
     * @param level The world level
     * @param showDebugParticles Whether to show debug particles on sampled entities
     * @param applyDecay Whether to apply heat decay after sampling (true for natural, false for manual)
     */
    private static void sampleCivilizationEntities(Level level, boolean showDebugParticles, boolean applyDecay) {
        int sampledCount = 0;
        int totalEntitiesChecked = 0;
        
        // Use ServerLevel to get all entities
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "[DEBUG] ServerLevel obtained, checking entities...");
            
            // Get list of entity types to scan from config
            java.util.List<String> entityTypesToScan = getEntityTypesToScan();
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "[DEBUG] Scanning for {} entity types: {}", 
                entityTypesToScan.size(), entityTypesToScan);
            
            // Convert to set for faster lookup
            java.util.Set<String> entityTypeSet = new java.util.HashSet<>(entityTypesToScan);
            
            // Iterate through all entities and check if they match configured types
            for (net.minecraft.world.entity.Entity entity : serverLevel.getAllEntities()) {
                totalEntitiesChecked++;
                if (entity == null || !entity.isAlive()) {
                    continue;
                }
                
                // Check if this entity type is in our scan list
                String entityTypeStr = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                
                if (!entityTypeSet.contains(entityTypeStr)) {
                    continue; // Not a civilization entity
                }
                
                String dim = level.dimension().location().toString();
                int chunkX = entity.getBlockX() >> 4;
                int chunkZ = entity.getBlockZ() >> 4;
                String chunkKey = dim + "|" + chunkX + "|" + chunkZ;
                
                // Get entity weight from config
                double entityWeight = Config.getEntityWeight(entityTypeStr);
                
                // Add heat to this chunk based on entity weight
                ChunkHeatData heatData = heatmap.computeIfAbsent(chunkKey, 
                    k -> new ChunkHeatData(chunkX, chunkZ));
                int oldHeat = heatData.getHeat();
                heatData.addHeat(entityWeight);
                int newHeat = heatData.getHeat();
                
                CEALogger.info(CivillisEntitiesAddon.LOGGER, "[SAMPLE] Entity {} at chunk [{},{}] added {} heat: {} -> {}", 
                    entityTypeStr, chunkX, chunkZ, entityWeight, oldHeat, newHeat);
                
                // Show debug particle effect on sampled entity
                if (showDebugParticles) {
                    CEALogger.debug(CivillisEntitiesAddon.LOGGER, "[PARTICLE] Spawning particles for entity at ({}, {}, {})", 
                        entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
                    spawnDebugParticle(entity);
                }
                
                sampledCount++;
            }
        }
        
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Sampled {} civilization entities (checked {} total entities) across {} chunks", 
            sampledCount, totalEntitiesChecked, heatmap.size());
        
        // Apply heat decay AFTER adding new heat (each sampling period)
        // BUT: Skip decay if manual placement mode is enabled (to preserve manually created markers)
        if (Config.MANUAL_PLACEMENT_MODE.getAsBoolean()) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manual placement mode enabled - skipping heat decay to preserve manual markers");
        } else {
            // Always apply decay, regardless of whether entities were found
            // This ensures heat decreases when no entities are present
            decayHeatValues();
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Heat decayed after sampling (sampled {} entities, checked {} total)", 
                sampledCount, totalEntitiesChecked);
        }
        
        if (totalEntitiesChecked == 0 && tickCounter % 100 == 0) {
            CEALogger.warn(CivillisEntitiesAddon.LOGGER, "No entities found during sampling! This may be because entity chunks are unloaded.");
            CEALogger.warn(CivillisEntitiesAddon.LOGGER, "To maintain heat values, stay near configured entities or use /ce_sample command.");
        }
    }
    
    /**
     * Spawn debug particle effect above entity head
     * Single END_ROD particle that rises straight up from entity head
     */
    private static void spawnDebugParticle(net.minecraft.world.entity.Entity entity) {
        if (entity.level().isClientSide() || !(entity.level() instanceof net.minecraft.server.level.ServerLevel)) {
            return;
        }
        
        double x = entity.getX();
        double y = entity.getY() + entity.getEyeHeight() + 0.8; // Higher above head
        double z = entity.getZ();
        
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) entity.level();
        
        // Spawn single END_ROD particle that rises straight up
        // CRITICAL: Zero spread ensures particle stays at exact position
        // Positive speed on Y axis makes it rise vertically
        serverLevel.sendParticles(
            net.minecraft.core.particles.ParticleTypes.END_ROD,
            x, y, z,
            1,      // count: Only 1 particle per villager
            0.0,    // xSpread: Zero = no horizontal spread
            0.0,    // ySpread: Zero = no vertical spread at spawn
            0.0,    // zSpread: Zero = no depth spread
            0.5     // speed: Upward velocity (particle will rise)
        );
        
        // Log for debugging
        CEALogger.debug(CivillisEntitiesAddon.LOGGER, "[PARTICLE] Spawned END_ROD at ({}, {}, {}) with speed=0.5", x, y, z);
    }
    
    /**
     * Process settlements at end of day - update markers based on current heat
     * Markers can upgrade, downgrade, or be removed based on heat changes
     * 
     * Note: Heat is NOT reset after processing. Heat accumulates continuously and decays over time.
     * This allows for gradual settlement growth and decline based on entity presence.
     */
    private static void processDailyCE(Level level) {
        // Check if manual placement mode is enabled
        if (Config.MANUAL_PLACEMENT_MODE.getAsBoolean()) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Manual placement mode enabled - skipping automatic marker management");
            return;
        }
        
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Processing settlements from heatmap...");
        
        String dim = level.dimension().location().toString();
        int[] counters = new int[3]; // [created, upgraded_downgraded, removed]
        
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Heatmap has {} entries before processing", heatmap.size());
        
        // Debug: Log all heatmap entries and their heat values
        for (Map.Entry<String, ChunkHeatData> entry : heatmap.entrySet()) {
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "[DEBUG] Heatmap entry: key={}, heat={}, threshold={}", 
                entry.getKey(), entry.getValue().getHeat(), HEAT_THRESHOLD);
        }
        
        // Iterate through all chunks with heat data
        heatmap.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (!key.startsWith(dim + "|")) {
                return false; // Only process current dimension
            }
            
            ChunkHeatData heatData = entry.getValue();
            
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "Processing chunk [{},{}] in dimension {}, heat={}", 
                heatData.chunkX, heatData.chunkZ, dim, heatData.getHeat());
            
            // Check if heat meets threshold for having a marker
            if (heatData.getHeat() >= HEAT_THRESHOLD) {
                // Calculate what the marker level should be based on current heat
                int desiredLevel = calculateLevelFromHeat(heatData.getHeat());
                
                CEALogger.info(CivillisEntitiesAddon.LOGGER, "Chunk [{},{}] meets threshold! Heat={}, desiredLevel={}", 
                    heatData.chunkX, heatData.chunkZ, heatData.getHeat(), desiredLevel);
                
                if (heatData.markerEntity == null || heatData.markerEntity.isRemoved()) {
                    // Create new marker
                    CEALogger.info(CivillisEntitiesAddon.LOGGER, "Creating NEW marker for chunk [{},{}]", 
                        heatData.chunkX, heatData.chunkZ);
                    if (createOrUpdateMarker(level, heatData, desiredLevel)) {
                        counters[0]++; // markersCreated
                        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Successfully created marker for chunk [{},{}]", 
                            heatData.chunkX, heatData.chunkZ);
                    } else {
                        CEALogger.warn(CivillisEntitiesAddon.LOGGER, "Failed to create marker for chunk [{},{}]", 
                            heatData.chunkX, heatData.chunkZ);
                    }
                } else {
                    // Marker exists - check if it needs to upgrade/downgrade
                    int currentLevel = heatData.markerEntity.getPersistentData().getInt("settlement_level");
                    if (currentLevel != desiredLevel) {
                        // Level changed - recreate marker with new level
                        createOrUpdateMarker(level, heatData, desiredLevel);
                        counters[1]++; // markersUpgradedOrDowngraded
                        CEALogger.debug(CivillisEntitiesAddon.LOGGER, "Marker in chunk [{},{}] changed from L{} to L{}",
                            heatData.chunkX, heatData.chunkZ, currentLevel, desiredLevel);
                    }
                    // If level is the same, do nothing (keep stable)
                }
                
                // DO NOT reset heat - let it accumulate and decay naturally
                return false; // Keep in map
            } else {
                // Heat below threshold - marker should gradually decay
                CEALogger.info(CivillisEntitiesAddon.LOGGER, "Chunk [{},{}] heat {} below threshold {}",
                    heatData.chunkX, heatData.chunkZ, heatData.getHeat(), HEAT_THRESHOLD);
                
                if (heatData.markerEntity != null && !heatData.markerEntity.isRemoved()) {
                    int currentLevel = heatData.markerEntity.getPersistentData().getInt("settlement_level");
                    
                    if (currentLevel > 1) {
                        // Gradually downgrade by 1 level per processing cycle
                        int newLevel = currentLevel - 1;
                        createOrUpdateMarker(level, heatData, newLevel);
                        counters[1]++; // markersUpgradedOrDowngraded
                        CEALogger.debug(CivillisEntitiesAddon.LOGGER, "Marker in chunk [{},{}] downgraded from L{} to L{} (low heat: {})",
                            heatData.chunkX, heatData.chunkZ, currentLevel, newLevel, heatData.getHeat());
                    } else {
                        // Level 1 with low heat: REMOVE the marker and clean up heatmap data
                        heatData.markerEntity.discard();
                        heatData.markerEntity = null;
                        counters[2]++; // markersRemoved
                        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Marker in chunk [{},{}] removed (L1 with low heat: {})",
                            heatData.chunkX, heatData.chunkZ, heatData.getHeat());
                    }
                }
                
                // Remove from heatmap when heat is too low (allows cleanup of abandoned areas)
                return true; // Remove from heatmap
            }
        });
        
        CEALogger.info(CivillisEntitiesAddon.LOGGER, "Settlement processing complete: {} created, {} upgraded/downgraded, {} removed", 
            counters[0], counters[1], counters[2]);
    }
    
    /**
     * Get particle type based on settlement level
     */
    private static net.minecraft.core.particles.ParticleOptions getParticleForLevel(int level) {
        if (level >= 8) return net.minecraft.core.particles.ParticleTypes.ENCHANT;
        if (level >= 6) return net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
        if (level >= 4) return net.minecraft.core.particles.ParticleTypes.FLAME;
        if (level >= 2) return net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
        return net.minecraft.core.particles.ParticleTypes.SMOKE;
    }
    

    /**
     * Create new marker or update existing one with specified level
     * Uses Marker entity for permanent data storage
     * Marker entities are designed for data storage and are never cleaned up by Minecraft
     */
    private static boolean createOrUpdateMarker(Level level, ChunkHeatData heatData, int markerLevel) {
        if (level.isClientSide()) {
            return false;
        }
        
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return false;
        }
        
        int centerX = (heatData.chunkX << 4) + 8;
        int centerZ = (heatData.chunkZ << 4) + 8;
        // Place at ground level + 10 blocks
        int groundY = level.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
            centerX, centerZ
        );
        int centerY = groundY + 10;
        
        // Check if marker already exists
        if (heatData.markerEntity != null && !heatData.markerEntity.isRemoved()) {
            // Update existing marker (for downgrade/upgrade)
            net.minecraft.world.entity.Marker marker = (net.minecraft.world.entity.Marker) heatData.markerEntity;
            
            // Update level in NBT
            marker.getPersistentData().putInt("settlement_level", markerLevel);
            marker.getPersistentData().putInt("settlement_heat", heatData.getHeat());
            
            CEALogger.debug(CivillisEntitiesAddon.LOGGER, "Updated settlement marker to L{} in chunk [{},{}] (heat={})", 
                markerLevel, heatData.chunkX, heatData.chunkZ, heatData.getHeat());
        } else {
            // Create new Marker entity
            net.minecraft.world.entity.Marker marker = net.minecraft.world.entity.EntityType.MARKER.create(serverLevel);
            if (marker == null) {
                CivillisEntitiesAddon.LOGGER.error("Failed to create Marker entity!");
                return false;
            }
            
            // Set position
            marker.setPos(centerX + 0.5, centerY, centerZ + 0.5);
            
            // Add tags and data
            marker.addTag("civillis_settlement_marker");
            marker.getPersistentData().putInt("settlement_chunk_x", heatData.chunkX);
            marker.getPersistentData().putInt("settlement_chunk_z", heatData.chunkZ);
            marker.getPersistentData().putInt("settlement_level", markerLevel);
            marker.getPersistentData().putInt("settlement_heat", heatData.getHeat());
            
            serverLevel.addFreshEntity(marker);
            heatData.markerEntity = marker;
            
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "[MARKER CHECK] Created Marker entity: entityId={}, position=({}, {}, {}), level={}",
                marker.getId(), marker.getX(), marker.getY(), marker.getZ(), markerLevel);
            
            CEALogger.debug(CivillisEntitiesAddon.LOGGER, "Created settlement marker L{} in chunk [{},{}] (heat={})", 
                markerLevel, heatData.chunkX, heatData.chunkZ, heatData.getHeat());
        }
        
        return true;
    }
    
    /**
     * Gradually decay heat values to simulate abandonment
     * Called every sampling period (before adding new heat)
     * Uses configuration from Config.java for decay mode and values
     */
    private static void decayHeatValues() {
        boolean usePercentageDecay = Config.USE_PERCENTAGE_DECAY.getAsBoolean();
        int heatDecayFixed = Config.HEAT_DECAY_FIXED.getAsInt();
        double heatDecayRate = Config.HEAT_DECAY_RATE.get();
        
        heatmap.values().forEach(heatData -> {
            int currentHeat = heatData.getHeat();
            if (currentHeat > 0) {
                int newHeat;
                
                if (usePercentageDecay) {
                    // Percentage-based decay (alternative mode)
                    int decayAmount = (int)(currentHeat * heatDecayRate);
                    newHeat = Math.max(0, currentHeat - decayAmount);
                } else {
                    // Fixed-value decay (default mode)
                    newHeat = Math.max(0, currentHeat - heatDecayFixed);
                }
                
                heatData.setHeat(newHeat);
            }
        });
    }
    
    /**
     * Check if an entity should be tracked as a civilization entity
     */
    private static boolean isCivilizationEntity(Entity entity) {
        String entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        
        // Get configured entities from config (entityWeights list)
        java.util.List<? extends String> configuredEntities = Config.ENTITY_WEIGHTS.get();
        if (configuredEntities == null || configuredEntities.isEmpty()) {
            return false;
        }
        
        // Check if entity is in the configured list
        for (String entry : configuredEntities) {
            String[] parts = entry.split(":");
            if (parts.length >= 2 && parts[0].equals(entityType)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get list of entity types to scan based on configuration
     * Returns a list of entity type strings (e.g., "minecraft:villager", "minecraft:cow")
     */
    private static java.util.List<String> getEntityTypesToScan() {
        java.util.List<String> entityTypes = new java.util.ArrayList<>();
        
        // Get entities from config (entityWeights list)
        java.util.List<? extends String> configuredEntities = Config.ENTITY_WEIGHTS.get();
        
        // Check if config has valid entries
        boolean hasValidConfig = configuredEntities != null && !configuredEntities.isEmpty();
        
        if (hasValidConfig) {
            // Parse entity types from "namespace:type:weight" format
            for (String entry : configuredEntities) {
                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    String entityType = parts[0] + ":" + parts[1];
                    entityTypes.add(entityType);
                }
            }
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "[CONFIG] Tracking {} entities from config: {}", 
                entityTypes.size(), entityTypes);
        } else {
            // Config list is empty or null: use DEFAULT villager list
            entityTypes.add("minecraft:villager");
            CEALogger.info(CivillisEntitiesAddon.LOGGER, "[CONFIG] No custom entities configured, using default: [minecraft:villager]");
        }
        
        return entityTypes;
    }
    
    /**
     * Get marker weight for a specific chunk (for Mixin integration)
     * Simple approach: treat markers like blocks, convert level to weight
     * 
     * @param chunkKey The chunk key (dimension|chunkX|chunkZ)
     * @return Marker weight contribution, or 0 if no marker in this chunk
     */
    public static double getMarkerWeightForChunk(String chunkKey) {
        ChunkHeatData heatData = heatmap.get(chunkKey);
        if (heatData == null || heatData.markerEntity == null || heatData.markerEntity.isRemoved()) {
            return 0.0;
        }
        
        int markerLevel = heatData.markerEntity.getPersistentData().getInt("settlement_level");
        if (markerLevel <= 0) {
            return 0.0;
        }
        
        // Convert marker level to weight (similar to how blocks have weights)
        // Higher level markers contribute more weight
        // This is added to the block score before normalization
        double weight = markerLevel * 10.0; // L1=10, L5=50, L10=100
        
        return weight;
    }
    
    /**
     * Get total settlement power in an area (for civilization scoring)
     * Power calculation: 
     * - Level 1-5: Full value (level * 10)
     * - Level 6-10: Diminishing returns (only +2 per level)
     * This ensures level 4-5 markers provide near-maximum civilization value
     * 
     * RADIATION MECHANISM:
     * Markers radiate influence to nearby chunks based on their level:
     * - Level 1-3: No radiation (single chunk only)
     * - Level 4-5: 1-chunk radius (9 chunks total)
     * - Level 6-8: 2-chunk radius (25 chunks total)
     * - Level 9-10: 3-chunk radius (49 chunks total)
     * Radiation strength decreases with distance from the marker
     */
    public static int getSettlementPower(Level level, BlockPos center, int radiusBlocks) {
        int totalPower = 0;
        int chunkRadius = radiusBlocks / 16;
        
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        String dim = level.dimension().location().toString();
        
        int markersFound = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                String key = dim + "|" + (centerChunkX + dx) + "|" + (centerChunkZ + dz);
                ChunkHeatData data = heatmap.get(key);
                if (data != null && data.markerEntity != null) {
                    int markerLevel = data.markerEntity.getPersistentData().getInt("settlement_level");
                    
                    // Calculate base power with diminishing returns
                    int basePower;
                    if (markerLevel <= 5) {
                        // Levels 1-5: Full value (10 per level)
                        basePower = markerLevel * 10;
                    } else {
                        // Levels 6-10: Diminishing returns
                        // Level 5 = 50, then +2 per additional level
                        basePower = 50 + (markerLevel - 5) * 2;
                    }
                    
                    // Apply radiation effect - distribute power to surrounding chunks
                    int radiationRadius = getRadiationRadius(markerLevel);
                    
                    if (radiationRadius > 0) {
                        // Calculate distance from this chunk to the marker chunk
                        int markerChunkX = data.chunkX;
                        int markerChunkZ = data.chunkZ;
                        int currentChunkX = centerChunkX + dx;
                        int currentChunkZ = centerChunkZ + dz;
                        
                        int distanceX = Math.abs(currentChunkX - markerChunkX);
                        int distanceZ = Math.abs(currentChunkZ - markerChunkZ);
                        int maxDistance = Math.max(distanceX, distanceZ);
                        
                        if (maxDistance <= radiationRadius) {
                            // Apply distance-based falloff
                            // At distance 0: 100% power
                            // At distance N: (radiationRadius - N + 1) / (radiationRadius + 1) of power
                            double falloffFactor = (double)(radiationRadius - maxDistance + 1) / (radiationRadius + 1);
                            int radiatedPower = (int)(basePower * falloffFactor);
                            
                            totalPower += radiatedPower;
                            markersFound++;
                            
                            if (Config.ENABLE_DEBUG_MODE) {
                                com.civillis.entities.CivillisEntitiesAddon.LOGGER.debug(
                                    "[SettlementPower] Found L{} marker at chunk [{},{}], basePower={}, distance={}, falloff={:.1f}%, radiatedPower={}, totalPower={}",
                                    markerLevel, markerChunkX, markerChunkZ, basePower, maxDistance,
                                    falloffFactor * 100, radiatedPower, totalPower
                                );
                            }
                        }
                    } else {
                        // No radiation - only affects its own chunk
                        if (dx == 0 && dz == 0) {
                            totalPower += basePower;
                            markersFound++;
                            
                            if (Config.ENABLE_DEBUG_MODE) {
                                com.civillis.entities.CivillisEntitiesAddon.LOGGER.debug(
                                    "[SettlementPower] Found L{} marker at chunk [{},{}], basePower={}, totalPower={}",
                                    markerLevel, centerChunkX + dx, centerChunkZ + dz, basePower, totalPower
                                );
                            }
                        }
                    }
                }
            }
        }
        
        if (Config.ENABLE_DEBUG_MODE && markersFound == 0) {
            com.civillis.entities.CivillisEntitiesAddon.LOGGER.warn(
                "[SettlementPower] No markers found in radius {} around [{},{},{}]! Heatmap size: {}",
                radiusBlocks, center.getX(), center.getY(), center.getZ(), heatmap.size()
            );
        }
        
        return totalPower;
    }
    
    /**
     * Get radiation radius based on marker level
     * Higher level markers have larger influence areas
     * 
     * @param markerLevel The marker level (1-10)
     * @return Radiation radius in chunks (0 = no radiation beyond own chunk)
     */
    private static int getRadiationRadius(int markerLevel) {
        if (markerLevel >= 9) {
            return 3; // L9-10: 3-chunk radius (49 chunks total)
        } else if (markerLevel >= 6) {
            return 2; // L6-8: 2-chunk radius (25 chunks total)
        } else if (markerLevel >= 4) {
            return 1; // L4-5: 1-chunk radius (9 chunks total)
        } else {
            return 0; // L1-3: No radiation (single chunk only)
        }
    }
    
    /**
     * Clear all heatmap data
     */
    public static void clearAll() {
        heatmap.values().forEach(data -> {
            if (data.markerEntity != null && !data.markerEntity.isRemoved()) {
                data.markerEntity.discard();
            }
        });
        heatmap.clear();
        tickCounter = 0;
    }
    
    /**
     * Restore a single heatmap entry from loaded data
     * Called by HeatmapDataPersistence when loading from file
     */
    public static void restoreHeatmapEntry(String key, ChunkHeatData heatData, int savedLevel) {
        heatmap.put(key, heatData);
        CivillisEntitiesAddon.LOGGER.debug("Restored heatmap entry: {} (heat={}, level={})", 
            key, heatData.getHeat(), savedLevel);
    }
    
    /**
     * Recreate markers from loaded heatmap data without decaying heat values
     * Called after world load to immediately restore markers with correct levels
     */
    public static void recreateMarkersFromLoadedData(net.minecraft.server.level.ServerLevel level) {
        CivillisEntitiesAddon.LOGGER.info("Recreating markers from loaded heatmap data...");
        
        String dim = level.dimension().location().toString();
        int recreatedCount = 0;
        
        for (ChunkHeatData heatData : heatmap.values()) {
            if (heatData.getHeat() >= HEAT_THRESHOLD) {
                int desiredLevel = calculateLevelFromHeat(heatData.getHeat());
                
                // Create marker directly without going through full processing
                if (createOrUpdateMarker(level, heatData, desiredLevel)) {
                    recreatedCount++;
                    CivillisEntitiesAddon.LOGGER.debug("Recreated marker L{} in chunk [{},{}] (heat={})",
                        desiredLevel, heatData.chunkX, heatData.chunkZ, heatData.getHeat());
                }
            }
        }
        
        CivillisEntitiesAddon.LOGGER.info("Recreated {} markers from loaded data", recreatedCount);
    }
    
    /**
     * Data structure for chunk heat values
     */
    public static class ChunkHeatData {
        public final int chunkX;
        public final int chunkZ;
        public int heat;
        public net.minecraft.world.entity.Entity markerEntity;  // Changed from AreaEffectCloud to Entity to support Marker
        
        public ChunkHeatData(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.heat = 0;
            this.markerEntity = null;
        }
        
        public void addHeat(double amount) {
            this.heat = Math.min(MAX_HEAT_VALUE, (int)(this.heat + amount));
        }
        
        public int getHeat() {
            return heat;
        }
        
        public void setHeat(int value) {
            this.heat = value;
        }
        
        public void resetHeat() {
            this.heat = 0;
        }
    }
    
    /**
     * Get all heatmap data (for Mixin integration)
     * Returns a copy of the internal map to avoid concurrent modification
     */
    public static java.util.Map<String, ChunkHeatData> getAllHeatmapData() {
        return new java.util.HashMap<>(heatmap);
    }
    
    /**
     * Restore settlement markers from existing entities on world load
     * Scans for all entities with "civillis_settlement_marker" tag and rebuilds heatmap data
     * This prevents losing settlement data after restart
     */
    public static void restoreMarkersFromEntities(net.minecraft.server.level.ServerLevel level) {
        // Only restore from overworld to avoid duplicate scanning across dimensions
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            CivillisEntitiesAddon.LOGGER.debug("Skipping marker restoration for non-overworld dimension: {}", 
                level.dimension().location());
            return;
        }
        
        CivillisEntitiesAddon.LOGGER.info("Restoring settlement markers from existing entities...");
        
        // Delay restoration by 20 ticks (1 second) to ensure entities are fully loaded
        level.getServer().execute(() -> {
            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Re-schedule to server thread after sleep
            level.getServer().execute(() -> {
                performRestore(level);
                
                // Recreate any missing markers from heatmap data
                recreateMissingMarkers(level);
                
                CivillisEntitiesAddon.LOGGER.info("World initialization complete");
            });
        });
    }
    
    private static void performRestore(net.minecraft.server.level.ServerLevel level) {
        int restoredCount = 0;
        int totalEntities = 0;
        String dim = level.dimension().location().toString();
        
        CivillisEntitiesAddon.LOGGER.info("Scanning entities in dimension {}...", dim);
        
        // Debug: Check how many entities are in the world
        int entityCount = 0;
        for (Entity e : level.getAllEntities()) {
            entityCount++;
        }
        CivillisEntitiesAddon.LOGGER.info("[DEBUG] Total entities in world: {}", entityCount);
        
        // First pass: Find all marker entities and rebuild heatmap
        for (Entity entity : level.getAllEntities()) {
            totalEntities++;
            if (!entity.getTags().contains("civillis_settlement_marker")) {
                continue;
            }
            
            CivillisEntitiesAddon.LOGGER.info("Found settlement marker entity at [{},{},{}]", 
                entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
            
            // Extract data from entity
            var persistentData = entity.getPersistentData();
            int chunkX = persistentData.getInt("settlement_chunk_x");
            int chunkZ = persistentData.getInt("settlement_chunk_z");
            int markerLevel = persistentData.getInt("settlement_level");
            int heat = persistentData.getInt("settlement_heat");
            
            CivillisEntitiesAddon.LOGGER.debug(
                "Marker data: chunk=[{},{}], level={}, heat={}",
                chunkX, chunkZ, markerLevel, heat
            );
            
            if (chunkX == 0 && chunkZ == 0) {
                CivillisEntitiesAddon.LOGGER.warn("Skipping marker with invalid chunk coordinates [0,0]");
                continue; // Invalid data
            }
            
            // Rebuild heatmap entry
            String chunkKey = dim + "|" + chunkX + "|" + chunkZ;
            ChunkHeatData heatData = new ChunkHeatData(chunkX, chunkZ);
            
            // Restore heat value: use saved heat if available, otherwise calculate from level
            // IMPORTANT: Ensure heat is at least enough to maintain current level
            int restoredHeat = heat > 0 ? heat : (markerLevel * HEAT_THRESHOLD);
            // If heat is below threshold but marker exists, set it to minimum to prevent immediate downgrade
            if (restoredHeat < HEAT_THRESHOLD && markerLevel > 0) {
                restoredHeat = HEAT_THRESHOLD; // Minimum heat to keep marker alive
            }
            heatData.setHeat(restoredHeat);
            heatData.markerEntity = entity;  // No cast needed, already Entity type
            
            heatmap.put(chunkKey, heatData);
            restoredCount++;
            
            CivillisEntitiesAddon.LOGGER.debug("Restored marker L{} in chunk [{},{}] (heat={})", 
                markerLevel, chunkX, chunkZ, heatData.getHeat());
        }
        
        CivillisEntitiesAddon.LOGGER.info("Scanned {} total entities, found {} settlement markers", 
            totalEntities, restoredCount);
        
        if (restoredCount > 0) {
            CivillisEntitiesAddon.LOGGER.info("Restored {} settlement markers from entities", restoredCount);
            
            // Apply current debug mode visibility to all restored markers
            applyVisibilityToAllMarkers(level);
        } else {
            CivillisEntitiesAddon.LOGGER.warn("No existing settlement marker entities found!");
            CivillisEntitiesAddon.LOGGER.info("Will recreate markers from heatmap data if available...");
        }
        
        // CRITICAL: After restoring from entities, also recreate any missing markers
        // This handles the case where heatmap data exists but marker entities were deleted
        recreateMissingMarkers(level);
    }
    
    /**
     * Recreate missing marker entities from heatmap data
     * Called after restoreMarkersFromEntities to ensure all markers exist
     */
    private static void recreateMissingMarkers(net.minecraft.server.level.ServerLevel level) {
        CivillisEntitiesAddon.LOGGER.info("Checking for missing markers...");
        
        int recreatedCount = 0;
        String dim = level.dimension().location().toString();
        
        for (Map.Entry<String, ChunkHeatData> entry : heatmap.entrySet()) {
            if (!entry.getKey().startsWith(dim + "|")) {
                continue; // Only process current dimension
            }
            
            ChunkHeatData heatData = entry.getValue();
            
            // Check if marker entity exists and is valid
            if (heatData.markerEntity != null && !heatData.markerEntity.isRemoved()) {
                continue; // Marker already exists
            }
            
            // Marker is missing - recreate it if heat is sufficient
            if (heatData.getHeat() >= HEAT_THRESHOLD) {
                int desiredLevel = calculateLevelFromHeat(heatData.getHeat());
                
                if (createOrUpdateMarker(level, heatData, desiredLevel)) {
                    recreatedCount++;
                    CivillisEntitiesAddon.LOGGER.info("Recreated missing marker L{} in chunk [{},{}] (heat={})",
                        desiredLevel, heatData.chunkX, heatData.chunkZ, heatData.getHeat());
                }
            }
        }
        
        if (recreatedCount > 0) {
            CivillisEntitiesAddon.LOGGER.info("Recreated {} missing markers", recreatedCount);
        } else {
            CivillisEntitiesAddon.LOGGER.info("No missing markers to recreate");
        }
    }
    
    /**
     * Apply current debug mode visibility settings to all existing markers
     * Called after restoration or config reload to ensure consistency
     * NOTE: Marker entities are always invisible, so this method is now a no-op
     */
    public static void applyVisibilityToAllMarkers(net.minecraft.server.level.ServerLevel level) {
        // Marker entities are always invisible and don't need visibility updates
        CivillisEntitiesAddon.LOGGER.info("Marker entities are always invisible, no visibility update needed");
    }
    
    /**
     * Sync chunk heat data to a specific player (for HUD display)
     * Called every second to update client-side HUD
     */
    public static void syncChunkHeatToPlayer(net.minecraft.server.level.ServerLevel level, 
                                             net.minecraft.server.level.ServerPlayer player,
                                             int chunkX, int chunkZ) {
        if (level.isClientSide()) {
            return;
        }
        
        String dim = level.dimension().location().toString();
        String chunkKey = dim + "|" + chunkX + "|" + chunkZ;
        ChunkHeatData heatData = heatmap.get(chunkKey);
        
        int heatValue = 0;
        int markerLevel = 0;
        boolean hasMarker = false;
        
        if (heatData != null) {
            heatValue = heatData.getHeat();
            
            if (heatData.markerEntity != null && !heatData.markerEntity.isRemoved()) {
                markerLevel = heatData.markerEntity.getPersistentData().getInt("settlement_level");
                hasMarker = true;
            }
        }
        
        // Get ticks until next sample
        int ticksUntilSample = getTicksUntilNextSample();
        
        // Create and send packet
        var packet = new com.civillis.entities.network.ChunkHeatSyncPacket(
            chunkX, chunkZ, dim, heatValue, markerLevel, hasMarker, ticksUntilSample
        );
        
        player.connection.send(packet);
    }
}
