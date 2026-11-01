package dev.esiliw.aquarium.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import dev.esiliw.aquarium.data.Climate;

import java.util.List;

/**
 * Chooses a vanilla biome from land cover, elevation and latitude.
 * (Terrarium 1.12 used climate data + its own biome set; this keeps to vanilla biomes so vanilla trees,
 * grass and mobs generate. Climate data can refine this later.)
 */
public final class EarthBiomes {
    /** our own biome: a normal forest with spruce mixed in (data/aquarium/worldgen/biome/mixed_forest.json) */
    public static final ResourceKey<Biome> MIXED_FOREST = key("mixed_forest"), MEDITERRANEAN_WOODLAND = key("mediterranean_woodland"),
            CHAPARRAL = key("chaparral"), STEPPE = key("steppe"), COLD_DESERT = key("cold_desert"), TUNDRA = key("tundra"),
            SUBTROPICAL_FOREST = key("subtropical_forest"), OUTBACK = key("outback"),
            TROPICAL_COAST = key("tropical_coast"), EUCALYPTUS_FOREST = key("eucalyptus_forest"), EUCALYPTUS_WOODLAND = key("eucalyptus_woodland");

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, new net.minecraft.resources.ResourceLocation("aquarium", name));
    }

    public static final List<ResourceKey<Biome>> ALL = List.of(
            Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN, Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.BEACH,
            Biomes.SNOWY_BEACH, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SNOWY_PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST,
            Biomes.FLOWER_FOREST, Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,
            Biomes.WINDSWEPT_SAVANNA, Biomes.DESERT, Biomes.BADLANDS, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.MEADOW,
            Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.ICE_SPIKES,
            Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.STONY_SHORE,
            MIXED_FOREST, MEDITERRANEAN_WOODLAND, CHAPARRAL, STEPPE, COLD_DESERT, TUNDRA, SUBTROPICAL_FOREST, OUTBACK, TROPICAL_COAST, EUCALYPTUS_FOREST, EUCALYPTUS_WOODLAND);

    private EarthBiomes() {
    }

    public static ResourceKey<Biome> select(Cover cover, double elevation, double lat, boolean ocean) {
        return select(cover, elevation, lat, ocean, Climate.NONE);
    }

    /** biome from land cover + Köppen climate (+ altitude for mountains); climate NONE falls back to latitude rules */
    /** volcanic islands and fields where bare ground is black lava and beaches are black sand */
    private static final double[][] VOLCANIC = {
            {63.2, 66.6, -24.6, -13.4},     // Iceland
            {18.8, 22.3, -160.3, -154.7},   // Hawaii
            {27.6, 29.5, -18.2, -13.3},     // Canary Islands
            {36.9, 39.8, -31.3, -25.0},     // Azores
            {37.6, 37.9, 14.8, 15.2},       // Etna
            {40.7, 40.9, 14.3, 14.5},       // Vesuvius
            {-1.5, 0.7, -92.0, -89.0},      // Galapagos
            {-21.4, -20.8, 55.2, 55.9},     // Reunion
            {-39.4, -38.9, 175.4, 175.8},   // Tongariro, New Zealand
            {31.5, 31.7, 130.5, 130.8},     // Sakurajima
            {38.4, 38.9, 15.1, 15.3},       // Aeolian Islands (Stromboli)
    };

    public static boolean volcanic(double lat, double lon) {
        for (double[] b : VOLCANIC) if (lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]) return true;
        return false;
    }

    /** salt flats and salt pans: bare flat ground here is white salt crust */
    private static final double[][] SALT = {
            {-20.75, -19.85, -68.35, -66.85},   // Salar de Uyuni
            {-19.6, -19.1, -68.45, -67.9},      // Salar de Coipasa
            {-23.9, -23.0, -68.45, -68.0},      // Salar de Atacama
            {-30.2, -29.4, -65.3, -64.4},       // Salinas Grandes
            {40.6, 41.1, -114.0, -113.3},       // Bonneville Salt Flats
            {36.0, 36.4, -116.95, -116.75},     // Badwater Basin, Death Valley
            {-19.05, -18.5, 15.85, 16.75},      // Etosha Pan
            {-21.1, -20.2, 24.9, 26.4},         // Makgadikgadi Pans
            {33.4, 34.2, 7.5, 9.1},             // Chott el Djerid
            {-29.6, -27.7, 136.7, 138.0},       // Kati Thanda / Lake Eyre
            {-31.6, -29.9, 137.4, 138.2},       // Lake Torrens
            {34.3, 35.3, 52.5, 55.0},           // Dasht-e Kavir
            {23.4, 24.5, 68.7, 71.2},           // Rann of Kutch
            {36.6, 37.2, 94.5, 96.0},           // Qarhan, Qaidam Basin
    };

    public static boolean saltFlat(double lat, double lon) {
        for (double[] b : SALT) if (lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]) return true;
        return false;
    }

    /** monsoon Asia, where farmland in warm wet climates is flooded rice paddies */
    public static boolean riceRegion(double lat, double lon, byte climate) {
        boolean warmWet = Climate.tropical(climate) || climate == Climate.Cfa || climate == Climate.Cwa || climate == Climate.Cwb;
        if (!warmWet) return false;
        return (lat > 18 && lat < 42 && lon > 98 && lon < 146)         // China, Korea, Japan
                || (lat > 5 && lat < 31 && lon > 67 && lon < 98)        // South Asia
                || (lat > -11 && lat <= 24 && lon > 92 && lon < 141);   // Southeast Asia
    }

    /** Australia and Tasmania */
    public static boolean australia(double lat, double lon) {
        return lat < -10 && lat > -44 && lon > 112 && lon < 154;
    }

    /** mainland Australia's interior, where deserts are red */
    public static boolean outback(double lat, double lon) {
        return lat < -17 && lat > -35 && lon > 114 && lon < 151;
    }

    public static ResourceKey<Biome> select(Cover cover, double elevation, double lat, boolean ocean, byte climate) {
        return select(cover, elevation, lat, Double.NaN, ocean, climate);
    }

    public static ResourceKey<Biome> select(Cover cover, double elevation, double lat, double lon, boolean ocean, byte climate) {
        double alat = Math.abs(lat);
        if (ocean) {
            boolean deep = elevation < -200;
            if (alat > 66) return deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
            if (alat > 45) return deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
            if (alat < 20) return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.WARM_OCEAN;
            if (alat < 32) return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
            return deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
        }
        if (climate == Climate.NONE) return byLatitude(cover, elevation, lat);

        // effective "coldness": latitude plus about 1 degree per 150 m of altitude (for peaks above the climate map's detail)
        double cold = alat + Math.max(0, elevation) / 150.0;
        if (elevation > 2200 || cold > 72) {
            if (cover == Cover.PERMANENT_SNOW || cold > 80) return elevation > 3500 ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS;
            if (cover.isBare() || cover == Cover.SPARSE_VEGETATION || cover == Cover.LICHENS_AND_MOSSES)
                return cold > 60 ? Biomes.SNOWY_SLOPES : Biomes.STONY_PEAKS;
        }
        if (cover == Cover.PERMANENT_SNOW || climate == Climate.EF) return elevation > 1500 ? Biomes.FROZEN_PEAKS : Biomes.SNOWY_PLAINS;
        if (cover == Cover.WATER) return Biomes.RIVER;
        if (cover == Cover.SALINE_FLOODED_FOREST) return Biomes.MANGROVE_SWAMP;
        if (cover == Cover.FRESH_FLOODED_FOREST || cover == Cover.FLOODED_VEGETATION)
            return Climate.tropical(climate) ? Biomes.MANGROVE_SWAMP : Climate.arid(climate) || climate == Climate.ET ? TUNDRA_OR(climate, Biomes.SWAMP) : Biomes.SWAMP;
        if (climate == Climate.ET) return cover.isForest() ? Biomes.TAIGA : TUNDRA;

        boolean trees = cover.isForest() || cover == Cover.TREE_OR_SHRUB_COVER || cover == Cover.TREE_AND_SHRUB_WITH_HERBACEOUS_COVER
                || cover == Cover.SPARSE_TREE;
        boolean needles = cover == Cover.NEEDLEAF_EVERGREEN || cover == Cover.NEEDLEAF_EVERGREEN_CLOSED || cover == Cover.NEEDLEAF_EVERGREEN_OPEN
                || cover == Cover.NEEDLEAF_DECIDUOUS || cover == Cover.NEEDLEAF_DECIDUOUS_CLOSED || cover == Cover.NEEDLEAF_DECIDUOUS_OPEN;
        boolean shrubs = cover == Cover.SHRUBLAND || cover == Cover.SHRUBLAND_EVERGREEN || cover == Cover.SHRUBLAND_DECIDUOUS
                || cover == Cover.SPARSE_SHRUB || cover == Cover.HERBACEOUS_COVER_WITH_TREE_AND_SHRUB;
        boolean grass = cover == Cover.GRASSLAND || cover == Cover.HERBACEOUS_COVER || cover == Cover.SPARSE_HERBACEOUS_COVER
                || cover == Cover.SPARSE_VEGETATION || cover == Cover.NO;
        boolean farm = cover == Cover.RAINFED_CROPLAND || cover == Cover.IRRIGATED_CROPLAND || cover == Cover.CROPLAND_WITH_VEGETATION
                || cover == Cover.VEGETATION_WITH_CROPLAND || cover == Cover.URBAN;
        boolean bare = cover.isBare();
        if (cover == Cover.LICHENS_AND_MOSSES) return TUNDRA;

        // Australia's forests and woodlands are eucalypts (the tropical rainforest stays jungle, the red centre treeless)
        if (trees && australia(lat, lon) && climate != Climate.Af && climate != Climate.Am && climate != Climate.BWh)
            return Climate.temperate(climate) && !Climate.mediterranean(climate) ? EUCALYPTUS_FOREST : EUCALYPTUS_WOODLAND;
        // palm-lined tropical coasts: open ground just above the sea
        boolean palmClimate = Climate.tropical(climate) || Climate.humidSubtropical(climate) && alat < 30;
        if (palmClimate && elevation < 5 && !trees && !farm && cover != Cover.URBAN) return TROPICAL_COAST;

        if (Climate.tropical(climate)) {
            if (trees) return climate == Climate.Aw ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE;
            if (bare) return Biomes.DESERT;
            if (farm && climate != Climate.Aw) return Biomes.SPARSE_JUNGLE;
            return Biomes.SAVANNA;
        }
        if (Climate.arid(climate)) {
            boolean hot = climate == Climate.BWh || climate == Climate.BSh;
            // the red Outback: badlands colours (no mesa cliffs here, terrain comes from the height data)
            if (outback(lat, lon) && climate == Climate.BWh) return trees ? OUTBACK : Biomes.BADLANDS;   // scrub and desert oaks: grass, no trees
            if (trees) return hot ? Biomes.SAVANNA : MEDITERRANEAN_WOODLAND;
            if (bare) return climate == Climate.BWh ? (cover == Cover.BARE_CONSOLIDATED ? Biomes.BADLANDS : Biomes.DESERT)
                    : climate == Climate.BWk ? COLD_DESERT : hot ? Biomes.DESERT : COLD_DESERT;
            if (climate == Climate.BWh) return farm ? Biomes.PLAINS : Biomes.DESERT;      // irrigated fields in the desert stay green
            if (climate == Climate.BWk) return farm ? Biomes.PLAINS : COLD_DESERT;
            return hot ? Biomes.SAVANNA : STEPPE;
        }
        if (Climate.mediterranean(climate)) {
            if (trees) return climate == Climate.Csc ? MIXED_FOREST : (needles ? Biomes.TAIGA : MEDITERRANEAN_WOODLAND);
            if (shrubs) return CHAPARRAL;
            if (bare) return elevation > 1500 ? Biomes.STONY_PEAKS : CHAPARRAL;
            if (farm) return climate == Climate.Csa ? STEPPE : Biomes.PLAINS;
            return STEPPE;
        }
        if (Climate.temperate(climate)) {
            if (trees) {
                if (Climate.humidSubtropical(climate)) return SUBTROPICAL_FOREST;
                if (climate == Climate.Cfc || climate == Climate.Cwc) return Biomes.TAIGA;
                if (needles) return Biomes.OLD_GROWTH_SPRUCE_TAIGA;
                return cold > 47 ? MIXED_FOREST : Biomes.FOREST;
            }
            if (bare) return elevation > 1500 ? Biomes.STONY_PEAKS : Biomes.WINDSWEPT_HILLS;
            if (shrubs) return elevation > 1000 ? Biomes.MEADOW : Biomes.PLAINS;
            return elevation > 1200 && !farm ? Biomes.MEADOW : Biomes.PLAINS;
        }
        // continental (D) climates
        if (trees) {
            if (Climate.veryColdWinter(climate)) return Biomes.SNOWY_TAIGA;
            if (Climate.boreal(climate)) return needles || cold > 55 ? Biomes.TAIGA : MIXED_FOREST;
            if (climate == Climate.Dfa || climate == Climate.Dwa || climate == Climate.Dsa) return needles ? MIXED_FOREST : Biomes.FOREST;
            return needles ? Biomes.TAIGA : MIXED_FOREST;
        }
        if (bare) return elevation > 1500 ? Biomes.STONY_PEAKS : Biomes.WINDSWEPT_HILLS;
        if (Climate.boreal(climate)) return shrubs || grass ? TUNDRA : Biomes.PLAINS;
        if (climate == Climate.Dsa || climate == Climate.Dsb) return STEPPE;
        return Biomes.PLAINS;
    }

    private static ResourceKey<Biome> TUNDRA_OR(byte climate, ResourceKey<Biome> other) {
        return climate == Climate.ET ? TUNDRA : other;
    }

    /** the pre-climate rules, for when no climate is known (open sea edges, missing data) */
    private static ResourceKey<Biome> byLatitude(Cover cover, double elevation, double lat) {
        double alat = Math.abs(lat);
        // effective "coldness": latitude plus about 1 degree per 150 m of altitude
        double cold = alat + Math.max(0, elevation) / 150.0;
        boolean tropical = alat < 23.5 && cold < 30;
        boolean arid = false; // refined later with climate data

        if (elevation > 2200 || cold > 72) {
            if (cover == Cover.PERMANENT_SNOW || cold > 80) return elevation > 3500 ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS;
            if (cover.isBare() || cover == Cover.SPARSE_VEGETATION) return cold > 60 ? Biomes.SNOWY_SLOPES : Biomes.STONY_PEAKS;
        }

        return switch (cover) {
            case WATER -> cold > 60 ? Biomes.FROZEN_RIVER : Biomes.RIVER;
            case PERMANENT_SNOW -> elevation > 1500 ? Biomes.FROZEN_PEAKS : Biomes.ICE_SPIKES;
            case BROADLEAF_EVERGREEN -> tropical ? Biomes.JUNGLE : Biomes.FOREST;
            case BROADLEAF_DECIDUOUS, BROADLEAF_DECIDUOUS_CLOSED -> cold > 50 ? Biomes.BIRCH_FOREST : Biomes.FOREST;
            case BROADLEAF_DECIDUOUS_OPEN -> tropical ? Biomes.SAVANNA : Biomes.FOREST;
            case NEEDLEAF_EVERGREEN, NEEDLEAF_EVERGREEN_OPEN -> cold > 58 ? Biomes.SNOWY_TAIGA : Biomes.TAIGA;
            case NEEDLEAF_EVERGREEN_CLOSED -> cold > 58 ? Biomes.SNOWY_TAIGA : Biomes.OLD_GROWTH_SPRUCE_TAIGA;
            case NEEDLEAF_DECIDUOUS, NEEDLEAF_DECIDUOUS_CLOSED, NEEDLEAF_DECIDUOUS_OPEN -> cold > 58 ? Biomes.SNOWY_TAIGA : Biomes.OLD_GROWTH_PINE_TAIGA;
            case MIXED_LEAF_TYPE -> cold > 58 ? Biomes.TAIGA : cold > 47 ? MIXED_FOREST : Biomes.FOREST;   // no dark forest anywhere
            case TREE_AND_SHRUB_WITH_HERBACEOUS_COVER, TREE_OR_SHRUB_COVER -> tropical ? Biomes.SPARSE_JUNGLE : (cold > 55 ? Biomes.TAIGA : Biomes.FOREST);
            case HERBACEOUS_COVER_WITH_TREE_AND_SHRUB -> tropical ? Biomes.SAVANNA : Biomes.MEADOW;
            case SHRUBLAND, SHRUBLAND_EVERGREEN, SHRUBLAND_DECIDUOUS -> alat < 38 ? Biomes.SAVANNA : (cold > 60 ? Biomes.SNOWY_PLAINS : Biomes.WINDSWEPT_SAVANNA);
            case GRASSLAND, HERBACEOUS_COVER -> cold > 62 ? Biomes.SNOWY_PLAINS : (alat < 30 ? Biomes.SAVANNA : Biomes.PLAINS);
            case LICHENS_AND_MOSSES -> Biomes.SNOWY_PLAINS;
            case SPARSE_VEGETATION, SPARSE_HERBACEOUS_COVER, SPARSE_SHRUB, SPARSE_TREE ->
                    cold > 60 ? Biomes.SNOWY_PLAINS : (alat < 40 ? Biomes.DESERT : Biomes.WINDSWEPT_HILLS);
            case FRESH_FLOODED_FOREST, FLOODED_VEGETATION -> Biomes.SWAMP;
            case SALINE_FLOODED_FOREST -> Biomes.MANGROVE_SWAMP;
            case URBAN, RAINFED_CROPLAND, IRRIGATED_CROPLAND, CROPLAND_WITH_VEGETATION, VEGETATION_WITH_CROPLAND ->
                    cold > 62 ? Biomes.SNOWY_PLAINS : (alat < 25 ? Biomes.SAVANNA : Biomes.PLAINS);
            case BARE, BARE_UNCONSOLIDATED -> cold > 60 ? Biomes.SNOWY_PLAINS : Biomes.DESERT;
            case BARE_CONSOLIDATED -> cold > 60 ? Biomes.SNOWY_SLOPES : Biomes.BADLANDS;
            default -> elevation < 0 ? Biomes.OCEAN : (alat < 25 ? Biomes.SAVANNA : Biomes.PLAINS);
        };
    }
}
