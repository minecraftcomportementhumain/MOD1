package com.example.mysubmod.authentification;

import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Écran de mot de passe unifié pour les admins et les joueurs protégés
 */
@OnlyIn(Dist.CLIENT)
public class EcranMotDePasseAuth extends Screen {
    private EditBox champMotDePasse;
    private Button boutonConnexion;
    private final String typeCompte; // "ADMINISTRATEUR" ou "JOUEUR_PROTEGE"
    private int tentativesRestantes;
    private Component messageErreur;

    public EcranMotDePasseAuth(String typeCompte, int tentativesRestantes, int delaiSecondes) {
        super(Component.literal(typeCompte.equals("ADMINISTRATEUR") ? "Authentification Administrateur" : "Authentification Compte Protégé"));
        this.typeCompte = typeCompte;
        this.tentativesRestantes = tentativesRestantes;
        this.messageErreur = null;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int centreY = this.height / 2;

        // Champ de mot de passe avec masquage (comme EcranMotDePasseAdmin)
        champMotDePasse = new EditBox(this.font, centreX - 100, centreY + 30, 200, 20,
            Component.literal("Mot de passe"));
        champMotDePasse.setMaxLength(50);
        champMotDePasse.setHint(Component.literal("Entrez votre mot de passe..."));
        champMotDePasse.setFormatter((text, cursorPos) -> {
            // Remplace tous les caractères par des astérisques pour masquer le mot de passe
            return Component.literal("*".repeat(text.length())).getVisualOrderText();
        });
        champMotDePasse.setFocused(true);
        this.addWidget(champMotDePasse);

        // Bouton de connexion
        boutonConnexion = Button.builder(
            Component.literal("Se connecter"),
            button -> soumettreMotDePasse()
        ).bounds(centreX - 100, centreY + 65, 200, 20).build();
        this.addRenderableWidget(boutonConnexion);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Titre
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);

        // Instructions
        String ligneCitation1 = typeCompte.equals("ADMINISTRATEUR")
            ? "Vous vous connectez avec un compte administrateur."
            : "Vous vous connectez avec un compte protégé.";
        Component instructions = Component.literal(ligneCitation1);
        guiGraphics.drawCenteredString(this.font, instructions, this.width / 2, 70, 0xAAAAAA);

        Component instructions2 = Component.literal("Veuillez entrer votre mot de passe pour continuer.");
        guiGraphics.drawCenteredString(this.font, instructions2, this.width / 2, 85, 0xAAAAAA);

        // Tentatives restantes
        Component texteTentatives = Component.literal(String.format("§eTentatives restantes: §f%d/3", tentativesRestantes));
        guiGraphics.drawCenteredString(this.font, texteTentatives, this.width / 2, 110, 0xFFFFFF);

        // Avertissement si peu de tentatives restantes
        if (tentativesRestantes <= 1) {
            Component texteAvertissement = Component.literal("§c§lATTENTION: Dernier essai avant blacklist!");
            guiGraphics.drawCenteredString(this.font, texteAvertissement, this.width / 2, 125, 0xFF0000);
        }

        // Champ de mot de passe
        champMotDePasse.render(guiGraphics, mouseX, mouseY, partialTick);

        // Message d'erreur - sous le bouton
        if (messageErreur != null) {
            guiGraphics.drawCenteredString(this.font, messageErreur, this.width / 2, this.height / 2 + 95, 0xFF5555);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void soumettreMotDePasse() {
        String motDePasse = champMotDePasse.getValue();
        if (motDePasse.isEmpty()) {
            messageErreur = Component.literal("§cLe mot de passe ne peut pas être vide");
            return;
        }

        // Envoie le paquet d'authentification au serveur (même paquet pour les deux types)
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetAuthAdmin(motDePasse));

        // Efface le champ de mot de passe pour la sécurité
        champMotDePasse.setValue("");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Touche Entrée pour soumettre
        if (keyCode == 257) { // ENTER
            soumettreMotDePasse();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Ne peut pas fermer avec ESC - doit s'authentifier
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * Met à jour les tentatives restantes (appelé depuis le gestionnaire de paquets quand le serveur envoie une mise à jour)
     */
    public void definirTentativesRestantes(int attempts) {
        this.tentativesRestantes = attempts;
    }

    /**
     * Affiche un message d'erreur (appelé depuis le gestionnaire de paquets)
     */
    public void afficherErreur(String erreur) {
        this.messageErreur = Component.literal("§c" + erreur);
    }
}
