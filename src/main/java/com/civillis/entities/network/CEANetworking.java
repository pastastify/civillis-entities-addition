package com.civillis.entities.network;

import com.civillis.entities.CivillisEntitiesAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network packet registration for CEA mod
 * Handles server-to-client synchronization of chunk heat data
 */
public class CEANetworking {
    
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CEANetworking::registerPackets);
    }
    
    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CivillisEntitiesAddon.MODID)
            .versioned("1.0");
        
        // Register chunk heat sync packet (server -> client)
        // This packet is sent from server to client to update HUD display
        registrar.playToClient(
            ChunkHeatSyncPacket.TYPE,
            ChunkHeatSyncPacket.CODEC,
            (packet, context) -> {
                // This handler only executes on the client side
                context.enqueueWork(() -> {
                    ClientHeatDataCache.updateCurrentChunk(
                        packet.chunkX(),
                        packet.chunkZ(),
                        packet.dimension(),
                        packet.heatValue(),
                        packet.markerLevel(),
                        packet.hasMarker(),
                        packet.ticksUntilSample()
                    );
                });
            }
        );
    }
}
