package com.civillis.entities.mixin;

import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import civil.civilization.scoring.ScalableCivilizationService;
import com.civillis.entities.CivillisEntitiesAddon;
import com.civillis.entities.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Mixin to inject entity (Marker) scanning into Civillis civilization scoring.
 * 
 * This extends the computeCScoreForChunk method to also scan for settlement markers
 * in the chunk and add their weights to the total civilization score.
 * 
 * Design principle: Follows the same pattern as block weight scanning -
 * entities have configurable weights that contribute to the chunk's L1 score.
 */
@Mixin(ScalableCivilizationService.class)
public class EntityScanningInjectionMixin {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("CEA-EntityScanning");
    
    /**
     * Inject after computing block weights to add entity (Marker) contributions.
     * 
     * This allows CEA's settlement markers to be scanned as part of the normal
     * Civillis L1 score computation, following the same Scan-Score-Decide architecture.
     */
    @Inject(
        method = "computeCScoreForChunk",
        at = @At("RETURN"),
        cancellable = true
    )
    private void injectEntityScanning(ServerLevel world, VoxelChunkKey key, 
                                      CallbackInfoReturnable<Optional<CScore>> cir) {
        Optional<CScore> originalResult = cir.getReturnValue();
        
        // If original computation returned empty or zero, still check for entities
        if (originalResult.isEmpty()) {
            return;
        }
        
        double blockScore = originalResult.get().score();
        
        // Scan for settlement markers in this chunk
        double entityWeight = scanEntitiesInChunk(world, key);
        
        if (entityWeight > 0.0) {
            // Add entity weight to block score
            double totalWeight = blockScore + entityWeight;
            
            // Normalize using Civillis normalization factor
            double normalizedScore = Math.min(1.0, totalWeight / civil.config.CivilConfig.normalizationFactor);
            
            LOGGER.debug("Chunk [{},{},{}]: blockScore={}, entityWeight={}, totalScore={}", 
                key.getCx(), key.getCz(), key.getSy(), 
                String.format("%.4f", blockScore),
                String.format("%.4f", entityWeight),
                String.format("%.4f", normalizedScore));
            
            cir.setReturnValue(Optional.of(new CScore(normalizedScore)));
        }
    }
    
    /**
     * Scan for settlement marker entities in the given chunk and sum their weights.
     * 
     * @param world The server world
     * @param key The voxel chunk key
     * @return Total entity weight in this chunk
     */
    private double scanEntitiesInChunk(ServerLevel world, VoxelChunkKey key) {
        double totalEntityWeight = 0.0;
        
        // Get chunk bounds
        int chunkX = key.getCx();
        int chunkZ = key.getCz();
        int minY = key.getSy() * 16;
        int maxY = minY + 15;
        
        // Calculate chunk boundaries in world coordinates
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        
        // Scan all entities in the world (filtered by chunk bounds)
        for (Entity entity : world.getAllEntities()) {
            if (!entity.isAlive()) {
                continue;
            }
            
            // Check if entity is within this chunk's bounds
            int entityX = entity.getBlockX();
            int entityY = entity.getBlockY();
            int entityZ = entity.getBlockZ();
            
            if (entityX < minX || entityX > maxX || 
                entityZ < minZ || entityZ > maxZ ||
                entityY < minY || entityY > maxY) {
                continue;
            }
            
            // Check if this is a settlement marker
            if (entity.getTags().contains("civillis_settlement_marker")) {
                int markerLevel = entity.getPersistentData().getInt("settlement_level");
                
                // Get weight for this marker level from config
                double weight = getMarkerWeight(markerLevel);
                totalEntityWeight += weight;
                
                if (Config.ENABLE_DEBUG_MODE) {
                    CivillisEntitiesAddon.LOGGER.debug(
                        "[EntityScan] Found L{} marker at ({},{},{}), weight={}",
                        markerLevel, entityX, entityY, entityZ, 
                        String.format("%.2f", weight)
                    );
                }
            }
        }
        
        return totalEntityWeight;
    }
    
    /**
     * Get the civilization weight for a marker of given level.
     * Uses configuration-based weights with fallback to default calculation.
     * 
     * Default calculation (if not configured):
     * - Level 1-3: 50-80 (single chunk influence)
     * - Level 4-5: 120-150 (1-chunk radius diffusion, 9 chunks)
     * - Level 6-8: 200-250 (2-chunk radius diffusion, 25 chunks)
     * - Level 9-10: 300 (maximum influence, tolerance buffer)
     * 
     * @param level Marker level (1-10)
     * @return Weight value
     */
    private double getMarkerWeight(int level) {
        // Aggressive weight design for meaningful civilization impact
        
        if (level <= 3) {
            // L1-3: Basic stage, single chunk only
            // Linear progression: L1=50, L2=65, L3=80
            return 35.0 + level * 15.0;
        } else if (level <= 5) {
            // L4-5: Diffusion to 1-chunk radius (9 chunks)
            // Significant jump to reflect expanded influence
            // L4=120, L5=150
            return 80.0 + level * 20.0;
        } else if (level <= 8) {
            // L6-8: Diffusion to 2-chunk radius (25 chunks)
            // Major jump for maximum influence area
            // L6=200, L7=225, L8=250
            return 170.0 + level * 15.0;
        } else {
            // L9-10: Maximum influence, tolerance buffer
            // L9=280, L10=300
            return 250.0 + (level - 8) * 25.0;
        }
    }
}
