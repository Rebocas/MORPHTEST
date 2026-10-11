package com.morphmod.compat;

import com.morphmod.MorphMod;
import com.morphmod.config.MorphConfig;
import com.morphmod.morph.MorphKind;
import com.morphmod.morph.MorphManager;
import com.morphmod.morph.MorphState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Compatibilidade opcional com o TaCZ (Timeless and Classics Zero).
 * Feita por reflexao: o TaCZ NAO e necessario para compilar nem para rodar o mod.
 * Se a classe do evento nao existir (versao diferente), a compat simplesmente nao liga.
 */
public final class TaczCompat {
    private static final String SHOOT_EVENT = "com.tacz.guns.api.event.common.GunShootEvent";
    private static Method getShooter;

    private TaczCompat() {}

    @SuppressWarnings("unchecked")
    public static void init() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> raw = Class.forName(SHOOT_EVENT, false, TaczCompat.class.getClassLoader());
            getShooter = raw.getMethod("getShooter");
            Class<Event> type = (Class<Event>) raw;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, type, TaczCompat::onShoot);
            MorphMod.LOGGER.info("[morphmod] Compatibilidade com TaCZ ativada.");
        } catch (Throwable t) {
            MorphMod.LOGGER.warn("[morphmod] TaCZ encontrado, mas o evento de tiro nao foi localizado ({}). Compat de tiro desativada.", t.toString());
        }
    }

    private static void onShoot(Event event) {
        try {
            if (!event.isCancelable()) return;
            Object shooter = getShooter.invoke(event);
            if (!(shooter instanceof Player p)) return;
            // Sem atirar no meio da animacao de transformacao.
            if (MorphManager.isTransforming(p)) {
                event.setCanceled(true);
                return;
            }
            // Bloco / barco / carrinho nao seguram arma.
            MorphState s = MorphManager.get(p);
            if (MorphConfig.TACZ_BLOCK_NON_LIVING.get()
                    && (s.kind() == MorphKind.BLOCK || s.kind() == MorphKind.ENTITY)) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
            // nunca derrubar o jogo por causa da compat
        }
    }
}
