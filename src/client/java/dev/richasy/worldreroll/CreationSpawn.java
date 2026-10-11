package dev.richasy.worldreroll;

import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

/** Screen-local choices are armed only when vanilla actually creates a new world. */
public final class CreationSpawn {
    private static final WeakHashMap<WorldCreationUiState, Boolean> CHOICES = new WeakHashMap<>();
    private static String target;
    private static boolean locating;
    private CreationSpawn() {}

    public static boolean selected(WorldCreationUiState state) {
        return state.isGenerateStructures() && !state.isDebug() && CHOICES.getOrDefault(state, false);
    }

    public static void select(WorldCreationUiState state, boolean value) {
        CHOICES.put(state, value && state.isGenerateStructures() && !state.isDebug());
        state.onChanged();
    }

    public static void arm(WorldCreationUiState state) {
        target = selected(state) ? state.getTargetFolder() : null;
        locating = false;
    }

    public static boolean busy() { return target != null; }

    public static void tick(Minecraft client) {
        if (target == null) return;
        var screen = client.gui.screen();
        if (screen instanceof TitleScreen || screen instanceof AlertScreen || screen instanceof DisconnectedScreen) {
            target = null;
            return;
        }
        var server = client.getSingleplayerServer();
        if (locating || screen != null || server == null || client.player == null || client.level == null) return;
        String folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        if (!folder.equals(target)) return;
        locating = true;
        String expected = target;
        var progress = new GenericMessageScreen(Component.translatable("worldreroll.locating"));
        client.gui.setScreen(progress);
        server.submit(() -> VillageSpawn.place(server.overworld(), server.getPlayerList().getPlayers().getFirst()))
                .whenComplete((spawn, error) -> client.execute(() -> {
                    if (!expected.equals(target) || client.getSingleplayerServer() != server) return;
                    target = null;
                    locating = false;
                    if (error == null) {
                        if (client.gui.screen() == progress) client.gui.setScreen(null);
                        LoggerFactory.getLogger("worldreroll").info("Created world {} with village spawn at {}", expected, spawn);
                    } else {
                        LoggerFactory.getLogger("worldreroll").error("Village placement failed for newly created world {}; no saves deleted", expected, error);
                        client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(null),
                                Component.translatable("worldreroll.creation.failed_title"),
                                Component.translatable("worldreroll.creation.failed_body")));
                    }
                }));
    }
}
