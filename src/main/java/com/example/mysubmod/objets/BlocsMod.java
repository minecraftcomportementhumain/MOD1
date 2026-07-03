package com.example.mysubmod.objets;

import com.example.mysubmod.MonSubMod;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlocsMod {
    public static final DeferredRegister<Block> BLOCS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MonSubMod.MOD_ID);

    // Blocs bonbon non-visible du Sous-mode 3 (camouflés dans les colonnes Île / Pierre)
    public static final RegistryObject<Block> BONBON_CACHE_TERRE = BLOCS.register("bonbon_cache_terre",
        () -> new BlocBonbonCache(false));

    public static final RegistryObject<Block> BONBON_CACHE_PIERRE = BLOCS.register("bonbon_cache_pierre",
        () -> new BlocBonbonCache(true));

    public static void register(IEventBus eventBus) {
        BLOCS.register(eventBus);
    }
}
