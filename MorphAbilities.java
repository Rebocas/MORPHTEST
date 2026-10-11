package com.morphmod.morph;

import com.morphmod.config.MorphConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Especialidades dos mobs/blocos: atributos, voo, respirar na agua, imunidades e habilidade ativa. */
public final class MorphAbilities {
    private static final UUID HEALTH_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e01");
    private static final UUID ATTACK_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e02");
    private static final UUID ARMOR_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e03");
    private static final UUID KB_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e04");
    private static final UUID SPEED_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e05");
    private static final UUID BREACH_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e06");
    private static final UUID EREACH_ID = UUID.fromString("5b0c1f8a-3d11-4c7e-9a10-0a1b2c3d4e07");

    private static final Set<String> FLYERS = Set.of("bat", "bee", "parrot", "ghast", "phantom", "allay",
            "vex", "blaze", "ender_dragon", "wither");
    private static final Set<String> NO_FALL = Set.of("bat", "bee", "parrot", "ghast", "phantom", "allay",
            "vex", "blaze", "ender_dragon", "wither", "chicken");
    private static final Set<String> WATER_BREATHERS = Set.of("cod", "salmon", "tropical_fish", "pufferfish",
            "squid", "glow_squid", "guardian", "elder_guardian", "dolphin", "axolotl", "drowned", "tadpole");
    private static final Set<String> DRY_OUT = Set.of("cod", "salmon", "tropical_fish", "pufferfish", "squid", "glow_squid");
    private static final Set<String> NO_WITHER_EFFECT = Set.of("wither", "wither_skeleton");
    private static final Set<String> NO_POISON = Set.of("spider", "cave_spider", "skeleton", "stray", "wither_skeleton",
            "zombie", "husk", "drowned", "zombie_villager", "zombified_piglin", "phantom", "wither", "skeleton_horse", "zoglin");
    private static final Set<String> CLIMBERS = Set.of("spider", "cave_spider");
    private static final Set<String> BLOCK_FIREPROOF = Set.of("magma_block", "obsidian", "crying_obsidian",
            "netherite_block", "ancient_debris", "campfire", "soul_campfire");
    /** Mortos-vivos que queimam sob o sol (se nao estiverem de capacete). */
    private static final Set<String> SUN_BURN = Set.of("zombie", "zombie_villager", "skeleton", "drowned", "phantom");

    // ------------------------------------------------------------------ chaves

    private static String mob(MorphState s) {
        return s.isMorphed() && (s.kind() == MorphKind.MOB || s.kind() == MorphKind.ENTITY)
                && "minecraft".equals(s.id().getNamespace()) ? s.id().getPath() : "";
    }

    private static String blk(MorphState s) {
        return s.isMorphed() && s.kind() == MorphKind.BLOCK && "minecraft".equals(s.id().getNamespace())
                ? s.id().getPath() : "";
    }

    public static boolean isClimber(MorphState s) {
        return CLIMBERS.contains(mob(s));
    }

    public static boolean isFireImmune(MorphState s) {
        EntityType<?> t = s.entityType();
        if (t != null && t.fireImmune()) return true;
        return BLOCK_FIREPROOF.contains(blk(s));
    }

    public static boolean isExplosionImmune(MorphState s) {
        String m = mob(s);
        return m.equals("creeper") || m.equals("tnt") || blk(s).equals("tnt");
    }

    public static boolean immuneToEffect(MorphState s, MobEffect effect) {
        String m = mob(s);
        if (effect == MobEffects.WITHER) return NO_WITHER_EFFECT.contains(m);
        if (effect == MobEffects.POISON) return NO_POISON.contains(m);
        return false;
    }

    public static float blockReduction(MorphState s) {
        if (!MorphConfig.BLOCK_RESISTANCE.get()) return 0f;
        Block b = s.block();
        if (b == null) return 0f;
        float r = b.getExplosionResistance();
        return Mth.clamp(r / (r + 10f), 0f, 0.75f);
    }

