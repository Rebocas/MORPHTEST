package com.morphmod.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class MorphConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue TRANSFORM_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIN_SCALE;
    public static final ForgeConfigSpec.DoubleValue MAX_SCALE;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_CONTROLLER;
    public static final ForgeConfigSpec.BooleanValue INHERIT_HEALTH;
    public static final ForgeConfigSpec.BooleanValue INHERIT_ATTACK;
    public static final ForgeConfigSpec.BooleanValue INHERIT_ARMOR;
    public static final ForgeConfigSpec.BooleanValue INHERIT_SPEED;
    public static final ForgeConfigSpec.BooleanValue ABILITIES;
    public static final ForgeConfigSpec.BooleanValue BLOCK_RESISTANCE;
    public static final ForgeConfigSpec.BooleanValue AQUATIC_SUFFOCATE;
    public static final ForgeConfigSpec.BooleanValue TACZ_BLOCK_NON_LIVING;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("transformation");
        TRANSFORM_TICKS = b.comment("Duracao da animacao de transformacao em ticks (20 ticks = 1s). 40 a 60 = 2 a 3 segundos.")
                .defineInRange("transformTicks", 50, 40, 60);
        b.pop();

        b.push("size");
        MIN_SCALE = b.comment("Menor tamanho permitido.").defineInRange("minScale", 0.1D, 0.0625D, 1.0D);
        MAX_SCALE = b.comment("Maior tamanho permitido.").defineInRange("maxScale", 10.0D, 1.0D, 32.0D);
        b.pop();

        b.push("rules");
        REQUIRE_CONTROLLER = b.comment("Exigir o Controle de Morph no inventario para se transformar.")
                .define("requireController", true);
        INHERIT_HEALTH = b.comment("A vida maxima passa a ser a do mob.").define("inheritHealth", true);
        INHERIT_ATTACK = b.comment("O dano base de ataque passa a ser o do mob.").define("inheritAttack", true);
        INHERIT_ARMOR = b.comment("A armadura/resistencia a knockback passam a ser as do mob.").define("inheritArmor", true);
        INHERIT_SPEED = b.comment("A velocidade passa a ser proporcional a do mob (suavizada).").define("inheritSpeed", true);
        ABILITIES = b.comment("Habilidades especiais (voar, respirar na agua, imunidades, tecla de habilidade).")
                .define("abilities", true);
        BLOCK_RESISTANCE = b.comment("Blocos reduzem dano conforme a resistencia a explosao do bloco.")
                .define("blockResistance", true);
        AQUATIC_SUFFOCATE = b.comment("Peixes e lulas tomam dano fora da agua.").define("aquaticSuffocate", true);
        BLACKLIST = b.comment("IDs proibidos, ex: [\"minecraft:ender_dragon\", \"minecraft:bedrock\"]")
                .defineListAllowEmpty("blacklist", List.of(), o -> o instanceof String);
        b.pop();

        b.push("tacz");
        TACZ_BLOCK_NON_LIVING = b.comment("Impede atirar com armas do TaCZ enquanto for bloco ou entidade nao-viva (barco, carrinho...).")
                .define("blockShootingAsNonLiving", true);
        b.pop();

        SPEC = b.build();
    }

    private MorphConfig() {}
}
