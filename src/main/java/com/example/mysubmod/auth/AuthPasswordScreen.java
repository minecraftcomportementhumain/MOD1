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
    private final String accountType; // "ADMIN" or "PROTECTED_PLAYER"
    private final int remainingAttempts;
    private final int timeoutSeconds;

    public AuthPasswordScreen(String accountType, int remainingAttempts, int timeoutSeconds) {
        super(Component.literal(accountType.equals("ADMIN") ? "Authentification Admin" : "Authentification Compte Protégé"));
        this.accountType = accountType;
        this.remainingAttempts = remainingAttempts;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Password field
        this.passwordField = new EditBox(
            this.font,
            centerX - 100,
            centerY - 10,
            200,
            20,
            Component.literal("Mot de passe")
        );
        this.passwordField.setMaxLength(50);
        this.passwordField.setFocused(true);
        this.addWidget(this.passwordField);
        this.setInitialFocus(this.passwordField);

        // Submit button
        this.addRenderableWidget(Button.builder(
            Component.literal("Confirmer"),
            button -> submitPassword()
        ).bounds(centerX - 75, centerY + 30, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        String titleText = accountType.equals("ADMIN") ? "§c§lAuthentification Admin" : "§e§lCompte Protégé";
        guiGraphics.drawCenteredString(this.font, titleText, centerX, centerY - 60, 0xFFFFFF);

        // Instructions
        String instruction = "§7Entrez votre mot de passe :";
        guiGraphics.drawCenteredString(this.font, instruction, centerX, centerY - 35, 0xFFFFFF);

        // Remaining attempts
        String attemptsText = "§eTentatives restantes: §f" + remainingAttempts;
        guiGraphics.drawCenteredString(this.font, attemptsText, centerX, centerY + 60, 0xFFFFFF);

        // Timeout warning
        String timeoutText = "§cTemps restant: §f" + timeoutSeconds + "s";
        guiGraphics.drawCenteredString(this.font, timeoutText, centerX, centerY + 75, 0xFFFFFF);

        // Render masked password field
        String password = this.passwordField.getValue();
        String masked = "*".repeat(password.length());
        guiGraphics.drawString(
            this.font,
            masked,
            this.passwordField.getX() + 4,
            this.passwordField.getY() + 6,
            0xFFFFFF
        );

        // Render widgets
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void submitPassword() {
        String password = this.passwordField.getValue();

        if (password.isEmpty()) {
            return;
        }

        // Send to server - use AdminAuthPacket for now (will work for both)
        if (accountType.equals("ADMIN")) {
            NetworkHandler.INSTANCE.sendToServer(new com.example.mysubmod.auth.AdminAuthPacket(password));
        } else {
            // Same packet structure works for protected players
            NetworkHandler.INSTANCE.sendToServer(new com.example.mysubmod.auth.AdminAuthPacket(password));
        }

        this.passwordField.setValue("");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key submits
        if (keyCode == 257) { // ENTER
            submitPassword();
            return true;
        }

        // Don't allow ESC to close
        if (keyCode == 256) { // ESC
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause game
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Can't close with ESC
    }
}
