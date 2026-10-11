package com.morphmod.morph;

import com.morphmod.config.MorphConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lista o que pode virar morph (usada pelo console e para validar no servidor). */
public final class MorphRegistry {
    /** Entidades que nao renderizam, exigem dono/dados ou quebram o renderer. */
    private static final Set<String> HIDDEN = Set.of(
            "minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame",
            "minecraft:leash_knot", "minecraft:lightning_bolt", "minecraft:fishing_bobber",
            "minecraft:player", "minecraft:marker", "minecraft:interaction",
            "minecraft:area_effect_cloud", "minecraft:block_display",
            "minecraft:item_display", "minecraft:text_display");

    private static final Map<MorphKind, List<ResourceLocation>> CACHE = new ConcurrentHashMap<>();

    private MorphRegistry() {}

    public static boolean isLiving(EntityType<?> t) {
        return DefaultAttributes.hasSupplier(t);
    }

    public static List<ResourceLocation> list(MorphKind kind) {
        List<ResourceLocation> all = CACHE.computeIfAbsent(kind, MorphRegistry::build);
        List<? extends String> black = MorphConfig.BLACKLIST.get();
        if (black.isEmpty()) return all;
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : all) if (!black.contains(id.toString())) out.add(id);
        return out;
    }

    public static boolean isAllowed(MorphKind kind, ResourceLocation id) {
        if (id == null || kind == MorphKind.NONE) return false;
        if (MorphConfig.BLACKLIST.get().contains(id.toString())) return false;
        return eligible(kind, id);
    }

    private static List<ResourceLocation> build(MorphKind kind) {
        List<ResourceLocation> out = new ArrayList<>();
        if (kind == MorphKind.BLOCK) {
            for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) if (eligible(kind, id)) out.add(id);
        } else {
            for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) if (eligible(kind, id)) out.add(id);
        }
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    private static boolean eligible(MorphKind kind, ResourceLocation id) {
        if (kind == MorphKind.BLOCK) {
            Block b = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            return b != null && b.asItem() != Items.AIR
                    && b.defaultBlockState().getRenderShape() != RenderShape.INVISIBLE;
        }
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (t == null || !t.canSummon() || HIDDEN.contains(id.toString())) return false;
        boolean living = isLiving(t);
        return kind == MorphKind.MOB ? living : (kind == MorphKind.ENTITY && !living);
    }
}
