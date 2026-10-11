package dev.richasy.worldreroll;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

/** Runs exclusively on the replacement integrated server's thread. */
public final class VillageSpawn {
    private VillageSpawn() {}

    public static BlockPos place(ServerLevel level, ServerPlayer player) {
        if (!level.getServer().getWorldGenSettings().options().generateStructures()) {
            throw new IllegalStateException("Village preference requires structure generation");
        }
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 100, false);
        if (village == null) throw new IllegalStateException("No village found within the bounded search");
        BlockPos spawn = safeGroundNear(level, village);
        if (spawn == null) throw new IllegalStateException("No safe ground found near village at " + village);
        if (level.getServer().getWorldGenSettings().options().generateBonusChest()) {
            moveBonusChest(level, level.getRespawnData().globalPos().pos(), spawn);
        }
        if (!player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                Set.of(), player.getYRot(), 0, true)) {
            throw new IllegalStateException("Could not move player to village spawn");
        }
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), spawn, 0, 0));
        return spawn;
    }

    private static void moveBonusChest(ServerLevel level, BlockPos previousSpawn, BlockPos spawn) {
        var loot = net.minecraft.world.level.storage.loot.BuiltInLootTables.SPAWN_BONUS_CHEST;
        // Vanilla places its bonus chest in the original spawn chunk. Identify it
        // by its unopened loot table, never by merely being a nearby chest.
        var source = level.getChunkAt(previousSpawn).getBlockEntities().values().stream()
                .filter(entity -> entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                        && loot.equals(chest.getLootTable()))
                .map(entity -> (net.minecraft.world.level.block.entity.ChestBlockEntity) entity)
                .findFirst().orElse(null);
        BlockPos destination = null;
        for (int radius = 2; radius <= 8 && destination == null; radius++) {
            for (int x = -radius; x <= radius && destination == null; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    int px = spawn.getX() + x, pz = spawn.getZ() + z;
                    var chunk = level.getChunk(Math.floorDiv(px, 16), Math.floorDiv(pz, 16));
                    BlockPos pos = new BlockPos(px, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            px & 15, pz & 15) + 1, pz);
                    if (Math.abs(pos.getY() - spawn.getY()) <= 2 && level.getWorldBorder().isWithinBounds(pos)
                            && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        destination = pos;
                        break;
                    }
                }
            }
        }
        if (destination == null) throw new IllegalStateException("No safe bonus chest position near " + spawn);
        if (!level.setBlock(destination, Blocks.CHEST.defaultBlockState(), 3)
                || !(level.getBlockEntity(destination) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) {
            throw new IllegalStateException("Could not place bonus chest at " + destination);
        }
        chest.setLootTable(loot);
        chest.setLootTableSeed(source == null ? level.getRandom().nextLong() : source.getLootTableSeed());
        chest.setChanged();
        if (source != null) {
            level.removeBlock(source.getBlockPos(), false);
        } else {
            org.slf4j.LoggerFactory.getLogger("worldreroll").warn(
                    "Vanilla bonus chest absent from original spawn chunk {}; generated one at final spawn {}",
                    previousSpawn, destination);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos torch = destination.relative(direction);
            var state = Blocks.TORCH.defaultBlockState();
            if (level.getBlockState(torch).isAir() && state.canSurvive(level, torch)) level.setBlock(torch, state, 3);
        }
    }

    private static BlockPos safeGroundNear(ServerLevel level, BlockPos village) {
        for (int radius = 8; radius <= 64; radius += 4) {
            for (int x = -radius; x <= radius; x += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    int px = village.getX() + x, pz = village.getZ() + z;
                    // Level.getHeight returns the minimum world Y for an unloaded chunk.
                    var chunk = level.getChunk(Math.floorDiv(px, 16), Math.floorDiv(pz, 16));
                    BlockPos feet = new BlockPos(px, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            px & 15, pz & 15) + 1, pz);
                    var ground = level.getBlockState(feet.below());
                    boolean terrain = ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND)
                            || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.GRAVEL)
                            || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.DIRT_PATH);
                    if (terrain && level.getWorldBorder().isWithinBounds(feet)
                            && ground.isFaceSturdy(level, feet.below(), Direction.UP)
                            && ground.getFluidState().isEmpty() && level.getBlockState(feet).isAir()
                            && level.getBlockState(feet.above()).isAir()) return feet;
                }
            }
        }
        return null;
    }
}
