package com.example.mysubmod.sousmodes.sousmode2.reseau;

import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons;
import com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Paquet pour mettre à jour les compteurs de bonbons côté client
 * Étendu pour Sous-mode 2 pour suivre les compteurs par île ET type de ressource
 */
public class PaquetMiseAJourCompteurBonbons {
    private final Map<TypeIle, Map<TypeRessource, Integer>> compteursBonbons;

    public PaquetMiseAJourCompteurBonbons(Map<TypeIle, Map<TypeRessource, Integer>> compteursBonbons) {
        this.compteursBonbons = compteursBonbons;
    }

    public PaquetMiseAJourCompteurBonbons(FriendlyByteBuf tampon) {
        this.compteursBonbons = new HashMap<>();
        int nombreIles = tampon.readInt();

        for (int i = 0; i < nombreIles; i++) {
            TypeIle ile = tampon.readEnum(TypeIle.class);
            int nombreTypes = tampon.readInt();

            Map<TypeRessource, Integer> compteursTypes = new HashMap<>();
            for (int j = 0; j < nombreTypes; j++) {
                TypeRessource type = tampon.readEnum(TypeRessource.class);
                int nombre = tampon.readInt();
                compteursTypes.put(type, nombre);
            }

            compteursBonbons.put(ile, compteursTypes);
        }
    }

    public void encoder(FriendlyByteBuf tampon) {
        tampon.writeInt(compteursBonbons.size());

        for (Map.Entry<TypeIle, Map<TypeRessource, Integer>> entreeIle : compteursBonbons.entrySet()) {
            tampon.writeEnum(entreeIle.getKey());

            Map<TypeRessource, Integer> compteursTypes = entreeIle.getValue();
            tampon.writeInt(compteursTypes.size());

            for (Map.Entry<TypeRessource, Integer> entreeType : compteursTypes.entrySet()) {
                tampon.writeEnum(entreeType.getKey());
                tampon.writeInt(entreeType.getValue());
            }
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