    public static void modifyFall(MorphState s, LivingFallEvent e) {
        String m = mob(s);
        String b = blk(s);
        if (NO_FALL.contains(m) || b.equals("slime_block")) e.setDamageMultiplier(0f);
        else if (b.equals("hay_block") || b.equals("honey_block")) e.setDamageMultiplier(0.2f);
        else if (m.equals("goat")) e.setDistance(Math.max(0f, e.getDistance() - 10f));
    }

    public static void thorns(ServerPlayer p, MorphState s, DamageSource src) {
        if (s.kind() != MorphKind.BLOCK) return;
        Entity att = src.getDirectEntity();
        if (!(att instanceof LivingEntity le) || att == p) return;
        switch (blk(s)) {
            case "cactus", "sweet_berry_bush" -> le.hurt(p.damageSources().cactus(), 1.0f);
            case "magma_block" -> le.setSecondsOnFire(3);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ atributos e voo

    private static void setMod(ServerPlayer p, Attribute attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (Math.abs(amount) > 1.0E-4) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /** Remove tudo e reaplica conforme o estado. Idempotente. */
    public static void refresh(ServerPlayer p, MorphState s) {
        float oldMax = p.getMaxHealth();
        float oldHealth = p.getHealth();

        double health = 0, attack = 0, armor = 0, kb = 0;
        double speedRatio = 1.0;

        EntityType<?> t = s.isMorphed() ? s.entityType() : null;
        if (t != null && DefaultAttributes.hasSupplier(t)) {
            @SuppressWarnings("unchecked")
            AttributeSupplier sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) t);
            if (MorphConfig.INHERIT_HEALTH.get() && sup.hasAttribute(Attributes.MAX_HEALTH)) {
                AttributeInstance base = p.getAttribute(Attributes.MAX_HEALTH);
                double playerBase = base == null ? 20.0 : base.getBaseValue();
                health = Math.max(2.0, sup.getBaseValue(Attributes.MAX_HEALTH)) - playerBase;
            }
            if (MorphConfig.INHERIT_ATTACK.get() && sup.hasAttribute(Attributes.ATTACK_DAMAGE)) {
                attack = sup.getBaseValue(Attributes.ATTACK_DAMAGE) - 1.0;
            }
            if (MorphConfig.INHERIT_ARMOR.get()) {
                if (sup.hasAttribute(Attributes.ARMOR)) armor = sup.getBaseValue(Attributes.ARMOR);
                if (sup.hasAttribute(Attributes.KNOCKBACK_RESISTANCE)) kb = sup.getBaseValue(Attributes.KNOCKBACK_RESISTANCE);
            }
            if (MorphConfig.INHERIT_SPEED.get() && sup.hasAttribute(Attributes.MOVEMENT_SPEED)) {
                speedRatio = Mth.clamp(Math.sqrt(sup.getBaseValue(Attributes.MOVEMENT_SPEED) / 0.1), 0.6, 1.8);
            }
        }
        if (s.kind() == MorphKind.BLOCK) speedRatio *= 0.85; // blocos sao pesados

        float scale = s.scale();
        speedRatio *= Mth.clamp(Math.sqrt(scale), 0.5, 2.0);
        double reach = Mth.clamp(scale, 0.5f, 6f) - 1.0;

        setMod(p, Attributes.MAX_HEALTH, HEALTH_ID, "morphmod.health", health, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ATTACK_DAMAGE, ATTACK_ID, "morphmod.attack", attack, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ARMOR, ARMOR_ID, "morphmod.armor", armor, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.KNOCKBACK_RESISTANCE, KB_ID, "morphmod.kb", kb, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.MOVEMENT_SPEED, SPEED_ID, "morphmod.speed", speedRatio - 1.0, AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, ForgeMod.BLOCK_REACH.get(), BREACH_ID, "morphmod.breach", reach, AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, ForgeMod.ENTITY_REACH.get(), EREACH_ID, "morphmod.ereach", reach, AttributeModifier.Operation.MULTIPLY_BASE);

        float newMax = p.getMaxHealth();
        if (p.isAlive() && oldMax > 0 && Math.abs(newMax - oldMax) > 0.01f) {
            p.setHealth(Mth.clamp(oldHealth / oldMax * newMax, 1.0f, newMax)); // mantem a mesma % de vida
        }

        boolean canFly = MorphConfig.ABILITIES.get() && s.kind() == MorphKind.MOB && FLYERS.contains(mob(s));
        setFlight(p, canFly);

        if (s.isDefault()) p.setMaxUpStep(0.6f);
    }

    private static void setFlight(ServerPlayer p, boolean want) {
        Abilities ab = p.getAbilities();
        CompoundTag data = p.getPersistentData();
        if (want) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                data.putBoolean(MorphManager.FLY_TAG, true);
                p.onUpdateAbilities();
            }
        } else if (data.getBoolean(MorphManager.FLY_TAG)) {
            data.remove(MorphManager.FLY_TAG);
            if (!p.isCreative() && !p.isSpectator()) {
                ab.mayfly = false;
                ab.flying = false;
                p.onUpdateAbilities();
            }
        }
    }

    // ------------------------------------------------------------------ tick (servidor)

    public static void tick(ServerPlayer p, MorphState s) {
        if (!MorphConfig.ABILITIES.get() || !s.isMorphed()) return;
        String m = mob(s);

        if (isFireImmune(s) && p.isOnFire()) p.clearFire();

        if (WATER_BREATHERS.contains(m) && p.isInWater()) {
            p.setAirSupply(p.getMaxAirSupply());
            if (p.tickCount % 10 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false, false));
            }
        }
        if (MorphConfig.AQUATIC_SUFFOCATE.get() && DRY_OUT.contains(m) && !p.isInWaterRainOrBubble()
                && p.tickCount % 20 == 0) {
            p.hurt(p.damageSources().dryOut(), 1.0f);
        }
        if (p.tickCount % 20 == 0 && s.kind() == MorphKind.MOB && FLYERS.contains(m) && !p.getAbilities().mayfly) {
            setFlight(p, true); // reaplica se o modo de jogo resetou
        }
        // Mortos-vivos queimam ao sol (exceto com capacete ou na agua/chuva).
        if (p.tickCount % 20 == 0 && SUN_BURN.contains(m) && !p.isCreative() && !p.isSpectator()
                && p.level().isDay() && !p.isInWaterRainOrBubble() && !p.isOnFire()
                && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                && p.level().canSeeSky(BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()))) {
            p.setSecondsOnFire(8);
        }
        // Enderman toma dano da agua e da chuva.
        if (m.equals("enderman") && p.tickCount % 10 == 0 && p.isInWaterRainOrBubble() && !p.isCreative()) {
            p.hurt(p.damageSources().generic(), 1.0f);
        }
    }

    /** Efeitos aplicados ao acertar um alvo em corpo a corpo (veneno da aranha-da-caverna, wither, etc). */
    public static void onMeleeHit(ServerPlayer p, MorphState s, LivingEntity target) {
        if (!MorphConfig.ABILITIES.get() || target == p) return;
        switch (mob(s)) {
            case "cave_spider" -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 0), p);
            case "bee" -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0), p);
            case "husk" -> target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 140, 0), p);
            case "wither_skeleton" -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0), p);
            case "stray" -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0), p);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ habilidade ativa (tecla R)

    private record Ab(int cooldown, BiConsumer<ServerPlayer, MorphState> action) {}

    private static final Map<String, Ab> ABILITIES = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void forgetCooldown(UUID id) {
        COOLDOWNS.remove(id);
    }

    public static void clearCooldowns() {
        COOLDOWNS.clear();
    }

    static {
        ABILITIES.put("creeper", new Ab(80, (p, s) -> explode(p, 3.0f * boost(s), Level.ExplosionInteraction.MOB)));
        ABILITIES.put("tnt", new Ab(100, (p, s) -> explode(p, 4.0f * boost(s), Level.ExplosionInteraction.TNT)));
        ABILITIES.put("block/tnt", new Ab(100, (p, s) -> explode(p, 4.0f * boost(s), Level.ExplosionInteraction.TNT)));
        ABILITIES.put("enderman", new Ab(20, (p, s) -> teleport(p)));
        ABILITIES.put("blaze", new Ab(8, (p, s) -> launch(p, new SmallFireball(p.level(), p, look(p).x, look(p).y, look(p).z), SoundEvents.BLAZE_SHOOT)));
        ABILITIES.put("ghast", new Ab(40, (p, s) -> launch(p, new LargeFireball(p.level(), p, look(p).x, look(p).y, look(p).z, 1), SoundEvents.GHAST_SHOOT)));
        ABILITIES.put("wither", new Ab(20, (p, s) -> launch(p, new WitherSkull(p.level(), p, look(p).x, look(p).y, look(p).z), SoundEvents.WITHER_SHOOT)));
        ABILITIES.put("ender_dragon", new Ab(40, (p, s) -> launch(p, new DragonFireball(p.level(), p, look(p).x, look(p).y, look(p).z), SoundEvents.ENDER_DRAGON_SHOOT)));
        ABILITIES.put("skeleton", new Ab(15, (p, s) -> arrow(p)));
        ABILITIES.put("stray", new Ab(15, (p, s) -> arrow(p)));
        ABILITIES.put("snow_golem", new Ab(6, (p, s) -> snowball(p)));
        ABILITIES.put("chicken", new Ab(200, (p, s) -> {
            p.spawnAtLocation(Items.EGG);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 1f);
        }));
    }

    public static void useAbility(ServerPlayer p) {
        if (!MorphConfig.ABILITIES.get()) return;
        MorphState s = MorphManager.get(p);
        if (!s.isMorphed() || MorphManager.isTransforming(p)) return;
        String key = s.kind() == MorphKind.BLOCK ? "block/" + blk(s) : mob(s);
        Ab ab = ABILITIES.get(key);
        if (ab == null) return;
        long now = p.level().getGameTime();
        Long next = COOLDOWNS.get(p.getUUID());
        if (next != null && now < next) {
            p.displayClientMessage(Component.translatable("message.morphmod.cooldown"), true);
            return;
        }
        COOLDOWNS.put(p.getUUID(), now + ab.cooldown());
        ab.action().accept(p, s);
    }

    private static float boost(MorphState s) {
        return Mth.clamp(s.scale(), 0.5f, 3.0f);
    }

    private static Vec3 look(ServerPlayer p) {
        return p.getLookAngle();
    }

    private static void explode(ServerPlayer p, float power, Level.ExplosionInteraction mode) {
        p.level().explode(p, p.getX(), p.getY(0.5), p.getZ(), power, mode);
    }

    private static void teleport(ServerPlayer p) {
        HitResult hit = p.pick(32.0, 1.0f, false);
        Vec3 target = hit.getLocation();
        if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            target = Vec3.atBottomCenterOf(bhr.getBlockPos().relative(bhr.getDirection()));
        }
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY(0.5), p.getZ(), 40, 0.3, 0.6, 0.3, 0.5);
        p.connection.teleport(target.x, target.y, target.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        l.playSound(null, target.x, target.y, target.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        l.sendParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 40, 0.3, 0.6, 0.3, 0.5);
    }

    private static void launch(ServerPlayer p, AbstractHurtingProjectile proj, SoundEvent sound) {
        Vec3 l = p.getLookAngle();
        proj.setPos(p.getX() + l.x, p.getEyeY() - 0.2 + l.y, p.getZ() + l.z);
        p.level().addFreshEntity(proj);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void arrow(ServerPlayer p) {
        Arrow a = new Arrow(p.level(), p);
        a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0f, 3.0f, 1.0f);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        p.level().addFreshEntity(a);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void snowball(ServerPlayer p) {
        Snowball b = new Snowball(p.level(), p);
        b.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0f, 1.5f, 1.0f);
        p.level().addFreshEntity(b);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.PLAYERS, 1f, 1f);
    }
}
