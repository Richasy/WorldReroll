package dev.richasy.worldreroll.mixin;

import dev.richasy.worldreroll.CreationSpawn;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SwitchGrid;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fabric screen events cannot append controls to the private World tab layout. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin {
    @Shadow @Final private CreateWorldScreen this$0;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$Builder;build()Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid;"))
    private SwitchGrid worldreroll$addSpawnChoice(SwitchGrid.Builder builder) {
        var state = this$0.getUiState();
        builder.addSwitch(Component.translatable("worldreroll.creation.village"), () -> CreationSpawn.selected(state),
                        value -> CreationSpawn.select(state, value))
                .withIsActiveCondition(() -> state.isGenerateStructures() && !state.isDebug())
                .withInfo(Component.translatable("worldreroll.creation.village_hint"));
        return builder.build();
    }
}
