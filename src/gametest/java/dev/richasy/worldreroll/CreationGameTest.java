package dev.richasy.worldreroll;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public final class CreationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var reload = context.computeOnClient(m -> {
            m.getLanguageManager().setSelected("en_us");
            return m.reloadResourcePacks();
        });
        context.waitFor(m -> reload.isDone(), 400);
        reload.join();
        context.waitFor(m -> m.gui.overlay() == null, 400);
        context.runOnClient(m -> {
            m.options.guiScale().set(3);
            m.options.showAutosaveIndicator().set(false);
            m.options.pauseOnLostFocus = false;
            m.getWindow().setWindowed(1920, 1080);
            CreateWorldScreen.openFresh(m, () -> m.gui.setScreen(new TitleScreen()));
        });
        context.waitForScreen(CreateWorldScreen.class);
        context.runOnClient(m -> {
            var state = ((CreateWorldScreen)m.gui.screen()).getUiState();
            check(!CreationSpawn.selected(state), "Fresh screen inherited another creation choice");
            CreationSpawn.select(state, true);
            state.setGenerateStructures(false);
            check(!CreationSpawn.selected(state), "Village preference active without structures");
        });
        context.runOnClient(m -> m.gui.screen().children().stream()
                .filter(widget -> widget instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)
                .map(widget -> (net.minecraft.client.gui.components.tabs.TabNavigationBar)widget)
                .findFirst().orElseThrow().selectTab(1, false));
        context.runOnClient(m -> check(net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(m.gui.screen()).stream()
                .anyMatch(widget -> widget.getMessage().getString().contains("Spawn near a village") && !widget.active),
                "Missing disabled village option in actual World tab"));
        context.runOnClient(m -> ((CreateWorldScreen)m.gui.screen()).getUiState().setGenerateStructures(true));
        context.runOnClient(m -> {
            var button = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(m.gui.screen()).stream()
                    .filter(widget -> widget instanceof net.minecraft.client.gui.components.CycleButton<?>)
                    .map(widget -> (net.minecraft.client.gui.components.CycleButton<?>)widget)
                    .max(java.util.Comparator.comparingInt(widget -> widget.getY())).orElseThrow();
            button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
            check(!CreationSpawn.selected(((CreateWorldScreen)m.gui.screen()).getUiState()), "World-tab switch did not disable preference");
            button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
            check(CreationSpawn.selected(((CreateWorldScreen)m.gui.screen()).getUiState()), "World-tab switch did not enable preference");
        });
        context.waitTicks(5);
        context.takeScreenshot("worldreroll-create-world-en");
        changeLanguage(context, "zh_cn");
        context.runOnClient(m -> m.gui.screen().children().stream()
                .filter(widget -> widget instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)
                .map(widget -> (net.minecraft.client.gui.components.tabs.TabNavigationBar)widget)
                .findFirst().orElseThrow().selectTab(1, false));
        context.waitTicks(5);
        context.takeScreenshot("worldreroll-create-world-zh-cn");
        changeLanguage(context, "en_us");
        context.clickScreenButton("Cancel");
        context.waitForScreen(TitleScreen.class);
        check(!context.computeOnClient(m -> CreationSpawn.busy()), "Cancelled screen armed creation");

        Path source = null;
        for (boolean bonus : new boolean[]{true, false}) {
            var world = context.worldBuilder().adjustSettings(state -> {
                state.setSeed("8611");
                state.setWorldType(state.getNormalPresetList().stream().filter(entry -> entry.preset() != null
                        && entry.preset().is(WorldPresets.NORMAL)).findFirst().orElseThrow());
                state.setGenerateStructures(true);
                state.setBonusChest(bonus);
                state.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.SURVIVAL);
                state.setDifficulty(net.minecraft.world.Difficulty.HARD);
                CreationSpawn.select(state, true);
            }).create();
            try {
                context.waitFor(m -> !CreationSpawn.busy() && m.gui.screen() == null, 6000);
                source = context.computeOnClient(m -> m.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize());
                world.getServer().runOnServer(server -> {
                    var level = server.overworld();
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var feet = player.blockPosition();
                    var village = level.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, feet, 8, false);
                    check(village != null && village.distSqr(feet) <= 10000, "Creation did not place player near village");
                    check(level.getRespawnData().globalPos().pos().equals(feet), "Creation world spawn differs from player");
                    check(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                            && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP), "Unsafe creation spawn");
                    check(server.getWorldGenSettings().options().generateBonusChest() == bonus, "Bonus option changed");
                    check(level.getSeed() == 8611 && server.getWorldData().getDifficulty() == net.minecraft.world.Difficulty.HARD
                            && player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL, "Creation parameters changed");
                    assertBonusChest(level, feet, bonus);
                });
                if (!bonus) {
                    installNoVillages(source);
                    world.getServer().runOnServer(server -> {
                        var data = server.getWorldData();
                        var config = data.getDataConfiguration();
                        var enabled = new java.util.ArrayList<>(config.dataPacks().getEnabled());
                        enabled.add("file/no-villages");
                        data.setDataConfiguration(new net.minecraft.world.level.WorldDataConfiguration(
                                new net.minecraft.world.level.DataPackConfig(enabled, config.dataPacks().getDisabled()), config.enabledFeatures()));
                    });
                }
            } finally { world.close(); }
        }
        Path keptSource = source;
        context.runOnClient(m -> {
            try (var access = m.getLevelSource().validateAndCreateAccess(keptSource.getFileName().toString())) {
                var recreated = m.createWorldOpenFlows().recreateWorldData(access);
                var copiedPacks = CreateWorldScreen.createTempDataPackDirFromExistingWorld(
                        access.getLevelPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR), m);
                var screen = CreateWorldScreen.createFromExisting(m, () -> m.gui.setScreen(new TitleScreen()),
                        recreated.getFirst(), recreated.getSecond(), copiedPacks);
                CreationSpawn.select(screen.getUiState(), true);
                m.gui.setScreen(screen);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        context.clickScreenButton("Create New World");
        context.waitForScreen(net.minecraft.client.gui.screens.AlertScreen.class);
        check(Files.exists(keptSource.resolve("level.dat")), "Failed creation preference deleted an existing world");
        check(context.computeOnClient(m -> m.getSingleplayerServer() != null && !CreationSpawn.busy()), "Failed village placement did not retain new world");
        context.clickScreenButton("Back");
        var closingServer = context.computeOnClient(m -> m.getSingleplayerServer());
        context.runOnClient(m -> {
            m.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            m.gui.setScreen(new TitleScreen());
        });
        context.waitFor(m -> closingServer.isShutdown() && m.level == null && m.getSingleplayerServer() == null, 400);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
        org.slf4j.LoggerFactory.getLogger("worldreroll").info("WORLDREROLL_CREATION_PASS: actual World-tab option, structures gating/cancel, village spawn and bonus chest on/off");
    }

    private static void changeLanguage(ClientGameTestContext context, String language) {
        var reload = context.computeOnClient(m -> {
            m.getLanguageManager().setSelected(language);
            return m.reloadResourcePacks();
        });
        context.waitFor(m -> reload.isDone(), 400);
        reload.join();
        context.waitFor(m -> m.gui.overlay() == null, 400);
    }

    private static void installNoVillages(Path source) {
        try {
            Path root = source.resolve("datapacks/no-villages");
            Files.createDirectories(root);
            var format = net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.SERVER_DATA);
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"No villages fixture\",\"min_format\":"
                    + format.major() + ",\"max_format\":" + format.major() + "}}");
            Path tag = root.resolve("data/minecraft/tags/worldgen/structure/village.json");
            Files.createDirectories(tag.getParent());
            Files.writeString(tag, "{\"replace\":true,\"values\":[]}");
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    static void assertBonusChest(ServerLevel level, BlockPos feet, boolean enabled) {
        var chests = new java.util.ArrayList<ChestBlockEntity>();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-8, -2, -8), feet.offset(8, 2, 8))) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest
                    && BuiltInLootTables.SPAWN_BONUS_CHEST.equals(chest.getLootTable())) chests.add(chest);
        }
        check(chests.size() == (enabled ? 1 : 0), "Incorrect number of bonus chests near final spawn: " + chests.size());
        if (enabled) {
            var chest = chests.getFirst();
            check(level.getBlockState(chest.getBlockPos().above()).isAir(), "Bonus chest lid obstructed");
            chest.unpackLootTable(null);
            boolean filled = false;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) filled |= !chest.getItem(slot).isEmpty();
            check(filled, "Bonus chest has no actual vanilla loot");
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
