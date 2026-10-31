package dev.esiliw.aquarium.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;

/**
 * Chooses a vanilla biome from land cover, elevation and latitude.
 * (Terrarium 1.12 used climate data + its own biome set; this keeps to vanilla biomes so vanilla trees,
 * grass and mobs generate. Climate data can refine this later.)
 */
public final class EarthBiomes {
    public static final List<ResourceKey<Biome>> ALL = List.of(
            Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN, Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.BEACH,
            Biomes.SNOWY_BEACH, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SNOWY_PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST,
            Biomes.DARK_FOREST, Biomes.FLOWER_FOREST, Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,
            Biomes.WINDSWEPT_SAVANNA, Biomes.DESERT, Biomes.BADLANDS, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.MEADOW,
            Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.ICE_SPIKES,
            Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.STONY_SHORE);

    private EarthBiomes() {
    }

    public static ResourceKey<Biome> select(Cover cover, double elevation, double lat) {
        double alat = Math.abs(lat);
        if (elevation < 0 && (cover == Cover.WATER || cover == Cover.NO)) {
            boolean deep = elevation < -200;
            if (alat > 66) return deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
            if (alat > 45) return deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
            if (alat < 20) return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.WARM_OCEAN;
            if (alat < 32) return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
            return deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
        }

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
            case MIXED_LEAF_TYPE -> cold > 50 ? Biomes.TAIGA : Biomes.DARK_FOREST;
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
