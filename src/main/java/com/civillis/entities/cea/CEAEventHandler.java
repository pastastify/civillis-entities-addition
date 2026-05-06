package com.civillis.entities.cea;

import com.civillis.entities.CivillisEntitiesAddon;
import com.civillis.entities.Config;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Event handler for heatmap-based settlement tracking system
 * Periodically samples civilization entity positions to build a heat map
 */
public class CEAEventHandler {
    
    /**
     * Register all settlement event handlers
     */
    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(CEAEventHandler::onServerTick);
        CivillisEntitiesAddon.LOGGER.info("Heatmap-based settlement tracking registered");
    }
    
    /**
     * Server tick handler - delegates to heatmap system
     */
    private static void onServerTick(ServerTickEvent.Post event) {
        // Process all dimensions
        event.getServer().getAllLevels().forEach(level -> {
            if (level.isClientSide()) {
                return;
            }
            
            // Delegate to heatmap system (handles its own timing)
            CEAHeatmap.tick(level);
            
            // Only sync HUD data in debug mode to reduce performance overhead
            if (Config.ENABLE_DEBUG_MODE) {
                syncHUDToAllPlayers(event.getServer(), level);
            }
        });
    }
    
    /**
     * Sync current chunk heat data to all online players for HUD display
     * Only called when debug mode is enabled
     */
    private static void syncHUDToAllPlayers(net.minecraft.server.MinecraftServer server, net.minecraft.world.level.Level level) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        
        // Sync every second (20 ticks) to reduce network overhead
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            int chunkX = player.getBlockX() >> 4;
            int chunkZ = player.getBlockZ() >> 4;
            CEAHeatmap.syncChunkHeatToPlayer(serverLevel, player, chunkX, chunkZ);
        }
    }
}
