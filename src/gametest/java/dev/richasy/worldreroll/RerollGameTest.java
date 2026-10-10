package dev.richasy.worldreroll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.packs.PackType;

public final class RerollGameTest implements FabricClientGameTest {
    private static void capture(ClientGameTestContext context, String name) {
        boolean hidden = context.computeOnClient(m -> m.gui.hud.isHidden());
        try {
            if (!hidden) context.runOnClient(m -> m.gui.hud.toggle());
            context.waitTicks(5);
            context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
                    .of(name));
        } finally {
            if (!hidden) context.runOnClient(m -> m.gui.hud.toggle());
        }
    }
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(m -> {
            m.options.guiScale().set(3);
            m.options.showAutosaveIndicator().set(false);
            m.options.pauseOnLostFocus = false;
            m.getWindow().setWindowed(1920, 1080);
        });
        context.waitFor(m -> m.getWindow().getWidth() == 1920 && m.getWindow().getHeight() == 1080, 200);
        context.runOnClient(m -> m.resizeGui());
        context.waitTicks(10);
        var world = context.worldBuilder().adjustSettings(settings -> {
            settings.setSeed("8611");
            settings.setGenerateStructures(false);
            settings.setBonusChest(true);
        }).create();
        try {
            Path source = context.computeOnClient(m -> m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
            Path other = source.getParent().resolve("unrelated-reroll-test-" + java.util.UUID.randomUUID());
            try {
                Files.createDirectory(other);
                Files.writeString(other.resolve("keep.txt"), "Unrelated world must survive");
                world.getServer().runOnServer(server -> {
                    server.getWorldData().setGameType(GameType.SURVIVAL);
                    server.getWorldData().setDifficulty(Difficulty.HARD);
                    server.getWorldData().setDifficultyLocked(true);
                    server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                    server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
                    server.getPlayerList().getPlayers().getFirst().getInventory().add(new ItemStack(Items.DIAMOND, 32));
                    server.overworld().setBlock(new BlockPos(48, 90, 48), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                    installPack(server.getWorldPath(LevelResource.DATAPACK_DIR), false);
                    var config = server.getWorldData().getDataConfiguration();
                    var enabled = new ArrayList<>(config.dataPacks().getEnabled());
                    enabled.add("file/reroll-test");
                    server.getWorldData().setDataConfiguration(new WorldDataConfiguration(
                            new DataPackConfig(enabled, config.dataPacks().getDisabled()), config.enabledFeatures()));
                });
                long oldSeed = context.computeOnClient(m -> m.getSingleplayerServer().overworld().getSeed());
                context.setScreen(() -> new PauseScreen(true));
                capture(context, "worldreroll-pause-entry");
                context.clickScreenButton("Reroll");
                context.waitForScreen(RerollSettingsScreen.class);
                context.runOnClient(m -> check(net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(m.gui.screen()).stream()
                        .anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button
                                && button.getMessage().getString().equals("Near a village") && !button.active),
                        "Village selection enabled with structures disabled"));
                capture(context, "worldreroll-settings-structures-off");
                context.clickScreenButton("Continue");
                context.waitForScreen(ConfirmScreen.class);
                capture(context, "worldreroll-confirm");
                context.clickScreenButton("Cancel");
                context.waitForScreen(RerollSettingsScreen.class);
                context.clickScreenButton("Cancel");
                context.waitForScreen(PauseScreen.class);
                check(Files.exists(source.resolve("level.dat")), "Cancel deleted source");
                context.clickScreenButton("Reroll");
                context.clickScreenButton("Continue");
                context.clickScreenButton("Reroll world");
                context.runOnClient(m -> {
                    check(!RerollController.available(m), "Duplicate reroll allowed while preparing");
                    RerollController.start(m);
                });
                check(Files.exists(source.resolve("level.dat")), "Source deleted before replacement became playable");
                context.waitFor(m -> m.player != null && m.gui.screen() == null && m.getSingleplayerServer() != null
                        && !m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(source), 2400);
                var verification = context.computeOnClient(m -> m.getSingleplayerServer().submit(() -> {
                    var server = m.getSingleplayerServer();
                    var data = server.getWorldData();
                    check(server.overworld().getSeed() != oldSeed, "Seed was reused");
                    check(!server.getWorldGenSettings().options().generateStructures(), "Structure option changed");
                    check(server.getWorldGenSettings().options().generateBonusChest(), "Bonus chest option changed");
                    check(data.getGameType() == GameType.SURVIVAL, "Survival mode changed");
                    check(data.getDifficulty() == Difficulty.HARD && data.isDifficultyLocked(), "Difficulty changed");
                    check(server.getGameRules().get(GameRules.KEEP_INVENTORY), "Keep inventory rule lost");
                    check(!server.getGameRules().get(GameRules.ADVANCE_TIME), "Time rule lost");
                    check(!server.getWorldData().isDebugWorld() && data.isFlatWorld(), "World generator changed");
                    check(data.getDataConfiguration().dataPacks().getEnabled().contains("file/reroll-test"), "Datapack disabled");
                    check(server.overworld().recipeAccess().getRecipeFor(RecipeType.CRAFTING,
                            CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK))), server.overworld()).orElseThrow()
                            .value().assemble(CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK))))
                            .is(Items.DIAMOND), "Copied datapack recipe not active");
                    check(server.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.DIAMOND) == 0,
                            "Old inventory carried over");
                    check(!server.overworld().getBlockState(new BlockPos(48, 90, 48)).is(Blocks.DIAMOND_BLOCK),
                            "Old terrain carried over");
                }));
                context.waitFor(m -> verification.isDone(), 400);
                verification.join();
                context.waitFor(m -> !Files.exists(source), 400);
                check(Files.exists(other.resolve("keep.txt")), "Unrelated save deleted");
                context.waitFor(RerollController::available, 400);
                capture(context, "worldreroll-new-world");

                context.runOnClient(m -> check(m.getSingleplayerServer().publishServer(
                        net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, 0), "Could not publish test world"));
                context.setScreen(() -> new PauseScreen(true));
                context.runOnClient(m -> {
                    check(!RerollController.available(m), "Reroll allowed in LAN world");
                    check(net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(m.gui.screen()).stream()
                            .noneMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button
                                    && button.getMessage().getString().equals("Reroll")), "LAN menu has reroll button");
                    RerollController.start(m);
                    check(m.getSingleplayerServer().unpublishServer(), "Could not close test LAN publication");
                    m.gui.setScreen(null);
                });

                // A malformed enabled world-generation resource must fail recreation without deleting its source.
                Path failedSource = context.computeOnClient(m -> m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
                installPack(failedSource.resolve("datapacks"), true);
                context.runOnClient(RerollController::start);
                context.waitForScreen(AlertScreen.class);
                check(Files.exists(failedSource.resolve("level.dat")), "Creation failure deleted source");
                capture(context, "worldreroll-failure-keeps-source");
                context.runOnClient(m -> m.gui.setScreen(new TitleScreen()));
                context.runOnClient(m -> check(!RerollController.available(m), "Reroll available without singleplayer server"));
                org.slf4j.LoggerFactory.getLogger("worldreroll").info("WORLDREROLL_NATIVE_PASS: native pause/cancel/confirm, new seed, survival/difficulty/rules, copied active datapack, reset inventory/terrain, delayed exact-source deletion and failed recreation preservation");
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            } finally {
                // Clean only the unrelated fixture created by this test; failed-source evidence is retained in the test run directory.
                try {
                    Files.deleteIfExists(other.resolve("keep.txt"));
                    Files.deleteIfExists(other);
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            }
        } finally {
            if (context.computeOnClient(m -> m.level != null)) world.close();
        }
        villageScenario(context);
    }

    private static void villageScenario(ClientGameTestContext context) {
        var world = context.worldBuilder().adjustSettings(settings -> {
            settings.setSeed("8611");
            settings.setGenerateStructures(true);
            settings.setWorldType(settings.getNormalPresetList().stream().filter(entry -> entry.preset() != null
                    && entry.preset().is(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL)).findFirst().orElseThrow());
        }).create();
        try {
            Path source = context.computeOnClient(m -> m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
            context.setScreen(() -> new PauseScreen(true));
            context.clickScreenButton("Reroll");
            context.clickScreenButton("Near a village");
            capture(context, "worldreroll-settings-village");
            context.clickScreenButton("Continue");
            capture(context, "worldreroll-confirm-village");
            context.clickScreenButton("Reroll world");
            check(Files.exists(source.resolve("level.dat")), "Source deleted before village placement");
            context.waitFor(m -> !Files.exists(source) && RerollController.available(m), 6000);
            var verification = context.computeOnClient(m -> m.getSingleplayerServer().submit(() -> {
                var level = m.getSingleplayerServer().overworld();
                var player = m.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var feet = player.blockPosition();
                var village = level.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, feet, 8, false);
                check(village != null && village.distSqr(feet) <= 100 * 100, "Player not near village");
                check(level.getRespawnData().globalPos().pos().equals(feet), "World spawn not moved");
                check(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                        && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP),
                        "Unsafe village spawn");
                return village;
            }));
            context.waitFor(m -> verification.isDone(), 600);
            verification.join();
            capture(context, "worldreroll-village-spawn");
            // A clearly labelled spectator overview illustrates the village, not the initial player viewpoint.
            var village = verification.join();
            context.runOnClient(m -> {
                m.options.renderDistance().set(12);
                m.options.fov().set(60);
            });
            var overview = context.computeOnClient(m -> m.getSingleplayerServer().submit(() -> {
                var level = m.getSingleplayerServer().overworld();
                var player = m.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        village.getX(), village.getZ());
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, village.getX() + 30.5, surface + 35, village.getZ() + 30.5,
                        java.util.Set.of(), 135, 45, true);
            }));
            context.waitFor(m -> overview.isDone(), 400);
            overview.join();
            context.waitTicks(240);
            capture(context, "worldreroll-village-overview");
            var languageReload = context.computeOnClient(m -> {
                m.getLanguageManager().setSelected("zh_cn");
                return m.reloadResourcePacks();
            });
            context.waitFor(m -> languageReload.isDone(), 400);
            languageReload.join();
            context.waitFor(m -> m.gui.overlay() == null, 400);
            context.setScreen(() -> new PauseScreen(true));
            context.runOnClient(m -> m.gui.setScreen(new RerollSettingsScreen(m.gui.screen())));
            capture(context, "worldreroll-settings-zh-cn");
            context.clickScreenButton("继续");
            capture(context, "worldreroll-confirm-zh-cn");
            context.clickScreenButton("取消");
            context.runOnClient(m -> m.gui.setScreen(null));

            Path failedSource = context.computeOnClient(m -> m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
            try {
                Path root = failedSource.resolve("datapacks/no-villages");
                Files.createDirectories(root);
                var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
                Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"No villages fixture\",\"min_format\":"
                        + format.major() + ",\"max_format\":" + format.major() + "}}");
                Path tag = root.resolve("data/minecraft/tags/worldgen/structure/village.json");
                Files.createDirectories(tag.getParent());
                Files.writeString(tag, "{\"replace\":true,\"values\":[]}");
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            var enablePack = context.computeOnClient(m -> m.getSingleplayerServer().submit(() -> {
                var data = m.getSingleplayerServer().getWorldData();
                var config = data.getDataConfiguration();
                var enabled = new ArrayList<>(config.dataPacks().getEnabled());
                enabled.add("file/no-villages");
                data.setDataConfiguration(new WorldDataConfiguration(new DataPackConfig(enabled,
                        config.dataPacks().getDisabled()), config.enabledFeatures()));
            }));
            context.waitFor(m -> enablePack.isDone(), 400);
            enablePack.join();
            context.runOnClient(m -> RerollController.start(m, true));
            context.waitForScreen(AlertScreen.class);
            check(Files.exists(failedSource.resolve("level.dat")), "Missing village caused source deletion");
            context.clickScreenButton("返回");
            context.waitForScreen(TitleScreen.class);
            org.slf4j.LoggerFactory.getLogger("worldreroll").info("WORLDREROLL_VILLAGE_PASS: settings panel, safe village/player/world spawn and missing-village source preservation");
        } finally {
            if (context.computeOnClient(m -> m.level != null)) world.close();
        }
    }

    private static void installPack(Path packs, boolean broken) {
        try {
            Path root = packs.resolve("reroll-test");
            Files.createDirectories(root);
            var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Reroll test\",\"min_format\":"
                    + format.major() + ",\"max_format\":" + format.major() + "}}");
            Path recipe = root.resolve("data/rerolltest/recipe/test.json");
            Files.createDirectories(recipe.getParent());
            Files.writeString(recipe, "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:stick\"],\"result\":{\"id\":\"minecraft:diamond\"}}");
            if (broken) {
                Path bad = root.resolve("data/minecraft/worldgen/world_preset/normal.json");
                Files.createDirectories(bad.getParent());
                Files.writeString(bad, "intentionally invalid world generation JSON");
            }
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
