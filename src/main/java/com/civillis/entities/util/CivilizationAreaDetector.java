package com.civillis.entities.util;

import com.civillis.entities.CivillisEntitiesAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Utility class for detecting whether entities are within civilization areas
 */
public class CivilizationAreaDetector {
    
    /**
     * Check if an entity is within a civilization area
     * This method checks the chunk's civilization data from Civillis
     * 
     * @param entity The entity to check
     * @return true if the entity is in a civilization area, false otherwise
     */
    public static boolean isInCivilizationArea(Entity entity) {
        if (entity == null || entity.level() == null) {
            return false;
        }
        
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        
        try {
            // Get the chunk at entity position
            ChunkAccess chunk = level.getChunk(pos);
            
            if (chunk instanceof LevelChunk levelChunk) {
                // Check if this chunk has civilization data
                // Note: This is a simplified approach - actual implementation
                // would need to access Civillis's internal civilization data
                
                // For now, we'll use a heuristic based on block density
                // In a real integration, you'd query Civillis's API directly
                return hasCivilizationBlocks(levelChunk, pos);
            }
            
        } catch (Exception e) {
            CivillisEntitiesAddon.LOGGER.warn("Failed to check civilization area for entity: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if a chunk contains civilization blocks near the given position
     * This is a fallback method when direct Civillis integration is not available
     * 
     * @param chunk The chunk to check
     * @param centerPos The center position to check around
     * @return true if civilization blocks are detected
     */
    private static boolean hasCivilizationBlocks(LevelChunk chunk, BlockPos centerPos) {
        // Simplified detection: check for common civilization indicator blocks
        // In production, this should be replaced with actual Civillis API calls
        
        int checkRadius = 8; // Check within 8 blocks horizontally
        
        for (int x = -checkRadius; x <= checkRadius; x += 4) {
            for (int z = -checkRadius; z <= checkRadius; z += 4) {
                BlockPos checkPos = centerPos.offset(x, 0, z);
                
                // Check a few vertical levels
                for (int y = 0; y < 3; y++) {
                    BlockPos pos = checkPos.above(y);
                    
                    // Common civilization indicator blocks
                    var blockState = chunk.getBlockState(pos);
                    var block = blockState.getBlock();
                    
                    // Check for man-made blocks (simplified)
                    String blockName = block.toString().toLowerCase();
                    if (isCivilizationBlock(blockName)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if a block is typically found in civilized areas
     * 
     * @param blockName The block name to check
     * @return true if it's a civilization block
     */
    private static boolean isCivilizationBlock(String blockName) {
        return blockName.contains("planks") ||
               blockName.contains("stone_bricks") ||
               blockName.contains("cobblestone") ||
               blockName.contains("bricks") ||
               blockName.contains("glass") ||
               blockName.contains("door") ||
               blockName.contains("torch") ||
               blockName.contains("lantern") ||
               blockName.contains("fence") ||
               blockName.contains("wall");
    }
    
    /**
     * Check if multiple entities are in civilization areas and return count
     * 
     * @param entities Array of entities to check
     * @return Number of entities in civilization areas
     */
    public static int countEntitiesInCivilizationArea(Entity[] entities) {
        int count = 0;
        for (Entity entity : entities) {
            if (isInCivilizationArea(entity)) {
                count++;
            }
        }
        return count;
    }
}
