package com.example.mysubmod.sousmodes.sousmode1.reseau;

import com.example.mysubmod.sousmodes.sousmode1.client.HUDCompteurBonbons;
import com.example.mysubmod.sousmodes.sousmode1.iles.TypeIle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PaquetMiseAJourCompteurBonbons {
    private final Map<TypeIle, Integer> compteursBonbons;

    public PaquetMiseAJourCompteurBonbons(Map<TypeIle, Integer> compteursBonbons) {
        this.compteursBonbons = compteursBonbons;
    }

    public PaquetMiseAJourCompteurBonbons(FriendlyByteBuf tampon) {
        this.compteursBonbons = new HashMap<>();
        int taille = tampon.readInt();
        for (int i = 0; i < taille; i++) {
            TypeIle ile = tampon.readEnum(TypeIle.class);
            int nombre = tampon.readInt();
            compteursBonbons.put(ile, nombre);
        }
    }

    public void encoder(FriendlyByteBuf tampon) {
        tampon.writeInt(compteursBonbons.size());
        for (Map.Entry<TypeIle, Integer> entree : compteursBonbons.entrySet()) {
            tampon.writeEnum(entree.getKey());
            tampon.writeInt(entree.getValue());
        }
    }

    public void traiter(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Mettre à jour les données HUD côté client
            HUDCompteurBonbons.mettreAJourCompteursBonbons(compteursBonbons);
        });
        ctx.get().setPacketHandled(true);
    }
}
