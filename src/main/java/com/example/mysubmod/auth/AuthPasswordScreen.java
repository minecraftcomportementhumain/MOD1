package com.example.mysubmod.auth;

import com.example.mysubmod.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Unified password screen for both admins and protected players
 */
@OnlyIn(Dist.CLIENT)
public class AuthPasswordScreen extends Screen {
    private EditBox passwordField;
    private Button loginButton;
    private final String accountType; // "ADMIN" or "PROTECTED_PLAYER"
    private int remainingAttempts;
    private Component errorMessage;

    public AuthPasswordScreen(String accountType, int remainingAttempts, int timeoutSeconds) {
        super(Component.literal(accountType.equals("ADMIN") ? "Authentification Administrateur" : "Authentification Compte Protégé"));
        this.accountType = accountType;
        this.remainingAttempts = remainingAttempts;
        this.errorMessage = null;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Password field with masking (same as AdminPasswordScreen)
        passwordField = new EditBox(this.font, centerX - 100, centerY + 30, 200, 20,
            Component.literal("Mot de passe"));
        passwordField.setMaxLength(50);
        passwordField.setHint(Component.literal("Entrez votre mot de passe..."));
        passwordField.setFormatter((text, cursorPos) -> {
            // Replace all characters with asterisks for password masking
            return Component.literal("*".repeat(text.length())).getVisualOrderText();
        });
        passwordField.setFocused(true);
        this.addWidget(passwordField);

        // Login button
        loginButton = Button.builder(
            Component.literal("Se connecter"),
            button -> submitPassword()
        ).bounds(centerX - 100, centerY + 65, 200, 20).build();
        this.addRenderableWidget(loginButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);

        // Instructions
        String instructionLine1 = accountType.equals("ADMIN")
            ? "Vous vous connectez avec un compte administrateur."
            : "Vous vous connectez avec un compte protégé.";
        Component instructions = Component.literal(instructionLine1);
        guiGraphics.drawCenteredString(this.font, instructions, this.width / 2, 70, 0xAAAAAA);

        Component instructions2 = Component.literal("Veuillez entrer votre mot de passe pour continuer.");
        guiGraphics.drawCenteredString(this.font, instructions2, this.width / 2, 85, 0xAAAAAA);

        // Remaining attempts
        Component attemptsText = Component.literal(String.format("§eTentatives restantes: §f%d/3", remainingAttempts));
        guiGraphics.drawCenteredString(this.font, attemptsText, this.width / 2, 110, 0xFFFFFF);

        // Warning if few attempts left
        if (remainingAttempts <= 1) {
            Component warningText = Component.literal("§c§lATTENTION: Dernier essai avant blacklist!");
            guiGraphics.drawCenteredString(this.font, warningText, this.width / 2, 125, 0xFF0000);
        }

        // Password field
        passwordField.render(guiGraphics, mouseX, mouseY, partialTick);

        // Error message - below button
        if (errorMessage != null) {
            guiGraphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 + 95, 0xFF5555);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void submitPassword() {
        String password = passwordField.getValue();
        if (password.isEmpty()) {
            errorMessage = Component.literal("§cLe mot de passe ne peut pas être vide");
            return;
        }

        // Send authentication packet to server (same packet for both types)
        NetworkHandler.INSTANCE.sendToServer(new AdminAuthPacket(password));

        // Clear password field for security
        passwordField.setValue("");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key to submit
        if (keyCode == 257) { // ENTER
            submitPassword();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Cannot close with ESC - must authenticate
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * Update remaining attempts (called from packet handler when server sends update)
     */
    public void setRemainingAttempts(int attempts) {
        this.remainingAttempts = attempts;
    }

    /**
     * Show error message (called from packet handler)
     */
    public void showError(String error) {
        this.errorMessage = Component.literal("§c" + error);
    }
}
