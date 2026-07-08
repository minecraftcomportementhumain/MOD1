package com.example.mysubmod.sousmodes.sousmode3.client;

/**
 * Petits faits sur la carte active, transmis par le serveur au(x) admin(s) à l'entrée en
 * Sous-mode 3, pour piloter le grisage des cases du menu N (ex. : destruction de blocs
 * obligatoire si la carte cache des bonbons non-visibles). Classe neutre (état statique
 * simple), sans dépendance client, donc inoffensive si chargée côté serveur.
 */
public class FaitsCarteClientSousMode3 {
    private static boolean aBonbonsNonVisibles = false;
    private static boolean aBonbonsTypes = false;
    private static boolean aZonesIle = false;

    private FaitsCarteClientSousMode3() {
    }

    public static void definir(boolean bonbonsNonVisibles, boolean bonbonsTypes, boolean zonesIle) {
        aBonbonsNonVisibles = bonbonsNonVisibles;
        aBonbonsTypes = bonbonsTypes;
        aZonesIle = zonesIle;
    }

    public static boolean aBonbonsNonVisibles() {
        return aBonbonsNonVisibles;
    }

    public static boolean aBonbonsTypes() {
        return aBonbonsTypes;
    }

    public static boolean aZonesIle() {
        return aZonesIle;
    }
}
