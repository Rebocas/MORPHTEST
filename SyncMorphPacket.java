package com.morphmod.network;

import com.morphmod.client.ClientHandler;
import com.morphmod.morph.MorphState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Servidor -> Cliente: estado atual do morph de um jogador. */
public class SyncMorphPacket {
    private final UUID player;
    private final MorphState state;

    public SyncMorphPacket(UUID player, MorphState state) {
        this.player = player;
        this.state = state;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        state.write(buf);
    }

    public static SyncMorphPacket decode(FriendlyByteBuf buf) {
        return new SyncMorphPacket(buf.readUUID(), MorphState.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handleSync(player, state)));
        ctx.setPacketHandled(true);
    }
}
