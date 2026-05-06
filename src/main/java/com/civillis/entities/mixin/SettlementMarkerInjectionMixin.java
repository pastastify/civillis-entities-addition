package com.civillis.entities.mixin;

import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import civil.civilization.scoring.ScalableCivilizationService;
import com.civillis.entities.Config;
import com.civillis.entities.cea.CEAHeatmap;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Mixin to inject CEA settlement marker data into Civillis civilization scoring
 * 
 * SIMPLIFIED APPROACH: Treat markers like blocks
 * - Markers contribute weight just like blocks do
 * - Weight is added to the chunk's L1 score (CScore)
 * - No complex power calculation or radiation logic here
 */
@Mixin(ScalableCivilizationService.class)
public class SettlementMarkerInjectionMixin {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("CEA-Marker-Mixin");
    
    /**
     * Inject after computeCScoreForChunk to add marker contribution to L1 score
     * This is the simplest approach: markers act like special blocks in the chunk
     * 
     * @param world The server world
     * @param key The voxel chunk key
     * @param cir Callback info containing the computed CScore
     */
    @Inject(
        method = "computeCScoreForChunk",
        at = @At("RETURN"),
        cancellable = true
    )
    private void injectMarkerContribution(ServerLevel world, VoxelChunkKey key, 
                                         CallbackInfoReturnable<Optional<CScore>> cir) {
        // Check if CEA mod is enabled (master switch)
        if (!Config.ENABLE_CEA.getAsBoolean()) {
            return; // CEA disabled, skip injection
        }
        
        Optional<CScore> originalResult = cir.getReturnValue();
        if (originalResult.isEmpty()) {
            return; // Chunk not available, skip
        }
        
        double originalScore = originalResult.get().score();
        
        // Get marker contribution for this specific chunk
        String dim = world.dimension().location().toString();
        String chunkKey = dim + "|" + key.getCx() + "|" + key.getCz();
        
        double markerWeight = CEAHeatmap.getMarkerWeightForChunk(chunkKey);
        
        if (markerWeight > 0) {
            // Add marker weight to the original block score
            double totalWeight = originalScore + markerWeight;
            
            // Normalize using Civillis normalization factor (same as blocks)
            double normalizedScore = Math.min(1.0, totalWeight / civil.config.CivilConfig.normalizationFactor);
            
            LOGGER.debug("Chunk [{},{},{}]: blockScore={}, markerWeight={}, totalScore={}", 
                key.getCx(), key.getCz(), key.getSy(),
                String.format("%.4f", originalScore),
                String.format("%.4f", markerWeight),
                String.format("%.4f", normalizedScore));
            
            cir.setReturnValue(Optional.of(new CScore(normalizedScore)));
        }
    }
}
