package com.civillis.entities.client;

import com.civillis.entities.Config;
import com.civillis.entities.network.ClientHeatDataCache;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.network.chat.Component;

/**
 * Client-side HUD renderer for settlement debug information
 * Shows current chunk heat and marker level in actionbar
 * Works in both singleplayer and multiplayer (via network sync)
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CEADebugHUD {
    
    private static Component currentMessage = null;
    private static int updateCounter = 0;
    private static final int UPDATE_INTERVAL = 20; // Update every 20 ticks (1 second)
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        
        // Only show in debug mode
        if (!Config.ENABLE_DEBUG_MODE) {
            currentMessage = null;
            return;
        }
        
        // Don't show if game is paused or player is dead
        if (mc.player == null || mc.isPaused()) {
            return;
        }
        
        // Update message every UPDATE_INTERVAL ticks
        updateCounter++;
        if (updateCounter >= UPDATE_INTERVAL) {
            updateCounter = 0;
            updateMessage(mc);
        }
        
        // Set actionbar message
        if (currentMessage != null) {
            mc.gui.setOverlayMessage(currentMessage, false);
        }
    }
    
    private static void updateMessage(Minecraft mc) {
        // Get data from client-side cache (synced from server via packets)
        ClientHeatDataCache.ChunkHeatInfo info = ClientHeatDataCache.getCurrentChunkInfo();
        
        // Build message
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("§e[%d,%d] Heat:%d", info.chunkX, info.chunkZ, info.heatValue));
        
        if (info.hasMarker) {
            String levelColor = getLevelColor(info.markerLevel);
            msg.append(String.format(" %sL%d", levelColor, info.markerLevel));
        } else {
            int potentialLevel = calculatePotentialLevel(info.heatValue);
            if (potentialLevel > 0) {
                msg.append(String.format(" §7(Pending L%d)", potentialLevel));
            }
        }
        
        // Add countdown
        int secondsUntilSample = Math.max(0, info.ticksUntilSample / 20);
        msg.append(String.format(" §f%ds", secondsUntilSample));
        
        currentMessage = Component.literal(msg.toString());
    }
    
    /**
     * Calculate potential marker level from heat value (client-side only)
     */
    private static int calculatePotentialLevel(int heat) {
        if (heat < 5) return 0; // Below threshold
        
        double progress = (double)(heat - 5) / (100 - 5);
        progress = Math.max(0.0, Math.min(1.0, progress));
        
        double curvedProgress = Math.pow(progress, 1.0 / 2.5);
        int level = 1 + (int)Math.round(curvedProgress * 9.0);
        
        return Math.min(10, Math.max(1, level));
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
