package com.morphmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.morphmod.MorphMod;
import com.morphmod.morph.MorphKind;
import com.morphmod.morph.MorphState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Desenha o jogador como outra entidade ou bloco, usando um "dummy" que copia o estado do jogador. */
public final class MorphRenderer {
    private record Dummy(EntityType<?> type, Entity entity) {}

    private static final Map<UUID, Dummy> DUMMIES = new HashMap<>();
    private static final Set<EntityType<?>> BROKEN = new HashSet<>();

    private MorphRenderer() {}

    public static void forget(UUID id) {
        DUMMIES.remove(id);
    }

    public static void clear() {
        DUMMIES.clear();
    }

    /** Altura aproximada da forma, usada como centro do giro da animacao. */
    public static float formHeight(MorphState s) {
        float base = 1.8f;
        if (s.kind() == MorphKind.BLOCK) base = 1.0f;
        else if (s.isMorphed() && s.entityType() != null) base = s.entityType().getDimensions().height;
        return base * s.scale();
    }

    /** @return true se desenhou a forma (o chamador deve cancelar o render do jogador). */
    @SuppressWarnings("unchecked")
    public static boolean renderForm(MorphState st, AbstractClientPlayer p, float pt, PoseStack ps,
                                     MultiBufferSource buf, int light) {
        Minecraft mc = Minecraft.getInstance();
        EntityType<?> type = st.entityType();
        ps.pushPose();
        try {
            float sc = st.scale();
            ps.scale(sc, sc, sc);

            if (st.kind() == MorphKind.BLOCK) {
                Block b = st.block();
                if (b == null) return false;
                BlockState bs = orient(b.defaultBlockState(), p);
                RenderShape shape = bs.getRenderShape();
                if (shape == RenderShape.MODEL) {
                    ps.translate(-0.5, 0.0, -0.5);
                    mc.getBlockRenderer().renderSingleBlock(bs, ps, buf, light, OverlayTexture.NO_OVERLAY);
                    return true;
                }
                if (shape == RenderShape.ENTITYBLOCK_ANIMATED && b.asItem() != Items.AIR) {
                    // Baus, camas, shulker boxes, banners, placas, cabecas: desenha pelo modelo do item.
                    ps.translate(0.0, 0.5, 0.0);
                    mc.getItemRenderer().renderStatic(new ItemStack(b.asItem()), ItemDisplayContext.NONE, light,
                            OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                    return true;
                }
                return false;
            }

            if (type == null || BROKEN.contains(type)) return false;
            Entity e = dummy(p, type);
            if (e == null) return false;
            sync(p, e, pt);
            EntityRenderer<Entity> r = (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(e);
            r.render(e, Mth.lerp(pt, p.yRotO, p.getYRot()), pt, ps, buf, light);
            return true;
        } catch (Throwable t) {
            if (type != null && BROKEN.add(type)) {
                MorphMod.LOGGER.error("[morphmod] Falha ao renderizar {}; usando modelo do jogador.", type, t);
            }
            DUMMIES.remove(p.getUUID());
            return false;
        } finally {
            ps.popPose();
        }
    }

    /** Faz blocos com "facing" olharem para onde o jogador olha. */
    private static BlockState orient(BlockState bs, AbstractClientPlayer p) {
        if (bs.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return bs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.fromYRot(p.yBodyRot));
        }
        return bs;
    }

    private static Entity dummy(AbstractClientPlayer p, EntityType<?> type) {
        Dummy d = DUMMIES.get(p.getUUID());
        if (d != null && d.type() == type && d.entity().level() == p.level()) return d.entity();
        Entity e = type.create(p.level());
        if (e == null) {
            BROKEN.add(type);
            return null;
        }
        DUMMIES.put(p.getUUID(), new Dummy(type, e));
        return e;
    }

    private static void sync(AbstractClientPlayer p, Entity e, float pt) {
        e.setPos(p.getX(), p.getY(), p.getZ());
        e.xo = p.xo; e.yo = p.yo; e.zo = p.zo;
        e.xOld = p.xOld; e.yOld = p.yOld; e.zOld = p.zOld;
        e.setYRot(p.getYRot()); e.yRotO = p.yRotO;
        e.setXRot(p.getXRot()); e.xRotO = p.xRotO;
        e.tickCount = p.tickCount;
        e.walkDist = p.walkDist; e.walkDistO = p.walkDistO;
        e.setOnGround(p.onGround());
        e.setSprinting(p.isSprinting());
        e.setShiftKeyDown(p.isShiftKeyDown());

        Pose pose = p.getPose();
        e.setPose(pose == Pose.CROUCHING || pose == Pose.SWIMMING ? pose : Pose.STANDING);

        // Nome do jogador acima da forma (menos para o proprio jogador).
        e.setCustomName(p.getDisplayName());
        e.setCustomNameVisible(p != Minecraft.getInstance().player && !p.isDiscrete());

        if (e instanceof LivingEntity le) {
            le.yBodyRot = p.yBodyRot; le.yBodyRotO = p.yBodyRotO;
            le.yHeadRot = p.yHeadRot; le.yHeadRotO = p.yHeadRotO;
            le.animationSpeed = p.animationSpeed;
            le.animationSpeedOld = p.animationSpeedOld;
            le.animationPosition = p.animationPosition;
            le.hurtTime = p.hurtTime; le.hurtDuration = p.hurtDuration; le.deathTime = p.deathTime;
            le.attackAnim = p.attackAnim; le.oAttackAnim = p.oAttackAnim;
            le.swinging = p.swinging; le.swingTime = p.swingTime; le.swingingArm = p.swingingArm;
            // Itens e armaduras do jogador (arma do TaCZ aparece na mao de mobs humanoides).
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack want = p.getItemBySlot(slot);
                if (!ItemStack.matches(le.getItemBySlot(slot), want)) le.setItemSlot(slot, want.copy());
            }
        }
    }
}
