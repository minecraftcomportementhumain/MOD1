package com.example.mysubmod.cartes.reseau;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compression GZIP des transferts de cartes (sauvegarde C→S et chargement S→C).
 * Le récepteur détecte les octets magiques GZIP : des données non compressées
 * (anciens clients / serveurs) restent acceptées telles quelles.
 */
public final class UtilitaireCompressionCarte {

    /** Taille décompressée maximale acceptée (borne anti-bombe de décompression). */
    public static final int TAILLE_DECOMPRESSEE_MAX = 96 * 1024 * 1024;

    /** Taille d'un morceau réseau (sous la limite des paquets client→serveur, ~32 Ko). */
    public static final int TAILLE_MORCEAU = 30000;

    private UtilitaireCompressionCarte() {
    }

    /** Compresse puis découpe en morceaux réseau — pipeline partagé sauvegarde C→S et chargement S→C. */
    public static java.util.List<byte[]> compresserEtDecouper(byte[] brut) {
        byte[] comprime = compresser(brut);
        java.util.List<byte[]> morceaux = new java.util.ArrayList<>();
        for (int debut = 0; debut < comprime.length; debut += TAILLE_MORCEAU) {
            morceaux.add(java.util.Arrays.copyOfRange(comprime, debut,
                Math.min(comprime.length, debut + TAILLE_MORCEAU)));
        }
        return morceaux;
    }

    /** Compresse des données en GZIP (jamais null). */
    public static byte[] compresser(byte[] brut) {
        try {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream(Math.max(64, brut.length / 4));
            try (GZIPOutputStream gzip = new GZIPOutputStream(sortie)) {
                gzip.write(brut);
            }
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Échec de la compression des données de carte", e);
        }
    }

    /**
     * Décompresse les données si elles portent la signature GZIP, sinon les retourne
     * telles quelles. Lève IllegalArgumentException si les données compressées sont
     * corrompues ou dépassent TAILLE_DECOMPRESSEE_MAX une fois décompressées.
     */
    public static byte[] decompresserSiGzip(byte[] donnees) {
        if (donnees.length < 2 || (donnees[0] & 0xFF) != 0x1F || (donnees[1] & 0xFF) != 0x8B) {
            return donnees;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(donnees))) {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream(Math.min(donnees.length * 4, 1 << 20));
            byte[] tampon = new byte[16384];
            int total = 0;
            int lus;
            while ((lus = gzip.read(tampon)) > 0) {
                total += lus;
                if (total > TAILLE_DECOMPRESSEE_MAX) {
                    throw new IllegalArgumentException("Données de carte décompressées trop volumineuses");
                }
                sortie.write(tampon, 0, lus);
            }
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Données de carte compressées invalides", e);
        }
    }
}
