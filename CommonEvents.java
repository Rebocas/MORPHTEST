package com.morphmod.morph;

import com.morphmod.MorphMod;
import com.morphmod.config.MorphConfig;
import com.morphmod.network.ModNetwork;
import com.morphmod.network.SyncMorphPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MorphMod.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        if (p instanceof ServerPlayer sp) MorphManager.tickPending(sp);

        MorphState s = MorphManager.get(p);
        if (s.isDefault()) return;

        p.setMaxUpStep(0.6f * Math.max(1f, s.scale()));
        if (p instanceof ServerPlayer sp) MorphAbilities.tick(sp, s);

        // Aranhas escalam paredes (roda nos dois lados para o jogador local prever o movimento).
        if (MorphConfig.ABILITIES.get() && MorphAbilities.isClimber(s) && p.horizontalCollision
                && !p.isInWater() && !p.getAbilities().flying) {
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x, p.isShiftKeyDown() ? 0.0 : 0.2, d.z);
            p.fallDistance = 0f;
        }
    }

    /** Hitbox e altura dos olhos acompanham a forma e o tamanho. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p)) return;
        MorphState s = MorphManager.get(p);
        if (s.isDefault()) return;

        EntityDimensions dims = e.getNewSize();
        float eye = e.getNewEyeHeight();
        if (s.isMorphed()) {
            if (s.kind() == MorphKind.BLOCK) {
                dims = EntityDimensions.scalable(0.98f, 0.98f);
                eye = 0.6f * dims.height;
            } else if (s.entityType() != null) {
                // Limita a hitbox base (ex.: dragao 16x8) para o jogador nao ficar preso em paredes.
                EntityDimensions raw = s.entityType().getDimensions();
                dims = EntityDimensions.scalable(Math.min(raw.width, 4.0f), Math.min(raw.height, 4.0f));
                eye = 0.85f * dims.height;
            }
        }
        float sc = s.scale();
        e.setNewSize(EntityDimensions.scalable(dims.width * sc, dims.height * sc));
        e.setNewEyeHeight(eye * sc);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MorphState s = MorphManager.get(p);
        if (!s.isMorphed() || !MorphConfig.ABILITIES.get()) return;
        DamageSource src = e.getSource();
        if (MorphAbilities.isFireImmune(s) && src.is(DamageTypeTags.IS_FIRE)) {
            e.setCanceled(true);
            return;
        }
        if (MorphAbilities.isExplosionImmune(s) && src.is(DamageTypeTags.IS_EXPLOSION)) {
            e.setCanceled(true);
            return;
        }
        MorphAbilities.thorns(p, s, src);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MorphState s = MorphManager.get(p);
        if (s.kind() != MorphKind.BLOCK || e.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        e.setAmount(e.getAmount() * (1f - MorphAbilities.blockReduction(s)));
    }

    /** Efeitos de ataque do mob em que o jogador esta transformado. */
    @SubscribeEvent
    public static void onMorphAttack(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (e.getSource().getDirectEntity() != p) return; // so corpo a corpo
        MorphState s = MorphManager.get(p);
        if (s.isMorphed()) MorphAbilities.onMeleeHit(p, s, e.getEntity());
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player p) || !MorphConfig.ABILITIES.get()) return;
        MorphState s = MorphManager.get(p);
        if (s.isMorphed()) MorphAbilities.modifyFall(s, e);
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable e) {
        if (!(e.getEntity() instanceof Player p) || !MorphConfig.ABILITIES.get()) return;
        MorphState s = MorphManager.get(p);
        if (s.isMorphed() && MorphAbilities.immuneToEffect(s, e.getEffectInstance().getEffect())) {
            e.setResult(Event.Result.DENY);
        }
    }

    // ------------------------------------------------------------------ ciclo de vida

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) MorphManager.resync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) MorphManager.resync(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) MorphManager.resync(sp);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        MorphManager.invalidate(e.getEntity().getUUID());
        if (e.isWasDeath()) return; // morrer desfaz o morph
        CompoundTag from = e.getOriginal().getPersistentData();
        CompoundTag to = e.getEntity().getPersistentData();
        if (from.contains(MorphManager.TAG)) to.put(MorphManager.TAG, from.getCompound(MorphManager.TAG).copy());
        if (from.contains(MorphManager.FLY_TAG)) to.putBoolean(MorphManager.FLY_TAG, from.getBoolean(MorphManager.FLY_TAG));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) MorphManager.forget(sp.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        MorphManager.forget(e.getEntity().getUUID());
        MorphManager.invalidate(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        MorphManager.clearServer();
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof Player target) {
            ModNetwork.toPlayer(viewer, new SyncMorphPacket(target.getUUID(), MorphManager.get(target)));
        }
    }
}
