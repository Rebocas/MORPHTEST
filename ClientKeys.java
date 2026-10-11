package com.morphmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.morphmod.MorphMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MorphMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeys {
    public static final KeyMapping ABILITY = new KeyMapping("key.morphmod.ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.morphmod");
    public static final KeyMapping CONSOLE = new KeyMapping("key.morphmod.console", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.morphmod");

    private ClientKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent e) {
        e.register(ABILITY);
        e.register(CONSOLE);
    }
}
