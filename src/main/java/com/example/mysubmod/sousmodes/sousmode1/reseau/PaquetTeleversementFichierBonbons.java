package com.example.mysubmod.sousmodes.sousmode1.reseau;

import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode1.donnees.GestionnaireFichiersApparitionBonbons;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

public class PaquetTeleversementFichierBonbons {
    private final UUID idTransfert;
    private final String nomFichier;
    private final int nombreTotalMorceaux;
    private final int indexMorceau;
    private final byte[] donneesMorceau;

    public PaquetTeleversementFichierBonbons(UUID idTransfert, String nomFichier, int nombreTotalMorceaux, int indexMorceau, byte[] donneesMorceau) {
        this.idTransfert = idTransfert;
        this.nomFichier = nomFichier;
        this.nombreTotalMorceaux = nombreTotalMorceaux;
        this.indexMorceau = indexMorceau;
        this.donneesMorceau = donneesMorceau;
    }

    public static void encode(PaquetTeleversementFichierBonbons paquet, FriendlyByteBuf tampon) {
        tampon.writeUUID(paquet.idTransfert);
        tampon.writeUtf(paquet.nomFichier);
        tampon.writeInt(paquet.nombreTotalMorceaux);
        tampon.writeInt(paquet.indexMorceau);
        tampon.writeInt(paquet.donneesMorceau.length);
        tampon.writeBytes(paquet.donneesMorceau);
    }

    public static PaquetTeleversementFichierBonbons decode(FriendlyByteBuf tampon) {
        UUID idTransfert = tampon.readUUID();
        String nomFichier = tampon.readUtf();
        int nombreTotalMorceaux = tampon.readInt();
        int indexMorceau = tampon.readInt();
        int longueur = tampon.readInt();
        byte[] donneesMorceau = new byte[longueur];
        tampon.readBytes(donneesMorceau);
        return new PaquetTeleversementFichierBonbons(idTransfert, nomFichier, nombreTotalMorceaux, indexMorceau, donneesMorceau);
    }

    public static void traiter(PaquetTeleversementFichierBonbons paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) {
                // Vérifier les permissions admin côté serveur
                if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                    int succes = GestionnaireFichiersApparitionBonbons.getInstance().gererMorceau(paquet);
                    if (succes == 0) {
                        joueur.sendSystemMessage(Component.literal("§aFichier de spawn de bonbons téléchargé avec succès: " + paquet.nomFichier));
                    } else if(succes == -1){
                        joueur.sendSystemMessage(Component.literal("§cErreur lors du téléchargement du fichier. Vérifiez le format."));
                    }
                } else {
                    joueur.sendSystemMessage(Component.literal("§cVous n'avez pas les permissions pour télécharger des fichiers."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String intoString(byte[] byteArray){
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    public UUID obtenirIdTransfert() { return idTransfert; }
    public String obtenirNomFichier() { return nomFichier; }
    public int obtenirNombreTotalMorceaux() { return nombreTotalMorceaux; }
    public int obtenirIndexMorceau() { return indexMorceau; }
    public byte[] obtenirDonneesMorceau() { return donneesMorceau; }
}