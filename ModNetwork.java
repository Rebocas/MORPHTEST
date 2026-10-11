package com.morphmod.network;

import com.morphmod.MorphMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MorphMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private ModNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, MorphActionPacket.class, MorphActionPacket::encode, MorphActionPacket::decode, MorphActionPacket::handle);
        CHANNEL.registerMessage(id++, SyncMorphPacket.class, SyncMorphPacket::encode, SyncMorphPacket::decode, SyncMorphPacket::handle);
        CHANNEL.registerMessage(id++, TransformPacket.class, TransformPacket::encode, TransformPacket::decode, TransformPacket::handle);
    }

    public static void toTrackingAndSelf(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), msg);
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
