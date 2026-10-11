package dev.richasy.worldreroll.mixin;

import dev.richasy.worldreroll.CreationSpawn;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arm after confirmation, immediately before vanilla invokes its creation callback. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Shadow public abstract WorldCreationUiState getUiState();

    @Inject(method = "createWorldAndCleanup", at = @At("HEAD"))
    private void worldreroll$armVillageSpawn(CallbackInfo callback) {
        CreationSpawn.arm(getUiState());
    }
}
