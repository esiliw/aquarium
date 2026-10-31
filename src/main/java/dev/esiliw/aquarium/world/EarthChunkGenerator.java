/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. Generation is rewritten for the 1.20.1 world-gen pipeline.
 */
package dev.esiliw.aquarium.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.esiliw.aquarium.data.EarthData;
import dev.esiliw.aquarium.data.GeoProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class EarthChunkGenerator extends ChunkGenerator {
    public static final Codec<EarthChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME),
            EarthSettings.CODEC.fieldOf("settings").forGetter(g -> g.settings)
    ).apply(i, i.stable(EarthChunkGenerator::new)));

    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState MUD = Blocks.MUD.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    public final EarthSettings settings;
    private final GeoProjection projection;
    private final EarthData data;
    private final EarthBiomeSource earthBiomes;

    public EarthChunkGenerator(HolderGetter<Biome> biomes, EarthSettings settings) {
        this(new EarthBiomeSource(biomes, settings), settings);
    }

    private EarthChunkGenerator(EarthBiomeSource biomeSource, EarthSettings settings) {
        super(biomeSource);
        this.earthBiomes = biomeSource;
        this.settings = settings;
        this.projection = settings.projection();
        this.data = settings.createData();
    }

    public GeoProjection projection() {
        return this.projection;
    }

    public EarthData data() {
        return this.data;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ heights

    /** elevation in metres at a block column */
    public double elevationAt(int x, int z) {
        return this.data.elevation(this.projection.lat(z + 0.5), this.projection.lon(x + 0.5));
    }

    /** y of the top solid block for a given elevation */
    public int surfaceY(double elevation) {
        double y = elevation / this.settings.verticalScale();
        int sea = this.settings.seaLevel();
        int top;
        if (elevation < 0) {
            top = sea - 1 + (int) Math.floor(y);           // sea floor is always at least 1 block under water
            top = Math.min(top, sea - 2);
        } else {
            top = sea + (int) Math.floor(y) + this.settings.heightOffset();
        }
        return Math.max(this.settings.minY() + 1, Math.min(this.settings.maxY() - 1, top));
    }

    public int surfaceYAt(int x, int z) {
        return this.column(x, z).top;
    }

    /**
     * Everything about one block column.
     * top = y of the highest solid block, waterTop = y of the highest water block (or MIN_VALUE),
     * shore = y that must stay solid next to a lake so it doesn't spill (MIN_VALUE if none).
     */
    public record Column(int top, int waterTop, double elevation, Cover cover, boolean ocean, boolean lake, double lat) {
    }

    public Column column(int x, int z) {
        double lat = this.projection.lat(z + 0.5), lon = this.projection.lon(x + 0.5);
        double e = this.data.elevation(lat, lon);
        int sea = this.settings.seaLevel();

        Boolean outline = this.data.oceanAt(lat, lon);
        boolean ocean = outline != null ? outline : e < 0;
        if (ocean) {
            e = Math.min(e, -2.0);                          // coastline from the outlines wins over coarse elevation
            int top = this.surfaceY(e);
            return new Column(top, sea - 1, e, Cover.WATER, true, false, lat);
        }
        e = Math.max(e, 0.5);                               // land is never below the sea surface
        Cover cover = this.data.cover(lat, lon);
        if (cover == Cover.WATER) cover = Cover.NO;         // water comes from the lake model below
        int top = this.surfaceY(e);

        EarthData.Water w = this.data.water(lat, lon);
        if (w.fraction() > 0) {
            int lakeY = this.surfaceY(Math.max(w.level(), 0.5));
            boolean isLake = w.fraction() >= 0.5 || (w.fraction() > 0.15 && e < w.level() + 0.5);
            if (isLake) {
                double shoreMeters = w.shoreDistance() * this.data.coverPixelMeters() * Math.cos(Math.toRadians(lat));
                double depthMeters = Math.min(3.0 + shoreMeters * 0.02, 60.0);
                int depth = Math.max(1, (int) Math.round(depthMeters / this.settings.verticalScale()));
                int bed = Math.min(top, lakeY - depth);
                return new Column(bed, lakeY, e, Cover.WATER, false, true, lat);
            }
            top = Math.max(top, lakeY);                     // shore next to a lake stays at least at water level
        }
        return new Column(top, Integer.MIN_VALUE, e, cover, false, false, lat);
    }

    // ------------------------------------------------------------------ generation

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            this.fill(chunk);
            return chunk;
        }, executor);
    }

    private void fill(ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        // columns for an 18x18 window so slopes can be measured at the chunk edges
        Column[] cols = new Column[18 * 18];
        for (int dz = -1; dz <= 16; dz++) {
            for (int dx = -1; dx <= 16; dx++) {
                cols[(dx + 1) + (dz + 1) * 18] = this.column(x0 + dx, z0 + dz);
            }
        }
        int sea = this.settings.seaLevel();
        int minY = Math.max(chunk.getMinBuildHeight(), this.settings.minY());
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, this.settings.maxY());

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = (lx + 1) + (lz + 1) * 18;
                Column c = cols[i];
                int top = c.top();
                int slope = Math.max(Math.abs(cols[i - 1].top() - cols[i + 1].top()), Math.abs(cols[i - 18].top() - cols[i + 18].top()));
                Cover cover = c.cover();
                boolean cold = Math.abs(c.lat()) + Math.max(0, c.elevation()) / 150.0 > 66;
                boolean underwater = c.waterTop() != Integer.MIN_VALUE && top < c.waterTop();

                BlockState surface, under;
                if (underwater) {
                    int depth = c.waterTop() - top;
                    if (c.ocean()) surface = depth > 30 ? GRAVEL : SAND;
                    else surface = depth <= 2 ? SAND : (depth <= 6 ? GRAVEL : MUD);
                    under = surface == SAND ? SANDSTONE : STONE;
                } else if (slope >= 4) {
                    surface = STONE;
                    under = STONE;
                } else if (cover == Cover.PERMANENT_SNOW) {
                    surface = SNOW;
                    under = STONE;
                } else if (cover == Cover.BARE_CONSOLIDATED && !cold) {
                    surface = TERRACOTTA;
                    under = RED_SAND;
                } else if ((cover.isBare() || cover == Cover.SPARSE_VEGETATION) && !cold) {
                    surface = SAND;
                    under = SANDSTONE;
                } else if (c.elevation() < 3 && top <= sea + 1 && cover != Cover.URBAN) {
                    surface = SAND;                               // beaches right at sea level
                    under = SAND;
                } else if (cover == Cover.FLOODED_VEGETATION || cover == Cover.SALINE_FLOODED_FOREST || cover == Cover.FRESH_FLOODED_FOREST) {
                    surface = GRASS;
                    under = MUD;
                } else {
                    surface = GRASS;
                    under = DIRT;
                }

                for (int y = minY; y <= Math.min(top, maxY); y++) {
                    BlockState state;
                    if (y == minY) state = BEDROCK;
                    else if (y == top) state = surface;
                    else if (y >= top - 3) state = under;
                    else state = y < 0 ? DEEPSLATE : STONE;
                    setBlock(chunk, lx, y, lz, state);
                }
                if (underwater) {
                    for (int y = Math.max(top + 1, minY); y <= Math.min(c.waterTop(), maxY); y++) setBlock(chunk, lx, y, lz, WATER);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private static void setBlock(ChunkAccess chunk, int lx, int y, int lz, BlockState state) {
        int idx = chunk.getSectionIndex(y);
        if (idx < 0 || idx >= chunk.getSectionsCount()) return;
        LevelChunkSection section = chunk.getSection(idx);
        section.setBlockState(lx, y & 15, lz, state, false);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        // surface is placed in fillFromNoise
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // no carvers yet (caves would cut through the real terrain)
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos chunkPos = region.getCenter();
        Holder<Biome> biome = region.getBiome(chunkPos.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
        WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        rnd.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, chunkPos, rnd);
    }

    @Override
    public int getGenDepth() {
        return this.settings.height();
    }

    @Override
    public int getSeaLevel() {
        return this.settings.seaLevel();
    }

    @Override
    public int getMinY() {
        return this.settings.minY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Column c = this.column(x, z);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) return c.top() + 1;
        return Math.max(c.top(), c.waterTop()) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        int height = level.getHeight();
        BlockState[] column = new BlockState[height];
        Column c = this.column(x, z);
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            if (y <= c.top()) column[i] = y == minY ? BEDROCK : STONE;
            else if (y <= c.waterTop()) column[i] = WATER;
            else column[i] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        double lat = this.projection.lat(pos.getZ() + 0.5), lon = this.projection.lon(pos.getX() + 0.5);
        info.add(String.format(Locale.ROOT, "Aquarium: %.5f, %.5f  scale 1:%s", lat, lon, trim(this.settings.worldScale())));
        Column c = this.column(pos.getX(), pos.getZ());
        info.add("Cover: " + c.cover().name().toLowerCase(Locale.ROOT)
                + String.format(Locale.ROOT, "  elevation %.0f m", c.elevation()) + (c.ocean() ? "  ocean" : c.lake() ? "  lake" : ""));
    }

    public static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
