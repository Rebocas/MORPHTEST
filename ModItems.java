package com.morphmod.registry;

import com.morphmod.MorphMod;
import com.morphmod.item.MorphControllerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MorphMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MorphMod.MODID);

    public static final RegistryObject<Item> MORPH_CONTROLLER = ITEMS.register("morph_controller",
            () -> new MorphControllerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.morphmod"))
                    .icon(() -> new ItemStack(MORPH_CONTROLLER.get()))
                    .displayItems((params, out) -> out.accept(MORPH_CONTROLLER.get()))
                    .build());

    private ModItems() {}
}
