package com.example.mysubmod.authentification;

import com.google.common.net.InetAddresses;

/**
 * Normalisation des adresses IP partagée par les gestionnaires d'authentification.
 *
 * <p>Les IP arrivent sous deux formes différentes : {@code ServerPlayer.getIpAddress()} donne
 * une forme nue canonique (« 2001:db8::15 »), tandis que {@code Connection.getRemoteAddress()
 * .toString()} donne « /adresse:port » avec, en IPv6, l'adresse non compressée. Comparer ces
 * deux formes directement échoue. Cette classe ramène toute représentation d'une même IP à
 * UNE forme canonique identique (celle de {@code getIpAddress()}), condition nécessaire pour que
 * les clés de liste noire et les vérifications de fenêtre de monopole coïncident — en IPv4 comme
 * en IPv6.</p>
 */
public final class UtilitaireIp {

    private UtilitaireIp() {
    }

    /**
     * Extrait l'adresse IP nue et canonique : retire une barre oblique initiale, les crochets
     * IPv6 et le port, puis recompresse via la même routine que Minecraft ({@code getIpAddress()}).
     * Une IPv6 sans crochets (« 2001:db8::15 ») n'est jamais tronquée à son dernier deux-points —
     * ce que faisait l'ancien code, confondant des adresses distinctes. Renvoie la chaîne nettoyée
     * telle quelle si ce n'est pas une IP littérale (ex. « &lt;unknown&gt; »).
     */
    public static String extraireIpSansPort(String adresseIp) {
        if (adresseIp == null) {
            return "";
        }

        String hote = extraireHote(adresseIp);
        try {
            // Recompression canonique : « 2001:db8:0:0:0:0:0:15 » -> « 2001:db8::15 », pour que
            // la forme étendue de getRemoteAddress() et la forme compressée de getIpAddress()
            // donnent exactement la même chaîne.
            return InetAddresses.toAddrString(InetAddresses.forString(hote));
        } catch (IllegalArgumentException e) {
            // Pas une IP littérale : renvoyer la forme nettoyée (comparaison par égalité stricte).
            return hote;
        }
    }

    /** Retire barre oblique initiale, crochets IPv6 et port, sans canonicaliser. */
    private static String extraireHote(String adresseIp) {
        String resultat = adresseIp;

        // Retirer la barre oblique initiale (ex. « /127.0.0.1:port »).
        if (resultat.startsWith("/")) {
            resultat = resultat.substring(1);
        }

        // IPv6 entre crochets « [adresse]:port » -> l'adresse nue entre les crochets.
        int ouvrant = resultat.indexOf('[');
        int fermant = resultat.lastIndexOf(']');
        if (ouvrant == 0 && fermant > ouvrant) {
            return resultat.substring(ouvrant + 1, fermant);
        }

        int premierDeuxPoints = resultat.indexOf(':');
        if (premierDeuxPoints < 0) {
            return resultat; // IPv4 sans port
        }
        int dernierDeuxPoints = resultat.lastIndexOf(':');
        if (premierDeuxPoints == dernierDeuxPoints) {
            return resultat.substring(0, premierDeuxPoints); // IPv4 « adresse:port »
        }

        // Plusieurs deux-points : IPv6 sans crochets, éventuellement suffixée « :port ».
        // Si l'ensemble est déjà une IPv6 valide, la garder ; sinon retirer le dernier segment.
        if (InetAddresses.isInetAddress(resultat)) {
            return resultat;
        }
        return resultat.substring(0, dernierDeuxPoints);
    }
}
