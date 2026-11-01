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
    // road & rail guide
    private static final BlockState ROAD_MAJOR = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState ROAD_SECONDARY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState ROAD_MINOR = Blocks.DIRT_PATH.defaultBlockState();
    private static final BlockState RAIL_LINE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TRAM_LINE = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState STATION = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState BUILDING = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BLACK_SAND = Blocks.BLACK_CONCRETE_POWDER.defaultBlockState();

    public final EarthSettings settings;
    private final GeoProjection projection;
    private final EarthData data;
    private final EarthBiomeSource earthBiomes;
    private final TerrainModel terrain;

    public EarthChunkGenerator(HolderGetter<Biome> biomes, EarthSettings settings) {
        this(new EarthBiomeSource(biomes, settings), settings);
    }

    private EarthChunkGenerator(EarthBiomeSource biomeSource, EarthSettings settings) {
        super(biomeSource);
        this.earthBiomes = biomeSource;
        this.settings = settings;
        this.projection = settings.projection();
        this.data = settings.createData();
        this.terrain = new TerrainModel(settings, this.projection, this.data);
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

    public int surfaceY(double elevation) {
        return this.terrain.surfaceY(elevation);
    }

    public int surfaceYAt(int x, int z) {
        return this.column(x, z).top();
    }

    public TerrainModel.Column column(int x, int z) {
        return this.terrain.column(x, z);
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
        TerrainModel.Column[] cols = new TerrainModel.Column[18 * 18];
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
                TerrainModel.Column c = cols[i];
                int top = Math.max(c.top(), minY + 1);   // never a hole down to the void, whatever the data says
                if (c.waterTop() == Integer.MIN_VALUE) {
                    // banks hold the water in: never lower than a neighbouring lake or river surface
                    for (int nb : new int[]{i - 1, i + 1, i - 18, i + 18}) {
                        TerrainModel.Column n = cols[nb];
                        if (n.lake() && n.waterTop() > top) top = n.waterTop();
                    }
                }
                int slope = Math.max(Math.abs(cols[i - 1].top() - cols[i + 1].top()), Math.abs(cols[i - 18].top() - cols[i + 18].top()));
                Cover cover = c.cover();
                boolean cold = Math.abs(c.lat()) + Math.max(0, c.elevation()) / 150.0 > 66;
                boolean underwater = c.waterTop() != Integer.MIN_VALUE && top < c.waterTop();

                BlockState surface, under;
                if (!underwater && c.way() != dev.esiliw.aquarium.data.OsmWays.NONE) {
                    surface = switch (c.way()) {
                        case dev.esiliw.aquarium.data.OsmWays.MAJOR -> ROAD_MAJOR;
                        case dev.esiliw.aquarium.data.OsmWays.SECONDARY -> ROAD_SECONDARY;
                        case dev.esiliw.aquarium.data.OsmWays.MINOR -> ROAD_MINOR;
                        case dev.esiliw.aquarium.data.OsmWays.RAIL_BED -> GRAVEL;
                        case dev.esiliw.aquarium.data.OsmWays.TRAM -> TRAM_LINE;
                        case dev.esiliw.aquarium.data.OsmWays.RAIL_LINE -> RAIL_LINE;
                        default -> STATION;
                    };
                    under = surface == ROAD_MINOR ? DIRT : surface == GRAVEL ? GRAVEL : STONE;
                } else if (underwater) {
                    int depth = c.waterTop() - top;
                    if (c.ocean()) surface = depth > 30 ? GRAVEL : SAND;
                    else surface = depth <= 2 ? SAND : (depth <= 6 ? GRAVEL : MUD);
                    under = surface == SAND ? SANDSTONE : STONE;
                } else if (slope >= 4) {
                    // steep rock; in the red Outback (Uluru, Kata Tjuta) it's red, not grey
                    boolean red = (c.climate() == dev.esiliw.aquarium.data.Climate.BWh || c.climate() == dev.esiliw.aquarium.data.Climate.BSh)
                            && EarthBiomes.outback(c.lat(), this.projection.lon(x0 + lx + 0.5));
                    surface = red ? TERRACOTTA : STONE;
                    under = surface;
                } else if (c.climate() != dev.esiliw.aquarium.data.Climate.NONE && this.climatePalette(c, x0 + lx, z0 + lz) != null) {
                    surface = this.climatePalette(c, x0 + lx, z0 + lz);
                    if (surface == GRAVEL) under = GRAVEL;
                    else if (surface == SAND) under = SANDSTONE;
                    else if (surface == RED_SAND) under = RED_SANDSTONE;
                    else if (surface == BASALT || surface == BLACKSTONE || surface == BLACK_SAND) under = BLACKSTONE;
                    else if (surface == STONE) under = STONE;
                    else under = DIRT;
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
                } else if (c.buildingTop() > top) {
                    for (int y = Math.max(top + 1, minY); y <= Math.min(c.buildingTop(), maxY); y++) setBlock(chunk, lx, y, lz, BUILDING);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    /** climate-specific ground: cold-desert gravel, dry grass patches, tundra moss, rock instead of sand in wet climates */
    private BlockState climatePalette(TerrainModel.Column c, int x, int z) {
        byte k = c.climate();
        Cover cover = c.cover();
        boolean forest = cover.isForest();
        double n = dev.esiliw.aquarium.data.Noise.smooth(x / 5.0, z / 5.0, 51) * 0.7 + dev.esiliw.aquarium.data.Noise.smooth(x / 1.7, z / 1.7, 52) * 0.3;
        if (cover == Cover.PERMANENT_SNOW || cover == Cover.URBAN || cover == Cover.FLOODED_VEGETATION) return null;
        boolean beach = c.elevation() < 3 && c.top() <= this.settings.seaLevel() + 1;
        if (EarthBiomes.volcanic(c.lat(), this.projection.lon(x + 0.5))) {
            // black lava fields and black sand beaches
            if (beach && (cover.isBare() || cover == Cover.SPARSE_VEGETATION || cover == Cover.NO)) return BLACK_SAND;
            if (cover.isBare() || cover == Cover.SPARSE_VEGETATION) return n > 0.2 ? BASALT : BLACKSTONE;
        }
        if (beach) return null;                                                                   // beaches stay sand
        if ((k == dev.esiliw.aquarium.data.Climate.BWh || k == dev.esiliw.aquarium.data.Climate.BSh) && !forest
                && EarthBiomes.outback(c.lat(), this.projection.lon(x + 0.5))) {
            // red Outback ground: red sand with coarse dirt, dry grass on the steppe edges
            if (k == dev.esiliw.aquarium.data.Climate.BWh) return n > 0.35 ? COARSE_DIRT : RED_SAND;
            return n > 0.15 ? RED_SAND : n < -0.35 ? COARSE_DIRT : null;
        }
        if (k == dev.esiliw.aquarium.data.Climate.BWk && !forest) return n > 0.25 ? GRAVEL : n < -0.2 ? SAND : COARSE_DIRT;
        if (k == dev.esiliw.aquarium.data.Climate.ET && !forest && !cover.isBare()) return n > 0.2 ? MOSS : n < -0.3 ? COARSE_DIRT : GRASS;
        if (cover.isBare()) {
            if (dev.esiliw.aquarium.data.Climate.arid(k) || dev.esiliw.aquarium.data.Climate.tropical(k)) return null;   // sand, as before
            return n > 0 ? STONE : GRAVEL;
        }
        boolean dry = dev.esiliw.aquarium.data.Climate.steppe(k) || dev.esiliw.aquarium.data.Climate.mediterranean(k) || k == dev.esiliw.aquarium.data.Climate.BWh;
        boolean farm = cover == Cover.RAINFED_CROPLAND || cover == Cover.IRRIGATED_CROPLAND || cover == Cover.CROPLAND_WITH_VEGETATION;
        if (farm && (k == dev.esiliw.aquarium.data.Climate.BWh || k == dev.esiliw.aquarium.data.Climate.BWk)) return null;   // irrigated fields stay green
        if (dry && !forest) return n > (k == dev.esiliw.aquarium.data.Climate.BWh ? 0.0 : 0.45) ? COARSE_DIRT : null;
        return null;
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
        TerrainModel.Column c = this.column(x, z);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) return c.top() + 1;
        return c.highest() + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        int height = level.getHeight();
        BlockState[] column = new BlockState[height];
        TerrainModel.Column c = this.column(x, z);
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            if (y <= c.top()) column[i] = y == minY ? BEDROCK : STONE;
            else if (y <= c.waterTop()) column[i] = WATER;
            else if (y <= c.buildingTop()) column[i] = BUILDING;
            else column[i] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        double lat = this.projection.lat(pos.getZ() + 0.5), lon = this.projection.lon(pos.getX() + 0.5);
        info.add(String.format(Locale.ROOT, "Aquarium: %.5f, %.5f  scale 1:%s here", lat, lon, trim(Math.round(this.projection.metersPerBlock(lat) * 100) / 100.0)));
        TerrainModel.Column c = this.column(pos.getX(), pos.getZ());
        info.add("Cover: " + c.cover().name().toLowerCase(Locale.ROOT)
                + String.format(Locale.ROOT, "  elevation %.0f m", c.elevation()) + (c.ocean() ? "  ocean" : c.lake() ? "  lake" : ""));
    }

    public static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
