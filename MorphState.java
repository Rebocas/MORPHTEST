package com.morphmod.morph;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/** Estado imutavel de morph de um jogador: forma + tamanho. */
public record MorphState(MorphKind kind, @Nullable ResourceLocation id, float scale) {
    public static final MorphState DEFAULT = new MorphState(MorphKind.NONE, null, 1.0f);

    public boolean isMorphed() {
        return kind != MorphKind.NONE && id != null;
    }

    public boolean isDefault() {
        return !isMorphed() && scale == 1.0f;
    }

    @Nullable
    public EntityType<?> entityType() {
        if (id == null || (kind != MorphKind.MOB && kind != MorphKind.ENTITY)) return null;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    @Nullable
    public Block block() {
        if (id == null || kind != MorphKind.BLOCK) return null;
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    public MorphState withScale(float s) {
        return new MorphState(kind, id, s);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Kind", kind.name());
        if (id != null) t.putString("Id", id.toString());
        t.putFloat("Scale", scale);
        return t;
    }

    public static MorphState load(CompoundTag t) {
        if (t == null || t.isEmpty()) return DEFAULT;
        MorphKind k = MorphKind.byName(t.getString("Kind"));
        ResourceLocation id = t.contains("Id") ? ResourceLocation.tryParse(t.getString("Id")) : null;
        float sc = t.contains("Scale") ? t.getFloat("Scale") : 1.0f;
        if (!(sc > 0.01f) || sc > 64f) sc = 1.0f;
        if (id == null) k = MorphKind.NONE;
        return new MorphState(k, k == MorphKind.NONE ? null : id, sc);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeUtf(id == null ? "" : id.toString());
        buf.writeFloat(scale);
    }

    public static MorphState read(FriendlyByteBuf buf) {
        MorphKind k = buf.readEnum(MorphKind.class);
        String s = buf.readUtf();
        float sc = buf.readFloat();
        ResourceLocation id = s.isEmpty() ? null : ResourceLocation.tryParse(s);
        if (id == null) return new MorphState(MorphKind.NONE, null, sc);
        return new MorphState(k, id, sc);
    }
}
