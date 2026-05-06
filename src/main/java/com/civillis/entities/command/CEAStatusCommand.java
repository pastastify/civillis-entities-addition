package com.civillis.entities.command;

import com.civillis.entities.cea.CEAHeatmap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * Command to immediately display settlement marker levels for all loaded chunks
 * Usage: /ce_status
 * Shows: chunk coordinates, marker level, heat value for each active settlement
 */
public class CEAStatusCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ce")
            .then(Commands.literal("status")
                .requires(source -> source.hasPermission(2)) // Requires operator level 2
                .executes(CEAStatusCommand::execute)));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        
        // Get heatmap data from CEAHeatmap
        Map<String, ?> heatmapData = CEAHeatmap.getHeatmapData();
        
        if (heatmapData.isEmpty()) {
            context.getSource().sendSuccess(
                () -> Component.literal("§eNo settlement markers found in any loaded chunks."),
                true
            );
            return 0;
        }
        
        // Send header
        context.getSource().sendSuccess(
            () -> Component.literal("§6========== Settlement Status =========="),
            true
        );
        
        int markerCount = 0;
        int totalHeat = 0;
        int maxLevel = 0;
        
        // Iterate through all heatmap entries
        for (Map.Entry<String, ?> entry : heatmapData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Parse chunk key (format: "dimension|chunkX|chunkZ")
            String[] parts = key.split("\\|");
            if (parts.length < 3) continue;
            
            String dimension = parts[0];
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);
            
            // Get heat data
            if (value instanceof com.civillis.entities.cea.CEAHeatmap.ChunkHeatData heatData) {
                int heat = heatData.getHeat();
                
                // DEBUG: Log heatmap entry details
                context.getSource().sendSuccess(
                    () -> Component.literal(String.format("§7[DEBUG] Chunk (%d, %d): heat=%d, markerEntity=%s",
                        chunkX, chunkZ, heat,
                        heatData.markerEntity == null ? "null" : (heatData.markerEntity.isRemoved() ? "REMOVED" : "ALIVE"))),
                    true
                );
                
                // Get marker level if exists
                if (heatData.markerEntity != null && !heatData.markerEntity.isRemoved()) {
                    int markerLevel = heatData.markerEntity.getPersistentData().getInt("settlement_level");
                    int markerHeat = heatData.markerEntity.getPersistentData().getInt("settlement_heat");
                    
                    // Format color based on level
                    String levelColor = getLevelColor(markerLevel);
                    String markerInfo = String.format(
                        "§7[%s] Chunk (%d, %d) → %sL%d§7 (Heat: %d, Current Heat: %d)",
                        dimension, chunkX, chunkZ, levelColor, markerLevel, markerHeat, heat
                    );
                    
                    context.getSource().sendSuccess(
                        () -> Component.literal(markerInfo),
                        true
                    );
                    
                    markerCount++;
                    totalHeat += markerHeat; // Use marker's stored heat for summary
                    maxLevel = Math.max(maxLevel, markerLevel);
                } else {
                    // No marker yet - show heat data with pending status
                    // Calculate what level this would be if processed
                    int potentialLevel = com.civillis.entities.cea.CEAHeatmap.calculateLevelFromHeat(heat);
                    String statusColor = potentialLevel > 0 ? "§e" : "§7";
                    String statusText = potentialLevel > 0 ? "§7[PENDING]" : "§7[BELOW_THRESHOLD]";
                    
                    String pendingInfo = String.format(
                        "%s [%s] Chunk (%d, %d) → Heat: %d (Would be L%d)",
                        statusColor, dimension, chunkX, chunkZ, heat, potentialLevel
                    );
                    
                    context.getSource().sendSuccess(
                        () -> Component.literal(pendingInfo),
                        true
                    );
                    
                    totalHeat += heat;
                }
            }
        }
        
        // Send summary
        String summary = String.format(
            "§6Total: §e%d markers§6 | Max Level: §eL%d§6 | Total Heat: §e%d",
            markerCount, maxLevel, totalHeat
        );
        
        context.getSource().sendSuccess(
            () -> Component.literal(summary),
            true
        );
        
        context.getSource().sendSuccess(
            () -> Component.literal("§6======================================"),
            true
        );
        
        // Immediately trigger particle digit rendering for all markers
        // This ensures players can see the levels right away
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.civillis.entities.cea.CEAHeatmap.triggerParticleRender(serverLevel);
            context.getSource().sendSuccess(
                () -> Component.literal("§aParticle digits rendered! Check marker locations."),
                true
            );
        }
        
        return 1;
    }
    
    /**
     * Get color code based on settlement level
     */
    private static String getLevelColor(int level) {
        if (level >= 8) return "§d"; // Purple (L8-10)
        if (level >= 6) return "§b"; // Light blue (L6-7)
        if (level >= 4) return "§e"; // Yellow (L4-5)
        if (level >= 2) return "§a"; // Green (L2-3)
        return "§7"; // Gray (L1)
    }
}
