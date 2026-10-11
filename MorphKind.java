package com.morphmod.morph;

public enum MorphKind {
    NONE, MOB, ENTITY, BLOCK;

    public static MorphKind byName(String s) {
        for (MorphKind k : values()) if (k.name().equals(s)) return k;
        return NONE;
    }
}
