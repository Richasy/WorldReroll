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
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().adjustSettings(settings -> {
            settings.setSeed("8611");
            settings.setGenerateStructures(false);
            settings.setBonusChest(true);
        }).create()) {
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
                context.takeScreenshot("worldreroll-pause-entry");
                context.clickScreenButton("↻");
                context.waitForScreen(ConfirmScreen.class);
                context.takeScreenshot("worldreroll-confirm");
                context.clickScreenButton("Cancel");
                context.waitForScreen(PauseScreen.class);
                check(Files.exists(source.resolve("level.dat")), "Cancel deleted source");
                context.clickScreenButton("↻");
                context.clickScreenButton("Reroll world");
                check(Files.exists(source.resolve("level.dat")), "Source deleted before replacement became playable");
                context.waitFor(m -> m.player != null && m.gui.screen() == null && m.getSingleplayerServer() != null
                        && !m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(source), 2400);
                context.runOnClient(m -> {
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
                });
                context.waitFor(m -> !Files.exists(source), 400);
                check(Files.exists(other.resolve("keep.txt")), "Unrelated save deleted");
                context.waitFor(RerollController::available, 400);
                context.takeScreenshot("worldreroll-new-world");

                // A malformed enabled world-generation resource must fail recreation without deleting its source.
                Path failedSource = context.computeOnClient(m -> m.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
                installPack(failedSource.resolve("datapacks"), true);
                context.runOnClient(RerollController::start);
                context.waitForScreen(AlertScreen.class);
                check(Files.exists(failedSource.resolve("level.dat")), "Creation failure deleted source");
                context.takeScreenshot("worldreroll-failure-keeps-source");
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
