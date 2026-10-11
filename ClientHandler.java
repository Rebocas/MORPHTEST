package com.morphmod.client;

import com.morphmod.morph.MorphManager;
import com.morphmod.morph.MorphState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Recebe os pacotes do servidor (somente cliente). */
public final class ClientHandler {
    private ClientHandler() {}

    public static void handleSync(UUID id, MorphState state) {
        Minecraft mc = Minecraft.getInstance();
        MorphManager.clientSet(id, state);
        MorphManager.clientEndTransform(id);
        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(id);
            if (p != null) {
                if (state.isDefault()) p.setMaxUpStep(0.6f);
                p.refreshDimensions();
            }
        }
    }

    public static void handleTransform(UUID id, int duration, MorphState to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player p = mc.level.getPlayerByUUID(id);
        MorphState from = p != null ? MorphManager.get(p) : MorphState.DEFAULT;
        MorphManager.clientStartTransform(id, mc.level.getGameTime(), duration, from, to);
    }
}
