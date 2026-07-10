package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet client -> serveur : demander les données complètes d'une carte
 * (pour la charger dans l'éditeur). Le serveur répond en morceaux via PaquetDonneesCarte.
 */
public class PaquetDemandeDonneesCarte {
    private final String nomCarte;

    public PaquetDemandeDonneesCarte(String nomCarte) {
        this.nomCarte = nomCarte;
    }

    public static void encode(PaquetDemandeDonneesCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCarte);
    }

    public static PaquetDemandeDonneesCarte decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeDonneesCarte(tampon.readUtf());
    }

    public static void traiter(PaquetDemandeDonneesCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            // Le fichier est transmis tel quel (le client sait décoder les formats v1 et v2) :
            // aucun décodage + ré-encodage côté serveur, et le transfert part compressé
            String json = GestionnaireCartes.getInstance().lireJsonCarte(paquet.nomCarte);
            if (json == null) {
                joueur.sendSystemMessage(Component.literal("§cCarte introuvable : " + paquet.nomCarte));
                return;
            }

            java.util.List<byte[]> morceaux =
                UtilitaireCompressionCarte.compresserEtDecouper(json.getBytes(StandardCharsets.UTF_8));
            UUID idTransfert = UUID.randomUUID();
            for (int i = 0; i < morceaux.size(); i++) {
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetDonneesCarte(idTransfert, morceaux.size(), i, morceaux.get(i)));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
