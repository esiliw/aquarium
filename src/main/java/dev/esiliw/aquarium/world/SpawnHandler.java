package dev.esiliw.aquarium.world;

import dev.esiliw.aquarium.Aquarium;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Moves the world spawn to the configured real-world location the first time an Earth world loads. */
public final class SpawnHandler {
    private SpawnHandler() {
    }

    /** called when a new world picks its spawn: put it at the configured place. Returns false for non-Earth worlds. */
    public static boolean setInitialSpawn(ServerLevel level, net.minecraft.world.level.storage.ServerLevelData data) {
        if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator g)) return false;
        int x = (int) Math.floor(g.projection().blockX(g.settings.spawnLon()));
        int z = (int) Math.floor(g.projection().blockZ(g.settings.spawnLat()));
        TerrainModel.Column c = g.column(x, z);
        int y = Math.max(c.top(), c.waterTop()) + 1;
        data.setSpawn(new BlockPos(x, y, z), 0.0F);
        Flag flag = level.getDataStorage().computeIfAbsent(Flag::load, Flag::new, "aquarium_spawn");
        flag.done = true;
        flag.setDirty();
        Aquarium.LOGGER.info("Earth world starts at {}, {} -> block {} {} {}", g.settings.spawnLat(), g.settings.spawnLon(), x, y, z);
        return true;
    }

    public static void onWorldLoad(MinecraftServer server, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator g)) return;
        Flag flag = level.getDataStorage().computeIfAbsent(Flag::load, Flag::new, "aquarium_spawn");
        if (flag.done) return;
        int x = (int) Math.floor(g.projection().blockX(g.settings.spawnLon()));
        int z = (int) Math.floor(g.projection().blockZ(g.settings.spawnLat()));
        TerrainModel.Column c = g.column(x, z);
        int y = Math.max(c.top(), c.waterTop()) + 1;
        level.setDefaultSpawnPos(new BlockPos(x, y, z), 0.0F);
        flag.done = true;
        flag.setDirty();
        Aquarium.LOGGER.info("Earth spawn set to {}, {} -> block {} {} {}", g.settings.spawnLat(), g.settings.spawnLon(), x, y, z);
    }

    public static final class Flag extends SavedData {
        boolean done;

        static Flag load(CompoundTag tag) {
            Flag f = new Flag();
            f.done = tag.getBoolean("done");
            return f;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("done", this.done);
            return tag;
        }
    }
}
