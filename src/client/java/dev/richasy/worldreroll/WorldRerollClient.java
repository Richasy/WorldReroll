package dev.richasy.worldreroll;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class WorldRerollClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof PauseScreen) || !RerollController.available(client)) return;
            var buttons = Screens.getWidgets(screen);
            var anchor = buttons.stream().filter(widget -> widget instanceof Button)
                    .max(java.util.Comparator.comparingInt(widget -> widget.getY())).orElse(null);
            if (anchor == null) return;
            int x = anchor.getX() + anchor.getWidth() + 4;
            if (x + 20 > width) x = Math.max(0, anchor.getX() - 24);
            Button button = Button.builder(Component.literal("↻"), ignored -> {
                if (!RerollController.available(client)) return;
                String name = client.getSingleplayerServer().getWorldData().getLevelName();
                client.gui.setScreen(new ConfirmScreen(accepted -> {
                    if (accepted) RerollController.start(client);
                    else client.gui.setScreen(screen);
                }, Component.translatable("worldreroll.confirm.title"),
                        Component.translatable("worldreroll.confirm.body", name),
                        Component.translatable("worldreroll.button"), Component.translatable("gui.cancel")));
            }).bounds(x, anchor.getY(), 20, 20).build();
            button.setTooltip(Tooltip.create(Component.translatable("worldreroll.tooltip")));
            buttons.add(button);
        });
        ClientTickEvents.END_CLIENT_TICK.register(RerollController::tick);
    }
}
