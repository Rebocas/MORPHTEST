package com.morphmod.network;

import com.morphmod.config.MorphConfig;
import com.morphmod.morph.MorphAbilities;
import com.morphmod.morph.MorphKind;
import com.morphmod.morph.MorphManager;
import com.morphmod.morph.MorphRegistry;
import com.morphmod.morph.MorphState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> Servidor: pedidos feitos pelo console e pela tecla de habilidade. */
public class MorphActionPacket {
    public enum Action { MORPH, RESET, SCALE, ABILITY }

    private final Action action;
    private final MorphKind kind;
    private final ResourceLocation id;
    private final float scale;

    private MorphActionPacket(Action action, MorphKind kind, ResourceLocation id, float scale) {
        this.action = action;
        this.kind = kind;
        this.id = id;
        this.scale = scale;
    }

    public static MorphActionPacket morph(MorphKind kind, ResourceLocation id) {
        return new MorphActionPacket(Action.MORPH, kind, id, 1f);
    }

    public static MorphActionPacket reset() {
        return new MorphActionPacket(Action.RESET, MorphKind.NONE, null, 1f);
    }

    public static MorphActionPacket scale(float s) {
        return new MorphActionPacket(Action.SCALE, MorphKind.NONE, null, s);
    }

    public static MorphActionPacket ability() {
        return new MorphActionPacket(Action.ABILITY, MorphKind.NONE, null, 1f);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeEnum(kind);
        buf.writeUtf(id == null ? "" : id.toString());
        buf.writeFloat(scale);
    }

    public static MorphActionPacket decode(FriendlyByteBuf buf) {
        Action a = buf.readEnum(Action.class);
        MorphKind k = buf.readEnum(MorphKind.class);
        String s = buf.readUtf();
        float sc = buf.readFloat();
        return new MorphActionPacket(a, k, s.isEmpty() ? null : ResourceLocation.tryParse(s), sc);
    }

    public void handle(Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) process(p);
        });
        ctx.setPacketHandled(true);
    }

    private void process(ServerPlayer p) {
        switch (action) {
            case MORPH -> {
                if (!MorphManager.canUse(p) || !MorphRegistry.isAllowed(kind, id)) return;
                MorphManager.requestTransform(p, new MorphState(kind, id, MorphManager.get(p).scale()));
            }
            case RESET -> {
                if (!MorphManager.canUse(p) || MorphManager.get(p).isDefault()) return;
                MorphManager.requestTransform(p, MorphState.DEFAULT);
            }
            case SCALE -> {
                if (!MorphManager.canUse(p) || MorphManager.isTransforming(p) || Float.isNaN(scale)) return;
                if (!MorphManager.scaleAllowed(p)) return;
                float min = MorphConfig.MIN_SCALE.get().floatValue();
                float max = MorphConfig.MAX_SCALE.get().floatValue();
                float clamped = Mth.clamp(scale, min, max);
                MorphState cur = MorphManager.get(p);
                if (Math.abs(cur.scale() - clamped) < 1.0E-3f) return;
                MorphManager.applyScale(p, cur.withScale(clamped));
            }
            case ABILITY -> MorphAbilities.useAbility(p);
        }
    }
}
