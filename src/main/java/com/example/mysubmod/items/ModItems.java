package com.example.mysubmod.items;

import com.example.mysubmod.MySubMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MySubMod.MOD_ID);

    // SubMode1 candy (original blue candy)
    public static final RegistryObject<Item> CANDY = ITEMS.register("candy",
        () -> new CandyItem());

    // SubMode2 candies
    public static final RegistryObject<Item> CANDY_BLUE = ITEMS.register("candy_blue",
        () -> new CandyBlueItem());

    public static final RegistryObject<Item> CANDY_RED = ITEMS.register("candy_red",
        () -> new CandyRedItem());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}