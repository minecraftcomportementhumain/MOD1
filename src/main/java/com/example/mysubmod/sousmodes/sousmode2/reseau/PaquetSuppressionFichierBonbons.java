package com.example.mysubmod.sousmodes.sousmode2.reseau;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode2.donnees.GestionnaireFichiersApparitionBonbons;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaquetSuppressionFichierBonbons {
    private final String nomFichier;

    public PaquetSuppressionFichierBonbons(String nomFichier) {
        this.nomFichier = nomFichier;
    }

    public static void encode(PaquetSuppressionFichierBonbons paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomFichier);
    }

    public static PaquetSuppressionFichierBonbons decode(FriendlyByteBuf tampon) {
        return new PaquetSuppressionFichierBonbons(tampon.readUtf());
    }

    public static void traiter(PaquetSuppressionFichierBonbons paquet, Supplier<NetworkEvent.Context> fournisseurContexte) {
        NetworkEvent.Context contexte = fournisseurContexte.get();
        contexte.enqueueWork(() -> {
            ServerPlayer joueur = contexte.getSender();
            if (joueur != null && GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                boolean succes = GestionnaireFichiersApparitionBonbons.getInstance().supprimerFichier(paquet.nomFichier);
                if (succes) {
                    MonSubMod.JOURNALISEUR.info("Admin {} a supprimé le fichier de spawn de bonbons Sous-mode 2: {}",
                        joueur.getName().getString(), paquet.nomFichier);
                } else {
                    MonSubMod.JOURNALISEUR.warn("Échec de la suppression du fichier de spawn de bonbons Sous-mode 2: {} par {}",
                        paquet.nomFichier, joueur.getName().getString());
                }
            } else {
                MonSubMod.JOURNALISEUR.warn("Joueur non-admin {} a tenté de supprimer un fichier de spawn de bonbons Sous-mode 2",
                    joueur != null ? joueur.getName().getString() : "inconnu");
            }
        });
        contexte.setPacketHandled(true);
    }
}
