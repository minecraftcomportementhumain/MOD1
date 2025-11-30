package com.example.mysubmod.objets;

import com.example.mysubmod.MonSubMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemsMod {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MonSubMod.MOD_ID);

    // PNG 2D FAIT PAR Futureazoo
    // Bonbon SousMode1 (bonbon bleu original)
    public static final RegistryObject<Item> BONBON = ITEMS.register("candy",
        () -> new ItemBonbon());

    // Bonbons SousMode2
    public static final RegistryObject<Item> BONBON_BLEU = ITEMS.register("candy_blue",
        () -> new ItemBonbonBleu());

    public static final RegistryObject<Item> BONBON_ROUGE = ITEMS.register("candy_red",
        () -> new ItemBonbonRouge());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    @Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class CreativeTabEvents {
        @SubscribeEvent
        public static void addCreative(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
                event.accept(BONBON);
                event.accept(BONBON_BLEU);
                event.accept(BONBON_ROUGE);
            }
        }
    }
}