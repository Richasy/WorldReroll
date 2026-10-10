package dev.richasy.worldreroll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RerollController {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldreroll");
    private static Attempt attempt;

    private RerollController() {}

    public static boolean available(Minecraft client) {
        var server = client.getSingleplayerServer();
        return attempt == null && client.player != null && server != null && !server.isPublished();
    }

    public static void start(Minecraft client) {
        start(client, false);
    }

    public static void start(Minecraft client, boolean village) {
        if (!available(client)) return;
        var server = client.getSingleplayerServer();
        if (village && !server.getWorldGenSettings().options().generateStructures()) return;
        try {
            Path sourcePath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String sourceId = sourcePath.getFileName().toString();
            WorldIdentity source = WorldIdentity.capture(client.getLevelSource().getBaseDir(), sourceId);
            if (!source.directory().equals(sourcePath.toRealPath())) throw new IOException("Source world is outside saves");
            Attempt pending = new Attempt(source, server, village);
            attempt = pending;

            // Vanilla waits for the integrated server to save, shut down and release its world lock.
            client.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            client.setScreenAndShow(new GenericMessageScreen(Component.translatable("worldreroll.preparing")));
        } catch (Exception error) {
            failed(client, error);
        }
    }

    private static void prepare(Minecraft client, Attempt pending) {
        LevelStorageAccess targetAccess = null;
        try {
            client.setScreenAndShow(new GenericMessageScreen(Component.translatable("worldreroll.preparing")));
            try (var sourceAccess = client.getLevelSource().validateAndCreateAccess(pending.source.id())) {
                var recreated = client.createWorldOpenFlows().recreateWorldData(sourceAccess);
                var context = recreated.getSecond();
                context.validate();
                long previousSeed = context.options().seed();
                long seed;
                do { seed = WorldOptions.randomSeed(); } while (seed == previousSeed);
                var options = context.options().withSeed(OptionalLong.of(seed));
                var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
                LayeredRegistryAccess<RegistryLayer> registries = context.worldgenRegistries()
                        .replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
                var storedRules = LevelStorageSource.readExistingSavedData(sourceAccess,
                        registries.compositeAccess(), GameRuleMap.TYPE).getOrThrow();
                var gameRules = new GameRules(context.dataConfiguration().enabledFeatures(), storedRules);
                var data = new PrimaryLevelData(recreated.getFirst(), dimensions.specialWorldProperty(),
                        registries.compositeAccess().allRegistriesLifecycle());
                var settings = new LevelDataAndDimensions.WorldDataAndGenSettings(data,
                        new WorldGenSettings(options, context.selectedDimensions()));
                String targetId = WorldFolder.reserve(client.getLevelSource().getBaseDir(), recreated.getFirst().levelName());
                targetAccess = client.getLevelSource().validateAndCreateAccess(targetId);
                pending.target = WorldIdentity.capture(client.getLevelSource().getBaseDir(), targetId);
                copyDataPacks(sourceAccess.getLevelPath(LevelResource.DATAPACK_DIR),
                        targetAccess.getLevelPath(LevelResource.DATAPACK_DIR));
                pending.session.loading(targetId);
                LOGGER.info("Starting reroll: source={}, replacement={}, seed={}", pending.source.id(), targetId, seed);
                client.createWorldOpenFlows().createLevelFromExistingSettings(targetAccess,
                        context.dataPackResources(), registries, settings, Optional.of(gameRules));
                targetAccess = null; // Ownership transferred to the new integrated server.
            }
        } catch (Exception error) {
            if (targetAccess != null) {
                try { targetAccess.close(); }
                catch (IOException closeError) { error.addSuppressed(closeError); }
            }
            failed(client, error);
        }
    }

    private static void failed(Minecraft client, Exception error) {
        LOGGER.error("Could not create replacement world; source world retained", error);
        if (attempt != null) {
            attempt.session.cancel();
            releaseMarkers(attempt);
        }
        attempt = null;
        client.gui.setScreen(new AlertScreen(() -> {
            if (client.level != null) client.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            client.gui.setScreen(new TitleScreen());
        },
                Component.translatable("worldreroll.failed.title"), Component.translatable("worldreroll.failed.body")));
    }

    public static void tick(Minecraft client) {
        Attempt pending = attempt;
        if (pending == null) return;
        if (pending.session.phase() == RerollSession.Phase.PREPARING) {
            if (pending.previousServer.isShutdown() && client.getSingleplayerServer() == null && client.level == null) {
                prepare(client, pending);
            }
            return;
        }
        if (pending.session.phase() != RerollSession.Phase.LOADING) return;
        var screen = client.gui.screen();
        if (screen instanceof TitleScreen || screen instanceof DisconnectedScreen || screen instanceof AlertScreen) {
            pending.session.cancel();
            releaseMarkers(pending);
            attempt = null;
            LOGGER.warn("Replacement load cancelled; source {} retained", pending.source.id());
            return;
        }
        var server = client.getSingleplayerServer();
        boolean playable = screen == null && client.level != null && client.player != null && server != null;
        if (!playable) { pending.readyTicks = 0; return; }
        String activeId = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        if (!activeId.equals(pending.target.id())) { pending.readyTicks = 0; return; }
        if (pending.village && !pending.locating) {
            pending.locating = true;
            var locatingScreen = new GenericMessageScreen(Component.translatable("worldreroll.locating"));
            client.gui.setScreen(locatingScreen);
            server.submit(() -> VillageSpawn.place(server.overworld(), server.getPlayerList().getPlayers().getFirst()))
                    .whenComplete((spawn, error) -> client.execute(() -> {
                        if (attempt != pending) return;
                        if (error != null) {
                            failed(client, new IOException("Could not establish village spawn; source retained", error));
                        } else {
                            pending.spawn = spawn;
                            if (client.gui.screen() == locatingScreen) client.gui.setScreen(null);
                            LOGGER.info("Village spawn established for {} at {}", pending.target.id(), spawn);
                        }
                    }));
            return;
        }
        if (pending.village && (pending.spawn == null || !client.level.hasChunkAt(pending.spawn)
                || client.player.blockPosition().distSqr(pending.spawn) > 64)) {
            pending.readyTicks = 0;
            return;
        }
        if (++pending.readyTicks < 20 || !pending.session.ready(activeId, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                pending.target.verify(pending.source.directory());
                pending.source.verify(pending.target.directory());
                try (var access = client.getLevelSource().validateAndCreateAccess(pending.source.id())) {
                    // Recheck after taking the old world's lock. Use vanilla deletion, never an arbitrary recursive delete.
                    pending.source.verify(pending.target.directory());
                    access.deleteLevel();
                }
                pending.target.release();
                LOGGER.info("Replacement {} playable; deleted exact source {}", pending.target.id(), pending.source.id());
            } catch (IOException | net.minecraft.world.level.validation.ContentValidationException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }, Util.ioPool()).whenComplete((ignored, error) -> client.execute(() -> {
            pending.session.complete();
            if (attempt == pending) attempt = null;
            if (error != null) LOGGER.error("Replacement {} loaded but source {} could not be deleted",
                    pending.target.id(), pending.source.id(), error);
            if (client.player != null) client.player.sendSystemMessage(Component.translatable(
                    error == null ? "worldreroll.complete" : "worldreroll.delete_failed"));
        }));
    }

    private static void copyDataPacks(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        Path realSource = source.toRealPath();
        Path realTarget = target.toRealPath();
        try (var entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry) || !entry.toRealPath().startsWith(realSource)) {
                    throw new IOException("Linked datapack entry is not safe to copy: " + entry);
                }
                Path destination = realTarget.resolve(source.relativize(entry)).normalize();
                if (!destination.startsWith(realTarget)) throw new IOException("Datapack path escaped replacement");
                if (Files.isDirectory(entry)) Files.createDirectories(destination);
                else Files.copy(entry, destination);
            }
        }
    }

    private static void releaseMarkers(Attempt pending) {
        for (WorldIdentity identity : new WorldIdentity[]{pending.source, pending.target}) {
            if (identity == null) continue;
            try { identity.release(); }
            catch (IOException error) { LOGGER.warn("Could not remove attempt marker from {}", identity.id(), error); }
        }
    }

    private static final class Attempt {
        final WorldIdentity source;
        final net.minecraft.client.server.IntegratedServer previousServer;
        final RerollSession session = new RerollSession();
        final boolean village;
        boolean locating;
        net.minecraft.core.BlockPos spawn;
        WorldIdentity target;
        int readyTicks;
        Attempt(WorldIdentity source, net.minecraft.client.server.IntegratedServer previousServer, boolean village) {
            this.source = source;
            this.previousServer = previousServer;
            this.village = village;
        }
    }
}
