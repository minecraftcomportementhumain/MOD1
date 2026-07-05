package com.example.mysubmod.reseau;

import com.example.mysubmod.MonSubMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkHooks;

/**
 * Refuse, avec un message FRANÇAIS, tout client dont la version du mod diffère de celle du
 * serveur — au lieu de l'écran d'incompatibilité générique de Forge (avec ses boutons
 * « Open latest.log » / « Open Mods Folder »).
 *
 * Le canal réseau ({@link GestionnaireReseau}) accepte la connexion au niveau réseau ; c'est
 * ici, pendant la négociation de connexion, qu'on compare la version du canal annoncée par le
 * client à celle du serveur et qu'on déconnecte proprement en cas de différence. Une
 * déconnexion classique donne l'écran simple « message + Retour à la liste des serveurs ».
 */
@Mod.EventBusSubscriber(modid = MonSubMod.ID_MOD, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireVersionClient {

    @SubscribeEvent
    public static void onNegociation(PlayerNegotiationEvent event) {
        String versionServeur = GestionnaireReseau.versionProtocole();
        String versionClient = null;
        try {
            ConnectionData data = NetworkHooks.getConnectionData(event.getConnection());
            if (data != null) {
                versionClient = data.getChannels()
                    .get(ResourceLocation.fromNamespaceAndPath(MonSubMod.ID_MOD, "main"));
            }
        } catch (Throwable t) {
            MonSubMod.JOURNALISEUR.warn("Vérification de version du client impossible : {}", t.toString());
        }

        // Fail-closed : on refuse si la version est absente ou différente.
        if (versionClient == null || !versionClient.equals(versionServeur)) {
            MonSubMod.JOURNALISEUR.info("Connexion refusée (version client={} ≠ serveur={})",
                versionClient, versionServeur);
            event.getConnection().disconnect(Component.literal(
                "§c§lMauvaise version du mod\n\n"
                + "§eVersion du serveur : §f" + versionServeur + "\n"
                + "§eVotre version : §f" + (versionClient == null ? "absente / différente" : versionClient) + "\n\n"
                + "§fRedémarrez votre client Minecraft avec la dernière version du mod,\n"
                + "§fpuis reconnectez-vous."));
        }
    }
}
