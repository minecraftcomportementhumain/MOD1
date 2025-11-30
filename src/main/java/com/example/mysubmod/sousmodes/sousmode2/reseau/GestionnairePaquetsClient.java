package com.example.mysubmod.sousmodes.sousmode2.reseau;

import com.example.mysubmod.sousmodes.sousmode2.client.GestionnaireListeFichiersBonbons;
import com.example.mysubmod.sousmodes.sousmode2.client.EcranSelectionFichierBonbons;
import com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient;
import com.example.mysubmod.sousmodes.sousmode2.client.EcranSelectionIle;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class GestionnairePaquetsClient {

    public static void ouvrirEcranSelectionIle(int secondesRestantes) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new EcranSelectionIle(secondesRestantes));
    }

    public static void mettreAJourMinuterieJeu(int secondesRestantes) {
        MinuterieJeuClient.mettreAJourMinuterie(secondesRestantes);
    }

    public static void traiterListeFichiersBonbons(List<String> fichiersDisponibles, boolean ouvrirEcran) {
        // Stocker la liste des fichiers
        GestionnaireListeFichiersBonbons.obtenirInstance().definirListeFichiers(fichiersDisponibles);

        // Ouvrir l'écran uniquement si demandé et si nous avons des fichiers
        if (ouvrirEcran && !fichiersDisponibles.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new EcranSelectionFichierBonbons(fichiersDisponibles));
        }
    }

    public static void ouvrirEcranSelectionFichierBonbons() {
        // Toujours demander une liste de fichiers fraîche au serveur lors de l'ouverture du menu
        com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeListeFichiersBonbons());
    }

    public static void traiterFinPartie() {
        MinuterieJeuClient.marquerPartieTerminee();
    }
}
