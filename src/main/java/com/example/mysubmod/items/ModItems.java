package com.example.mysubmod.items;

import com.example.mysubmod.MySubMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MySubMod.MOD_ID);

    //PNG 2D FAIT PAR Futureazoo
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

    @Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class CreativeTabEvents {
        @SubscribeEvent
        public static void addCreative(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
                event.accept(CANDY);
                event.accept(CANDY_BLUE);
                event.accept(CANDY_RED);
            }
        }
    }
}