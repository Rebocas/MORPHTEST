package com.morphmod.network;

import com.morphmod.client.ClientHandler;
import com.morphmod.morph.MorphState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Servidor -> Cliente: inicia a animacao de transformacao (duracao + forma de destino). */
public class TransformPacket {
    private final UUID player;
    private final int duration;
    private final MorphState target;

    public TransformPacket(UUID player, int duration, MorphState target) {
        this.player = player;
        this.duration = duration;
        this.target = target;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeVarInt(duration);
        target.write(buf);
    }

    public static TransformPacket decode(FriendlyByteBuf buf) {
        return new TransformPacket(buf.readUUID(), buf.readVarInt(), MorphState.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handleTransform(player, duration, target)));
        ctx.setPacketHandled(true);
    }
}
