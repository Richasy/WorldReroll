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
        if (!player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                Set.of(), player.getYRot(), 0, true)) {
            throw new IllegalStateException("Could not move player to village spawn");
        }
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), spawn, 0, 0));
        return spawn;
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
