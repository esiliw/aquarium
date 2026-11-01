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
            SUBTROPICAL_FOREST = key("subtropical_forest");

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
            MIXED_FOREST, MEDITERRANEAN_WOODLAND, CHAPARRAL, STEPPE, COLD_DESERT, TUNDRA, SUBTROPICAL_FOREST);

    private EarthBiomes() {
    }

    public static ResourceKey<Biome> select(Cover cover, double elevation, double lat, boolean ocean) {
        return select(cover, elevation, lat, ocean, Climate.NONE);
    }

    /** biome from land cover + Köppen climate (+ altitude for mountains); climate NONE falls back to latitude rules */
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

        if (Climate.tropical(climate)) {
            if (trees) return climate == Climate.Aw ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE;
            if (bare) return Biomes.DESERT;
            if (farm && climate != Climate.Aw) return Biomes.SPARSE_JUNGLE;
            return Biomes.SAVANNA;
        }
        if (Climate.arid(climate)) {
            boolean hot = climate == Climate.BWh || climate == Climate.BSh;
            // the red Outback: badlands colours (no mesa cliffs here, terrain comes from the height data)
            if (outback(lat, lon) && climate == Climate.BWh && !trees) return Biomes.BADLANDS;
            if (trees) return hot ? Biomes.SAVANNA : MEDITERRANEAN_WOODLAND;
            if (bare) return climate == Climate.BWh ? (cover == Cover.BARE_CONSOLIDATED ? Biomes.BADLANDS : Biomes.DESERT)
                    : climate == Climate.BWk ? COLD_DESERT : hot ? Biomes.DESERT : COLD_DESERT;
            if (climate == Climate.BWh) return farm ? Biomes.SAVANNA : Biomes.DESERT;
            if (climate == Climate.BWk) return farm ? STEPPE : COLD_DESERT;
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
