package com.morphmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.morphmod.MorphMod;
import com.morphmod.morph.MorphManager;
import com.morphmod.morph.MorphState;
import com.morphmod.network.ModNetwork;
import com.morphmod.network.MorphActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MorphMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    // ------------------------------------------------------------------ render

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        if (!(e.getEntity() instanceof AbstractClientPlayer p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MorphState cur = MorphManager.get(p);
        MorphManager.Transform tr = MorphManager.clientTransform(p);
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();

        // Sem animacao: forma fixa (ou apenas tamanho alterado).
        if (tr == null) {
            if (cur.isMorphed()) {
                if (MorphRenderer.renderForm(cur, p, pt, ps, e.getMultiBufferSource(), e.getPackedLight())) {
                    e.setCanceled(true);
                }
            } else if (cur.scale() != 1.0f) {
                ps.scale(cur.scale(), cur.scale(), cur.scale());
            }
            return;
        }

        // Animacao de transformacao: gira acelerando, encolhe com "squash & stretch",
        // troca de forma no meio (quando esta minusculo) e cresce de volta.
        float t = Mth.clamp((mc.level.getGameTime() - tr.startTick + pt) / tr.duration, 0f, 1f);
        MorphState shown = t < 0.5f ? tr.from : tr.to;

        float center = MorphRenderer.formHeight(shown) * 0.5f;
        float spin = 1440f * t * t;                         // 4 voltas completas, termina alinhado
        float shrink = 1f - 0.8f * Mth.sin((float) Math.PI * t);
        float wob = Mth.sin(t * 38f);
        float sx = shrink * (1f - 0.10f * wob);
        float sy = shrink * (1f + 0.14f * wob);

        if (shown.isMorphed()) {
            ps.pushPose();
            applyTransform(ps, center, spin, sx, sy);
            boolean ok = MorphRenderer.renderForm(shown, p, pt, ps, e.getMultiBufferSource(), e.getPackedLight());
            ps.popPose();
            if (ok) {
                e.setCanceled(true);
                return;
            }
        }
        // Forma do jogador (ou fallback): aplica a animacao e deixa o render normal seguir.
        applyTransform(ps, center, spin, sx, sy);
        if (shown.scale() != 1.0f) ps.scale(shown.scale(), shown.scale(), shown.scale());
    }

    private static void applyTransform(PoseStack ps, float center, float spin, float sx, float sy) {
        ps.translate(0.0, center, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        ps.scale(sx, sy, sx);
        ps.translate(0.0, -center, 0.0);
    }

    // ------------------------------------------------------------------ particulas da animacao

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        Level lvl = p.level();
        if (!lvl.isClientSide) return;
        MorphManager.Transform tr = MorphManager.clientTransform(p);
        if (tr == null) return;

        long now = lvl.getGameTime();
        float t = Mth.clamp((now - tr.startTick) / (float) tr.duration, 0f, 1f);
        double h = Math.max(0.6, p.getBbHeight());
        double rad = Math.max(0.6, p.getBbWidth()) * (1.0 - 0.6 * Math.sin(Math.PI * t)) + 0.3;
        RandomSource r = lvl.random;

        for (int i = 0; i < 3; i++) {
            double ang = now * 0.55 + i * (Math.PI * 2.0 / 3.0);
            double x = p.getX() + Math.cos(ang) * rad;
            double z = p.getZ() + Math.sin(ang) * rad;
            double y = p.getY() + ((now * 0.07 + i * 0.4) % h);
            if (i == 0) lvl.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.02, 0);
            else if (i == 1) lvl.addParticle(ParticleTypes.WITCH, x, y, z, 0, 0.02, 0);
            else lvl.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, 0.02, 0);
        }
        if (now % 2 == 0) {
            // notas coloridas (arco-iris) subindo em volta
            lvl.addParticle(ParticleTypes.NOTE, p.getX() + (r.nextDouble() - 0.5) * rad * 2,
                    p.getY() + h * (0.4 + 0.6 * r.nextDouble()), p.getZ() + (r.nextDouble() - 0.5) * rad * 2,
                    r.nextInt(24) / 24.0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ teclas

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (ClientKeys.ABILITY.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(MorphActionPacket.ability());
        }
        while (ClientKeys.CONSOLE.consumeClick()) {
            if (mc.screen != null) continue;
            if (MorphManager.canUse(mc.player)) mc.setScreen(new ConsoleScreen());
            else mc.player.displayClientMessage(Component.translatable("message.morphmod.no_controller"), true);
        }
    }

    // ------------------------------------------------------------------ limpeza

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof Player p) MorphRenderer.forget(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        MorphManager.clientClear();
        MorphRenderer.clear();
    }
}
