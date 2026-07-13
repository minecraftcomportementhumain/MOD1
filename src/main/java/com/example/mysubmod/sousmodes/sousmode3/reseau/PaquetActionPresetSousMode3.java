package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.GestionnairePresetsSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : action d'un admin sur les presets de config du menu N
 * (lister, sauvegarder la config courante, charger, supprimer). Le serveur répond
 * toujours par un {@link PaquetReponsePresetSousMode3} (liste à jour, + config au chargement).
 */
public class PaquetActionPresetSousMode3 {

    public enum Action { LISTER, SAUVEGARDER, CHARGER, SUPPRIMER }

    private final Action action;
    private final String nom;              // vide pour LISTER
    private final ConfigPartieSousMode3 config; // non-null seulement pour SAUVEGARDER

    public PaquetActionPresetSousMode3(Action action, String nom, ConfigPartieSousMode3 config) {
        this.action = action;
        this.nom = nom == null ? "" : nom;
        this.config = config;
    }

    public static void encode(PaquetActionPresetSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeEnum(paquet.action);
        tampon.writeUtf(paquet.nom, GestionnairePresetsSousMode3.LONGUEUR_MAX_NOM);
        tampon.writeBoolean(paquet.config != null);
        if (paquet.config != null) {
            paquet.config.ecrire(tampon);
        }
    }

    public static PaquetActionPresetSousMode3 decode(FriendlyByteBuf tampon) {
        Action action = tampon.readEnum(Action.class);
        String nom = tampon.readUtf(GestionnairePresetsSousMode3.LONGUEUR_MAX_NOM);
        ConfigPartieSousMode3 config = tampon.readBoolean() ? ConfigPartieSousMode3.lire(tampon) : null;
        return new PaquetActionPresetSousMode3(action, nom, config);
    }

    public static void traiter(PaquetActionPresetSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return; // Réservé aux admins
            }
            GestionnairePresetsSousMode3 presets = GestionnairePresetsSousMode3.getInstance();
            String message = null;
            ConfigPartieSousMode3 configChargee = null;

            switch (paquet.action) {
                case SAUVEGARDER -> {
                    if (paquet.config != null && GestionnairePresetsSousMode3.nomValide(paquet.nom)) {
                        paquet.config.borner();
                        message = presets.sauvegarder(paquet.nom, paquet.config)
                            ? "§aPreset « " + paquet.nom + " » sauvegardé"
                            : "§cÉchec de la sauvegarde du preset";
                    } else {
                        message = "§cNom de preset invalide (lettres, chiffres, - et _, max "
                            + GestionnairePresetsSousMode3.LONGUEUR_MAX_NOM + ")";
                    }
                }
                case CHARGER -> {
                    configChargee = presets.charger(paquet.nom);
                    message = configChargee != null
                        ? "§aPreset « " + paquet.nom + " » chargé"
                        : "§cPreset introuvable : " + paquet.nom;
                }
                case SUPPRIMER -> message = presets.supprimer(paquet.nom)
                    ? "§ePreset « " + paquet.nom + " » supprimé"
                    : "§cÉchec de la suppression du preset";
                case LISTER -> { /* réponse = liste seule */ }
            }

            GestionnaireReseau.INSTANCE.sendTo(
                new PaquetReponsePresetSousMode3(presets.listerPresets(), configChargee, message),
                joueur.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        });
        ctx.get().setPacketHandled(true);
    }
}
