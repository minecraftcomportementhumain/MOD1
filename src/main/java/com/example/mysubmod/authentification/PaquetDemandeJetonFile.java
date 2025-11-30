package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client demandant le jeton de file stocké
 * Le client répond automatiquement avec PaquetVerificationJetonFile
 */
public class PaquetDemandeJetonFile {
    private final String nomCompte;

    public PaquetDemandeJetonFile(String nomCompte) {
        this.nomCompte = nomCompte;
    }

    public static void encode(PaquetDemandeJetonFile paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCompte, 50);
    }

    public static PaquetDemandeJetonFile decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeJetonFile(tampon.readUtf(50));
    }

    public static void traiter(PaquetDemandeJetonFile paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Côté client: Vérifie si nous avons un jeton pour ce compte
            String jeton = StockageJetonsFile.obtenirJeton(paquet.nomCompte);

            if (jeton != null) {
                MonSubMod.JOURNALISEUR.info("Serveur a demandé jeton pour {} - envoi du jeton: {}", paquet.nomCompte, jeton);
                // Envoie le jeton au serveur
                GestionnaireReseau.INSTANCE.sendToServer(new PaquetVerificationJetonFile(paquet.nomCompte, jeton));
            } else {
                MonSubMod.JOURNALISEUR.warn("Serveur a demandé jeton pour {} mais aucun jeton trouvé dans le stockage", paquet.nomCompte);
                // Envoie un jeton vide pour déclencher le rejet
                GestionnaireReseau.INSTANCE.sendToServer(new PaquetVerificationJetonFile(paquet.nomCompte, ""));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
