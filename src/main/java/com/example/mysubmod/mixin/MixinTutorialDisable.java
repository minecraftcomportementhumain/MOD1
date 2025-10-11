package com.example.mysubmod.mixin;

import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.client.tutorial.TutorialSteps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tutorial.class)
public class MixinTutorialDisable {

    @Inject(method = "setStep", at = @At("HEAD"), cancellable = true)
    private void disableTutorial(TutorialSteps step, CallbackInfo ci) {
        // Cancel all tutorial steps to prevent tips from showing
        ci.cancel();
    }
}
