package com.civillis.entities;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(CivillisEntitiesAddon.MODID)
public class CivillisEntitiesAddon {
    public static final String MODID = "civillis_entities";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static ModContainer modContainer;

    public CivillisEntitiesAddon(IEventBus modEventBus, ModContainer modContainer) {
        CivillisEntitiesAddon.modContainer = modContainer;
        LOGGER.info("Civillis Entities Addon initialized!");
        
        // Register networking
        com.civillis.entities.network.CEANetworking.init(modEventBus);
        
        // Register common setup
        modEventBus.addListener(this::commonSetup);
        
        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        // Register settlement tracking handlers
        com.civillis.entities.cea.CEAEventHandler.register(modEventBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Civillis Entities Addon common setup complete");
        LOGGER.info("Entity scanning system ready");
        
        // Register commands - must be done in commonSetup or later
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent cmdEvent) -> {
                com.civillis.entities.command.CEASampleCommand.register(cmdEvent.getDispatcher());
                com.civillis.entities.command.CEAProcessCommand.register(cmdEvent.getDispatcher());
                com.civillis.entities.command.ToggleDebugCommand.register(cmdEvent.getDispatcher());
                com.civillis.entities.command.CEAStatusCommand.register(cmdEvent.getDispatcher());
                com.civillis.entities.command.CEACreateMarkerCommand.register(cmdEvent.getDispatcher());
                com.civillis.entities.command.CEARemoveMarkerCommand.register(cmdEvent.getDispatcher());
                LOGGER.info("Registered /ce_sample, /ce_process, /ce_debug, /ce_status, /ce_create_marker, and /ce_remove_marker commands");
            }
        );
        LOGGER.info("Command listener registered");
        
        // Register world load listener to restore settlement markers
        // IMPORTANT: Use a delayed task to avoid blocking world load
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.level.LevelEvent.Load loadEvent) -> {
                if (loadEvent.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // Only load heatmap data once (from overworld)
                    if (serverLevel.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                        // Schedule loading on server thread with delay to ensure entities are fully loaded
                        serverLevel.getServer().execute(() -> {
                            try {
                                LOGGER.info("World load event triggered - restoring heatmap data for dimension: {}", 
                                    serverLevel.dimension().location());
                                
                                // Step 1: Load heatmap data from JSON file (primary source)
                                java.nio.file.Path gameDir = serverLevel.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                                com.civillis.entities.cea.HeatmapDataPersistence.loadHeatmap(gameDir);
                                
                                // Step 2: Recreate marker entities from loaded heat values
                                com.civillis.entities.cea.CEAHeatmap.recreateMarkersFromLoadedData(serverLevel);
                                
                                // Initialize day tracking for BetterDays compatibility
                                com.civillis.entities.cea.CEAHeatmap.initializeDayTracking(serverLevel);
                                
                                LOGGER.info("Heatmap data restoration complete");
                            } catch (Exception e) {
                                LOGGER.error("Failed to restore heatmap data: {}", e.getMessage(), e);
                            }
                        });
                    }
                }
            }
        );
        LOGGER.info("World load listener registered for heatmap data restoration");
        
        // Register world save listener to persist settlement heatmap data
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.level.LevelEvent.Save saveEvent) -> {
                if (saveEvent.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // Only save heatmap data once (from overworld)
                    if (serverLevel.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                        java.nio.file.Path gameDir = serverLevel.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                        com.civillis.entities.cea.HeatmapDataPersistence.saveHeatmap(gameDir);
                        LOGGER.info("World save event triggered heatmap data persistence for dimension: {}", 
                            serverLevel.dimension().location());
                    }
                }
            }
        );
        LOGGER.info("World save listener registered for heatmap data persistence");
    }
}
