package com.example.mysubmod.cartes.client;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.reseau.PaquetListeCartes;
import com.example.mysubmod.cartes.reseau.PaquetResultatSauvegardeCarte;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion côté client des paquets du système de cartes.
 */
@OnlyIn(Dist.CLIENT)
public class GestionnairePaquetsCartes {
    // Réassemblage des données de carte envoyées en morceaux par le serveur
    private static final Map<UUID, Map<Integer, byte[]>> transfertsEnCours = new HashMap<>();

    public static void gererListeCartes(List<String> cartes, String carteSelectionnee, int but) {
        Minecraft mc = Minecraft.getInstance();

        switch (but) {
            case PaquetListeCartes.BUT_LISTE_SELECTION ->
                mc.setScreen(new EcranListeCartes(cartes, carteSelectionnee, EcranListeCartes.Mode.SELECTION_ACTIVE));
            case PaquetListeCartes.BUT_CHARGEMENT_EDITEUR -> {
                if (mc.screen instanceof EcranEditeurCarte editeur) {
                    mc.setScreen(new EcranListeCartes(cartes, carteSelectionnee, EcranListeCartes.Mode.CHARGEMENT_EDITEUR, editeur));
                }
            }
            default -> {
                // BUT_AUCUN : rien à faire (mise à jour silencieuse)
            }
        }
    }

    public static void gererReponseEditeur(boolean accesAccorde, String occupePar) {
        Minecraft mc = Minecraft.getInstance();
        if (accesAccorde) {
            mc.setScreen(new EcranEditeurCarte());
        } else {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                    "§cL'outil de création de carte est déjà utilisé par §e" + occupePar));
            }
        }
    }

    /** Ouvre l'écran de sélection de la zone de départ (parties sur carte des Sous-modes 1, 2 et 3) */
    public static void ouvrirSelectionZoneDepart(List<String> zones, List<Integer> tailles, int secondesRestantes) {
        Minecraft.getInstance().setScreen(new EcranSelectionZoneDepart(zones, tailles, secondesRestantes));
    }

    /** Affiche la fenêtre modale de refus de lancement d'un sous-mode */
    public static void afficherRefusLancement(String titre, List<String> lignes) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new com.example.mysubmod.client.gui.EcranModale(titre, lignes, mc.screen));
    }

    public static void gererResultatSauvegarde(int code, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranEditeurCarte editeur) {
            switch (code) {
                case PaquetResultatSauvegardeCarte.CODE_SUCCES -> editeur.surSauvegardeReussie(message);
                case PaquetResultatSauvegardeCarte.CODE_EXISTE_DEJA -> editeur.surDemandeConfirmationEcrasement(message);
                case PaquetResultatSauvegardeCarte.CODE_ERREUR -> editeur.surErreurSauvegarde(message);
            }
        } else if (mc.player != null) {
            if (code == PaquetResultatSauvegardeCarte.CODE_SUCCES) {
                mc.player.sendSystemMessage(Component.literal("§aCarte sauvegardée : " + message));
            } else {
                mc.player.sendSystemMessage(Component.literal("§cSauvegarde de carte : " + message));
            }
        }
    }

    public static void gererMorceauDonneesCarte(UUID idTransfert, int nombreTotalMorceaux, int indexMorceau, byte[] donnees) {
        Map<Integer, byte[]> morceaux = transfertsEnCours.computeIfAbsent(idTransfert, k -> new HashMap<>());
        morceaux.put(indexMorceau, donnees);

        if (morceaux.size() < nombreTotalMorceaux) {
            return;
        }
        transfertsEnCours.remove(idTransfert);

        int tailleTotale = 0;
        for (int i = 0; i < nombreTotalMorceaux; i++) {
            byte[] morceau = morceaux.get(i);
            if (morceau == null) {
                MonSubMod.JOURNALISEUR.error("Morceau manquant {} dans le transfert de carte", i);
                return;
            }
            tailleTotale += morceau.length;
        }
        byte[] complet = new byte[tailleTotale];
        int position = 0;
        for (int i = 0; i < nombreTotalMorceaux; i++) {
            byte[] morceau = morceaux.get(i);
            System.arraycopy(morceau, 0, complet, position, morceau.length);
            position += morceau.length;
        }

        try {
            CarteDonnees carte = CarteDonnees.depuisJson(new String(complet, StandardCharsets.UTF_8));
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof EcranEditeurCarte editeur) {
                editeur.chargerCarte(carte);
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du chargement des données de carte reçues", e);
        }
    }
}
