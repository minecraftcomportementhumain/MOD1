package com.example.mysubmod.objets;

import com.example.mysubmod.sousmodes.sousmode3.TypeRessource;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSanteSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSpecialisationSousMode3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Consommation d'un bonbon typé Bleu/Rouge au Sous-mode 3 (logique partagée par
 * {@link ItemBonbonBleu} et {@link ItemBonbonRouge}) : la spécialisation gère le
 * multiplicateur de soin ; l'option « manger à vie pleine » lève le refus au maximum
 * (le soin reste plafonné). Sans spécialisation active (cas résiduel : bonbon typé en
 * inventaire alors que l'option est décochée), le bonbon soigne comme un bonbon standard.
 */
final class UtilisationBonbonTypeSousMode3 {

    private UtilisationBonbonTypeSousMode3() {
    }

    static InteractionResultHolder<ItemStack> utiliser(Level level, ServerPlayer joueur,
                                                       ItemStack pileObjets, TypeRessource typeRessource) {
        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        if (!gestionnaire.estJoueurVivant(joueur.getUUID())) {
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
            return InteractionResultHolder.fail(pileObjets);
        }

        float vieActuelle = joueur.getHealth();
        float vieMaximale = joueur.getMaxHealth();
        boolean specialisation = gestionnaire.obtenirConfig().specialisation;
        float multiplicateur = specialisation
            ? GestionnaireSpecialisationSousMode3.getInstance().obtenirMultiplicateurSanteActuel(joueur.getUUID())
            : 1.0f;
        // Santé rendue configurable par partie (menu N › Bonbons & minage). Sans spécialisation
        // (cas résiduel documenté ci-dessus), le bonbon typé soigne comme un bonbon
        // STANDARD — les valeurs Bleu/Rouge n'ont de sens que quand les types sont actifs.
        float soinBase;
        if (specialisation) {
            soinBase = typeRessource == TypeRessource.BONBON_BLEU
                ? gestionnaire.obtenirConfig().soinBonbonBleu
                : gestionnaire.obtenirConfig().soinBonbonRouge;
        } else {
            soinBase = gestionnaire.obtenirConfig().soinBonbonStandard;
        }
        float soinReel = soinBase * multiplicateur;

        if (!gestionnaire.obtenirConfig().mangerDepasseMax && vieActuelle + soinReel > vieMaximale) {
            joueur.sendSystemMessage(Component.literal(
                "§cVous ne pouvez pas utiliser ce bonbon car il vous donnerait plus de vie que votre maximum"));
            return InteractionResultHolder.fail(pileObjets);
        }

        if (specialisation) {
            // La consommation typée (avec pénalité) est journalisée par le gestionnaire de santé
            GestionnaireSanteSousMode3.getInstance().gererConsommationBonbonTypee(joueur, typeRessource);
        } else {
            float nouvelleVie = Math.min(vieMaximale, vieActuelle + soinReel);
            joueur.setHealth(nouvelleVie);
            joueur.sendSystemMessage(Component.literal(
                String.format("§a✓ Vous avez récupéré %.1f cœur(s) !", soinReel / 2.0f)));
            if (gestionnaire.obtenirEnregistreurDonnees() != null) {
                gestionnaire.obtenirEnregistreurDonnees()
                    .enregistrerConsommationBonbon(joueur, typeRessource.name(), false, soinReel);
            }
        }
        pileObjets.shrink(1);

        level.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);
        return InteractionResultHolder.success(pileObjets);
    }
}
