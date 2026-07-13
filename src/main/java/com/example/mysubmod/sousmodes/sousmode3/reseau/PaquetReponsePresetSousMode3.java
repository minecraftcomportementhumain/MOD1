package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : réponse à une action sur les presets — liste des noms
 * disponibles (toujours), config chargée (seulement après CHARGER) et message de retour.
 */
public class PaquetReponsePresetSousMode3 {

    private final List<String> noms;
    private final ConfigPartieSousMode3 configChargee; // null sauf après un CHARGER réussi
    private final String message;                      // peut être null

    public PaquetReponsePresetSousMode3(List<String> noms, ConfigPartieSousMode3 configChargee, String message) {
        this.noms = noms;
        this.configChargee = configChargee;
        this.message = message == null ? "" : message;
    }

    public static void encode(PaquetReponsePresetSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeVarInt(paquet.noms.size());
        for (String nom : paquet.noms) {
            tampon.writeUtf(nom);
        }
        tampon.writeBoolean(paquet.configChargee != null);
        if (paquet.configChargee != null) {
            paquet.configChargee.ecrire(tampon);
        }
        tampon.writeUtf(paquet.message);
    }

    public static PaquetReponsePresetSousMode3 decode(FriendlyByteBuf tampon) {
        int n = tampon.readVarInt();
        List<String> noms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            noms.add(tampon.readUtf());
        }
        ConfigPartieSousMode3 config = tampon.readBoolean() ? ConfigPartieSousMode3.lire(tampon) : null;
        String message = tampon.readUtf();
        return new PaquetReponsePresetSousMode3(noms, config, message);
    }

    public static void traiter(PaquetReponsePresetSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.example.mysubmod.sousmodes.sousmode3.client.PresetsClientSousMode3
                .surReponse(paquet.noms, paquet.configChargee, paquet.message)));
        ctx.get().setPacketHandled(true);
    }
}
