package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Hachage de mots de passe centralisé.
 *
 * Utilise PBKDF2-HMAC-SHA256 (fonction lente à étirement de clé) au lieu d'un simple
 * SHA-256, afin de résister au brute-force hors-ligne si le fichier d'identifiants fuite.
 *
 * Format du hachage stocké : {@code pbkdf2$<iterations>$<hachageBase64>}.
 * Les anciens hachages SHA-256 (sans préfixe) restent vérifiables et sont migrés de
 * façon transparente à la première connexion réussie (voir {@link #estLegacy}).
 */
public final class UtilitaireMotDePasse {

    private static final int ITERATIONS = 210_000;
    private static final int LONGUEUR_CLE_BITS = 256;
    private static final String PREFIXE_PBKDF2 = "pbkdf2$";
    private static final SecureRandom ALEATOIRE_SECURISE = new SecureRandom();

    private UtilitaireMotDePasse() {
    }

    /** Génère un sel aléatoire de 16 octets encodé en Base64. */
    public static String genererSel() {
        byte[] sel = new byte[16];
        ALEATOIRE_SECURISE.nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    /** Calcule le hachage PBKDF2 d'un mot de passe avec le sel donné (format préfixé). */
    public static String hacher(String motDePasse, String sel) {
        byte[] cle = deriver(motDePasse, sel, ITERATIONS);
        return PREFIXE_PBKDF2 + ITERATIONS + "$" + Base64.getEncoder().encodeToString(cle);
    }

    /** True si le hachage stocké est un ancien SHA-256 (à migrer vers PBKDF2). */
    public static boolean estLegacy(String hachageStocke) {
        return hachageStocke != null && !hachageStocke.startsWith(PREFIXE_PBKDF2);
    }

    /**
     * Vérifie un mot de passe contre le hachage stocké (PBKDF2 ou ancien SHA-256),
     * en comparaison à temps constant.
     */
    public static boolean verifier(String motDePasse, String sel, String hachageStocke) {
        if (hachageStocke == null || hachageStocke.isEmpty()) {
            return false;
        }
        if (hachageStocke.startsWith(PREFIXE_PBKDF2)) {
            String[] parties = hachageStocke.split("\\$");
            if (parties.length != 3) {
                return false;
            }
            int iterations;
            try {
                iterations = Integer.parseInt(parties[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            byte[] attendu = Base64.getDecoder().decode(parties[2]);
            byte[] calcule = deriver(motDePasse, sel, iterations);
            return MessageDigest.isEqual(attendu, calcule);
        }
        // Ancien format : SHA-256(motDePasse + sel) encodé en Base64.
        String legacy = hacherSha256Legacy(motDePasse, sel);
        return MessageDigest.isEqual(
            legacy.getBytes(StandardCharsets.UTF_8),
            hachageStocke.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] deriver(String motDePasse, String sel, int iterations) {
        try {
            byte[] octetsSel = Base64.getDecoder().decode(sel);
            PBEKeySpec spec = new PBEKeySpec(
                motDePasse.toCharArray(), octetsSel, iterations, LONGUEUR_CLE_BITS);
            SecretKeyFactory usine = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return usine.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la dérivation PBKDF2", e);
            // Retourne un tableau constant : la comparaison échouera de toute façon.
            return new byte[LONGUEUR_CLE_BITS / 8];
        }
    }

    private static String hacherSha256Legacy(String motDePasse, String sel) {
        try {
            MessageDigest empreinte = MessageDigest.getInstance("SHA-256");
            byte[] hachage = empreinte.digest((motDePasse + sel).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hachage);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
