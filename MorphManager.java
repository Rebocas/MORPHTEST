package com.morphmod.morph;

import com.morphmod.config.MorphConfig;
import com.morphmod.network.ModNetwork;
import com.morphmod.network.SyncMorphPacket;
import com.morphmod.network.TransformPacket;
import com.morphmod.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fonte unica do estado de morph.
 * Servidor: estado salvo no NBT persistente do jogador (sobrevive a logout/restart).
 * Cliente: cache preenchido pelos pacotes de sincronizacao.
 */
public final class MorphManager {
    public static final String TAG = "MorphModState";
    public static final String FLY_TAG = "MorphModGrantedFly";

    private static final Map<UUID, MorphState> CLIENT_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Transform> CLIENT_TRANSFORMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /** Cache do estado no servidor (evita reler o NBT a cada tick/evento). */
    private static final Map<UUID, MorphState> SERVER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SCALE = new ConcurrentHashMap<>();

    /** Animacao em andamento (lado cliente). So dados, sem classes de cliente. */
    public static final class Transform {
        public final long startTick;
        public final int duration;
        public final MorphState from;
        public final MorphState to;

        Transform(long startTick, int duration, MorphState from, MorphState to) {
            this.startTick = startTick;
            this.duration = Math.max(1, duration);
            this.from = from;
            this.to = to;
        }
    }

    private static final class Pending {
        final MorphState target;
        final int total;
        int left;

        Pending(MorphState target, int total) {
            this.target = target;
            this.total = total;
            this.left = total;
        }
    }

    private MorphManager() {}

    // ------------------------------------------------------------------ leitura (ambos os lados)

    public static MorphState get(Player p) {
        if (p.level().isClientSide) return CLIENT_STATES.getOrDefault(p.getUUID(), MorphState.DEFAULT);
        return SERVER_STATES.computeIfAbsent(p.getUUID(), u -> MorphState.load(p.getPersistentData().getCompound(TAG)));
    }

    /** Descarta o cache do servidor para o jogador (ele sera relido do NBT). */
    public static void invalidate(UUID id) {
        SERVER_STATES.remove(id);
    }

    /** Limpa todo o estado de servidor (parada do servidor / troca de mundo no singleplayer). */
    public static void clearServer() {
        SERVER_STATES.clear();
        PENDING.clear();
        LAST_SCALE.clear();
        MorphAbilities.clearCooldowns();
    }

    /** Limite de frequencia para pedidos de tamanho (anti-spam). */
    public static boolean scaleAllowed(ServerPlayer p) {
        long now = p.level().getGameTime();
        Long last = LAST_SCALE.put(p.getUUID(), now);
        return last == null || now - last >= 3 || now < last;
    }

    public static boolean isTransforming(Player p) {
        return p.level().isClientSide ? CLIENT_TRANSFORMS.containsKey(p.getUUID()) : PENDING.containsKey(p.getUUID());
    }

    public static boolean canUse(Player p) {
        if (p.isSpectator()) return false;
        if (!MorphConfig.REQUIRE_CONTROLLER.get()) return true;
        Item item = ModItems.MORPH_CONTROLLER.get();
        Inventory inv = p.getInventory();
        for (ItemStack st : inv.items) if (st.is(item)) return true;
        for (ItemStack st : inv.offhand) if (st.is(item)) return true;
        return false;
    }

    // ------------------------------------------------------------------ cliente

    public static void clientSet(UUID id, MorphState s) {
        if (s.isDefault()) CLIENT_STATES.remove(id);
        else CLIENT_STATES.put(id, s);
    }

    public static Transform clientTransform(Player p) {
        return CLIENT_TRANSFORMS.get(p.getUUID());
    }

    public static void clientStartTransform(UUID id, long startTick, int duration, MorphState from, MorphState to) {
        CLIENT_TRANSFORMS.put(id, new Transform(startTick, duration, from, to));
    }

    public static void clientEndTransform(UUID id) {
        CLIENT_TRANSFORMS.remove(id);
    }

    public static void clientClear() {
        CLIENT_STATES.clear();
        CLIENT_TRANSFORMS.clear();
    }

    // ------------------------------------------------------------------ servidor

    public static void forget(UUID id) {
        PENDING.remove(id);
        LAST_SCALE.remove(id);
        MorphAbilities.forgetCooldown(id);
    }

    /** Inicia a transformacao animada (2-3 s). O morph so e aplicado no final. */
    public static void requestTransform(ServerPlayer p, MorphState target) {
        UUID id = p.getUUID();
        if (p.isSpectator() || PENDING.containsKey(id) || target.equals(get(p))) return;
        int dur = MorphConfig.TRANSFORM_TICKS.get();
        PENDING.put(id, new Pending(target, dur));
        ModNetwork.toTrackingAndSelf(p, new TransformPacket(id, dur, target));

        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        // Flutua girando, imovel e invulneravel durante a transformacao.
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, dur, 0, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur + 120, 0, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 9, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur + 10, 4, false, false, false));
    }

    public static void tickPending(ServerPlayer p) {
        Pending pd = PENDING.get(p.getUUID());
        if (pd == null) return;
        pd.left--;
        if (pd.left == pd.total / 2) {
            ServerLevel l = p.serverLevel();
            double cy = p.getY() + p.getBbHeight() / 2.0;
            l.sendParticles(ParticleTypes.POOF, p.getX(), cy, p.getZ(), 25, 0.4, 0.4, 0.4, 0.08);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        if (pd.left <= 0) {
            PENDING.remove(p.getUUID());
            p.removeEffect(MobEffects.LEVITATION);
            applyNow(p, pd.target, true);
        }
    }

    public static void applyScale(ServerPlayer p, MorphState s) {
        applyNow(p, s, false);
    }

    private static void applyNow(ServerPlayer p, MorphState target, boolean fx) {
        CompoundTag saved = target.save();
        p.getPersistentData().put(TAG, saved);
        SERVER_STATES.put(p.getUUID(), MorphState.load(saved));
        MorphAbilities.refresh(p, target);
        p.refreshDimensions();
        ModNetwork.toTrackingAndSelf(p, new SyncMorphPacket(p.getUUID(), target));
        if (fx) {
            ServerLevel l = p.serverLevel();
            double cy = p.getY() + p.getBbHeight() / 2.0;
            double w = Math.max(0.4, p.getBbWidth() / 2.0);
            double h = Math.max(0.4, p.getBbHeight() / 2.0);
            l.sendParticles(ParticleTypes.POOF, p.getX(), cy, p.getZ(), 40, w, h, w, 0.1);
            l.sendParticles(ParticleTypes.END_ROD, p.getX(), cy, p.getZ(), 30, w, h, w, 0.15);
            l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), cy, p.getZ(), 30, w, h, w, 0.4);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 1.0f);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** Reaplica atributos/voo e reenvia o estado (login, respawn, troca de dimensao). */
    public static void resync(ServerPlayer p) {
        invalidate(p.getUUID());
        MorphState s = get(p);
        MorphAbilities.refresh(p, s);
        p.refreshDimensions();
        ModNetwork.toTrackingAndSelf(p, new SyncMorphPacket(p.getUUID(), s));
    }
}
