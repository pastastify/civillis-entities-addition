package com.civillis.entities.network;

import com.civillis.entities.CivillisEntitiesAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet to sync chunk heat data from server to client
 */
public record ChunkHeatSyncPacket(
    int chunkX,
    int chunkZ,
    String dimension,
    int heatValue,
    int markerLevel,
    boolean hasMarker,
    int ticksUntilSample
) implements CustomPacketPayload {
    
    public static final Type<ChunkHeatSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CivillisEntitiesAddon.MODID, "chunk_heat_sync")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkHeatSyncPacket> CODEC = 
        new StreamCodec<>() {
            @Override
            public ChunkHeatSyncPacket decode(RegistryFriendlyByteBuf buf) {
                return new ChunkHeatSyncPacket(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt()
                );
            }
            
            @Override
            public void encode(RegistryFriendlyByteBuf buf, ChunkHeatSyncPacket packet) {
                buf.writeInt(packet.chunkX());
                buf.writeInt(packet.chunkZ());
                buf.writeUtf(packet.dimension());
                buf.writeInt(packet.heatValue());
                buf.writeInt(packet.markerLevel());
                buf.writeBoolean(packet.hasMarker());
                buf.writeInt(packet.ticksUntilSample());
            }
        };
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
