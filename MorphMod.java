package com.morphmod;

import com.mojang.logging.LogUtils;
import com.morphmod.compat.TaczCompat;
import com.morphmod.config.MorphConfig;
import com.morphmod.network.ModNetwork;
import com.morphmod.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MorphMod.MODID)
public class MorphMod {
    public static final String MODID = "morphmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MorphMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MorphConfig.SPEC);
        bus.addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            TaczCompat.init();
        });
    }
}
